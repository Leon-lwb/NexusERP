package com.nexus.station.controller;

import cn.hutool.core.io.IoUtil;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.oss.core.OssClient;
import com.nexus.common.oss.factory.OssFactory;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.sop.domain.SopDocument;
import com.nexus.station.service.StationSopService;
import com.nexus.system.domain.vo.SysOssVo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.Map;

/**
 * 工位终端 SOP 设备通道接口 (nexus-station, M3 契约 C16-C18)
 * <p>
 * 免登录(security.excludes 已放行 /mes/station/**), X-API-KEY 鉴权, 租户固定 000000。
 * 工位端按当前工序拉取绑定的已发布 SOP 文档列表(全屏轮播),
 * 文件经本控制器流式代理下载(MinIO 私有桶, 客户端下载器带 X-API-KEY 头直连,
 * 不走登录态签名 URL —— 对齐固件下载 M1-b 模式)。
 * </p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/station/sop")
public class StationSopController {

    private final StationSopService stationSopService;

    /** 设备上报密钥(与 StationDeviceController/FlashReportController 共用) */
    @Value("${mes.api-key:}")
    private String apiKey;

    /**
     * 工位当前 SOP 文档列表(C16): 按绑定顺序返回已发布文档(is_default 优先),
     * 并逐文档登记查阅留痕(operatorName 为在岗操作员)。
     */
    @GetMapping("/current")
    public R<Map<String, Object>> current(@RequestHeader(value = "X-API-KEY", required = false) String headerKey,
                                          @RequestParam String stationCode,
                                          @RequestParam(required = false) String operatorName,
                                          HttpServletRequest request) {
        checkApiKey(headerKey, request);
        Map<String, Object> data = TenantHelper.dynamic("000000",
            () -> stationSopService.current(stationCode, operatorName, request.getRemoteAddr()));
        return R.ok(data);
    }

    /**
     * SOP 版本轻量查询(C18, 客户端 5 分钟轮询比对用): 仅 sopId+versionNo, 不登记查阅日志。
     */
    @GetMapping("/version-check")
    public R<Map<String, Object>> versionCheck(@RequestHeader(value = "X-API-KEY", required = false) String headerKey,
                                               @RequestParam String stationCode,
                                               HttpServletRequest request) {
        checkApiKey(headerKey, request);
        Map<String, Object> data = TenantHelper.dynamic("000000",
            () -> stationSopService.versionCheck(stationCode));
        return R.ok(data);
    }

    /**
     * SOP 文件下载(C17): X-API-KEY 鉴权 → PUBLISHED 文档 → MinIO 流式代理输出。
     * <p>仅支持当前发布版(主档 file_oss_id 由发布动作从 CURRENT 版本同步, 无需版本参数)。</p>
     */
    @GetMapping("/{sopId}/download")
    @DS("mes")
    public void download(@RequestHeader(value = "X-API-KEY", required = false) String headerKey,
                         @PathVariable Long sopId,
                         HttpServletRequest request,
                         HttpServletResponse response) {
        checkApiKey(headerKey, request);
        TenantHelper.dynamic("000000", () -> {
            doDownload(sopId, response);
            return null;
        });
    }

    private void doDownload(Long sopId, HttpServletResponse response) {
        Map<String, Object> resolved = stationSopService.resolveDownload(sopId);
        SopDocument doc = (SopDocument) resolved.get("doc");
        SysOssVo ossVo = (SysOssVo) resolved.get("ossVo");
        OssClient ossClient = OssFactory.instance(ossVo.getService());
        String fileName = "sop-" + doc.getSopCode() + "-v" + doc.getCurrentVersion();
        try (InputStream in = ossClient.getObjectContent(ossVo.getFileName())) {
            response.setContentType(contentTypeOf(doc.getFileType()));
            response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
            IoUtil.copy(in, response.getOutputStream());
            response.flushBuffer();
            log.info("SOP 文件下载完成: sopId={}, code={}, version={}", sopId, doc.getSopCode(), doc.getCurrentVersion());
        } catch (Exception e) {
            throw new ServiceException("SOP 文件读取失败: " + e.getMessage());
        }
    }

    /** Content-Type 按文档类型细化(客户端下载器按流落盘, 主要供人工核对) */
    private String contentTypeOf(String fileType) {
        if (fileType == null) {
            return "application/octet-stream";
        }
        return switch (fileType) {
            case "PDF" -> "application/pdf";
            case "IMAGE" -> "image/*";
            case "VIDEO" -> "video/mp4";
            default -> "application/octet-stream";
        };
    }

    /**
     * API 密钥校验(fail-closed): 与 StationDeviceController 一致。
     */
    private void checkApiKey(String headerKey, HttpServletRequest request) {
        if (StringUtils.isBlank(apiKey)) {
            log.error("mes.api-key 未配置（生产环境请通过环境变量 MES_API_KEY 注入），拒绝工位设备通道请求");
            throw new ServiceException("工位服务未配置访问密钥，请联系管理员");
        }
        if (StringUtils.isBlank(headerKey) || !apiKey.equals(headerKey.trim())) {
            log.warn("工位设备通道密钥校验失败: ip={}, uri={}", request.getRemoteAddr(), request.getRequestURI());
            throw new ServiceException("无效的API密钥");
        }
    }
}
