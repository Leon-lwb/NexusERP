package com.nexus.mes.flash.provider;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.mes.flash.domain.MesFlashType;
import com.nexus.mes.flash.domain.bo.FlashRecordBo;
import com.nexus.mes.flash.mapper.MesFlashTypeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URL;

/**
 * 外部REST刷写 Provider (source=EXTERNAL)
 * <p>从外部系统 REST 获取刷写内容(source_config 配 url + token + method)。
 * SSRF 防护: 禁止访问回环/内网地址(复用 HttpNodeProvider 思路)。</p>
 *
 * @author Nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalRestFlashProvider implements FlashTypeProvider {

    private final MesFlashTypeMapper flashTypeMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getTypeCode() {
        return "external_rest";
    }

    @Override
    public String getSourceType() {
        return "EXTERNAL";
    }

    @Override
    public String getDisplayName() {
        return "外部REST刷写";
    }

    @Override
    public FlashContent resolveContent(FlashContext ctx) {
        MesFlashType ft = flashTypeMapper.selectOne(Wrappers.<MesFlashType>lambdaQuery()
            .eq(MesFlashType::getTypeCode, "external_rest").last("LIMIT 1"));
        if (ft == null || StringUtils.isBlank(ft.getSourceConfig())) {
            throw new ServiceException("external_rest 未配置 source_config(需含 url)");
        }
        String url;
        String token;
        try {
            JsonNode cfg = objectMapper.readTree(ft.getSourceConfig());
            url = cfg.has("url") ? cfg.get("url").asText() : null;
            token = cfg.has("token") ? cfg.get("token").asText() : null;
        } catch (Exception e) {
            throw new ServiceException("external_rest source_config 解析失败: " + e.getMessage());
        }
        if (StringUtils.isBlank(url)) {
            throw new ServiceException("source_config 缺 url");
        }
        checkSsrf(url);
        // 调外部 REST 取内容
        HttpRequest req = HttpUtil.createGet(url).timeout(5000);
        if (StringUtils.isNotBlank(token)) {
            req.header("Authorization", token);
        }
        String resp = req.execute().body();
        log.info("外部REST刷写取内容: snCode={}, url={}, respLen={}", ctx.getSnCode(), url, resp == null ? 0 : resp.length());
        return FlashContent.builder()
            .contentRefType("EXTERNAL")
            .contentPayload(resp)
            .build();
    }

    @Override
    public FlashVerifyResult verify(FlashContext ctx, FlashContent expected, FlashRecordBo actual) {
        // EXTERNAL类: 内容已获取即视为可刷写; 实际结果以上报 result 为准
        return FlashVerifyResult.builder()
            .pass(true)
            .contentRefType(expected.getContentRefType())
            .contentPayload(expected.getContentPayload())
            .build();
    }

    /**
     * SSRF 基础校验: 禁止回环/内网地址(防止服务端请求伪造)
     */
    private void checkSsrf(String url) {
        try {
            URL u = new URL(url);
            if (!"http".equals(u.getProtocol()) && !"https".equals(u.getProtocol())) {
                throw new ServiceException("SSRF: 仅允许 http/https 协议");
            }
            InetAddress addr = InetAddress.getByName(u.getHost());
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isAnyLocalAddress()) {
                throw new ServiceException("SSRF 防护: 禁止访问回环/内网地址 " + url);
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException("URL 校验失败: " + e.getMessage());
        }
    }
}
