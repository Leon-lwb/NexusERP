package com.nexus.station.controller;

import cn.hutool.core.io.IoUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.oss.core.OssClient;
import com.nexus.common.oss.factory.OssFactory;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.flash.domain.Firmware;
import com.nexus.mes.flash.mapper.FirmwareMapper;
import com.nexus.mes.flash.provider.FlashContent;
import com.nexus.mes.flash.provider.FlashContext;
import com.nexus.mes.flash.provider.FlashTypeProvider;
import com.nexus.mes.flash.provider.FlashTypeRegistry;
import com.nexus.mes.flash.provider.ResourceAllocation;
import com.nexus.mes.flash.service.IMesResourcePoolService;
import com.nexus.mes.flash.service.IFlashRecordService;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.system.domain.vo.SysOssVo;
import com.nexus.system.service.ISysOssService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工位终端刷写 prepare/固件下载接口 (nexus-station, M1 遗留契约 M1-a/M1-b)
 * <p>
 * URL 前缀 /mes/flash/report/station(security.excludes 已放行 /mes/flash/report/**),
 * X-API-KEY 鉴权, 租户固定 000000。
 * </p>
 *
 * <ul>
 *   <li>POST /mes/flash/report/station/prepare — 刷写内容预取/池值预分配(幂等, 客户端 404 降级直接刷写)</li>
 *   <li>GET /mes/flash/report/firmware/{id}/download — 固件文件下载流(X-API-KEY, 客户端做 MD5 校验)</li>
 * </ul>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/flash/report")
public class StationFlashPrepareController {

    private final FirmwareMapper firmwareMapper;
    private final ProSnMapper proSnMapper;
    private final FlashTypeRegistry flashTypeRegistry;
    private final IFlashRecordService flashRecordService;
    private final IMesResourcePoolService resourcePoolService;
    private final ISysOssService sysOssService;

    /** 设备上报密钥(与 FlashReportController 共用) */
    @Value("${mes.api-key:}")
    private String apiKey;

    /**
     * 刷写内容预取(M1-a): {snCode, flashTypeCode} → 定位 SN 待刷项, 预解析应刷内容。
     * <p>固件类返回 firmwareUrl(M1-b 下载端点, 相对路径)/contentChecksum/contentVersion;
     * 池类(MAC/SN)调用 Provider.allocate 预分配资源。幂等: 池资源已绑定时返回既有绑定。</p>
     * <p>无待刷项返回 data=null, 客户端按无任务处理(或 404 降级直接刷写)。</p>
     */
    @PostMapping("/station/prepare")
    @DS("mes")
    public R<Map<String, Object>> prepare(@RequestHeader(value = "X-API-KEY", required = false) String headerKey,
                                          @RequestBody Map<String, Object> body,
                                          HttpServletRequest request) {
        checkApiKey(headerKey, request);
        String snCode = body.get("snCode") == null ? null : String.valueOf(body.get("snCode")).trim();
        String flashTypeCode = body.get("flashTypeCode") == null ? null : String.valueOf(body.get("flashTypeCode")).trim();
        if (StringUtils.isBlank(snCode)) {
            throw new ServiceException("snCode 不能为空");
        }
        return R.ok(TenantHelper.dynamic("000000", () -> doPrepare(snCode, flashTypeCode)));
    }

    /**
     * 固件文件下载(M1-b): X-API-KEY 鉴权 → RELEASED 固件 → MinIO 流式代理输出
     * (客户端下载器直连本服务并带 X-API-KEY 头, 不走签名 URL)。
     */
    @GetMapping("/firmware/{firmwareId}/download")
    @DS("mes")
    public void downloadFirmware(@RequestHeader(value = "X-API-KEY", required = false) String headerKey,
                                 @PathVariable Long firmwareId,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        checkApiKey(headerKey, request);
        TenantHelper.dynamic("000000", () -> {
            doDownload(firmwareId, response);
            return null;
        });
    }

    // ==================== 私有实现 ====================

    private Map<String, Object> doPrepare(String snCode, String flashTypeCode) {
        ProSn sn = proSnMapper.selectOne(Wrappers.<ProSn>lambdaQuery()
            .eq(ProSn::getSnCode, snCode).last("LIMIT 1"));
        if (sn == null) {
            throw new ServiceException("SN不存在: " + snCode);
        }
        // 定位待刷项: 复用既有 station/pending 逻辑(已按工序绑定+最新结果过滤)
        List<Map<String, Object>> pending = flashRecordService.selectStationPending(snCode);
        Map<String, Object> target = pending.stream()
            .filter(p -> flashTypeCode == null || flashTypeCode.equals(p.get("flashTypeCode")))
            .filter(p -> !"PASS".equals(p.get("latestResult")))
            .findFirst()
            .orElse(null);
        if (target == null) {
            return null;
        }
        String typeCode = (String) target.get("flashTypeCode");
        Long contentRefId = (Long) target.get("contentRefId");
        FlashTypeProvider provider = flashTypeRegistry.getProvider(typeCode);

        Map<String, Object> data = new HashMap<>(8);
        data.put("flashTypeCode", typeCode);
        data.put("contentRefId", contentRefId);

        if ("POOL".equals(provider.getSourceType())) {
            // 池类: 预分配资源(allocate 幂等: 同 SN 重复分配返回已绑定资源)
            ResourceAllocation allocation = provider.allocate(FlashContext.builder()
                .snId(sn.getSnId()).snCode(sn.getSnCode())
                .mesWoId(sn.getMesWoId()).woNo(sn.getWoNo()).cardId(sn.getCardId())
                .flashTypeCode(typeCode).contentRefId(contentRefId)
                .snMaterialId(sn.getMaterialId())
                .build());
            if (allocation != null) {
                data.put("payload", allocation.getContentPayload());
                data.put("contentRefId", allocation.getContentRefId());
            }
            return data;
        }

        // 固件/配置类: resolveContent 校验 RELEASED + 物料匹配, 返回版本与 MD5
        FlashContent content = provider.resolveContent(FlashContext.builder()
            .snId(sn.getSnId()).snCode(sn.getSnCode())
            .mesWoId(sn.getMesWoId()).woNo(sn.getWoNo()).cardId(sn.getCardId())
            .flashTypeCode(typeCode).contentRefId(contentRefId)
            .snMaterialId(sn.getMaterialId())
            .build());
        if (content == null) {
            return data;
        }
        data.put("contentVersion", content.getContentVersion());
        data.put("contentChecksum", content.getContentChecksum());
        if (content.getContentRefId() != null && isFirmwareType(typeCode)) {
            data.put("firmwareUrl", "/mes/flash/report/firmware/" + content.getContentRefId() + "/download");
        }
        // INLINE 类内容直接下发
        if (StringUtils.isNotBlank(content.getContentPayload())) {
            data.put("payload", content.getContentPayload());
        }
        return data;
    }

    private void doDownload(Long firmwareId, HttpServletResponse response) {
        Firmware fw = firmwareMapper.selectById(firmwareId);
        if (fw == null) {
            throw new ServiceException("固件不存在: " + firmwareId);
        }
        if (!"RELEASED".equals(fw.getStatus())) {
            throw new ServiceException("仅已发布(RELEASED)固件可下载: " + fw.getFirmwareCode() + " 当前=" + fw.getStatus());
        }
        if (fw.getOssId() == null) {
            throw new ServiceException("固件未上传文件: " + fw.getFirmwareCode());
        }
        // sys_oss 位于 ry-vue(master) 库: @DS("mes") 已切线程数据源, 手动 push master 读取(FirmwareServiceImpl 先例)
        com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder.push("master");
        SysOssVo ossVo;
        try {
            ossVo = sysOssService.getById(fw.getOssId());
        } finally {
            com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder.poll();
        }
        if (ossVo == null || StringUtils.isBlank(ossVo.getFileName())) {
            throw new ServiceException("固件文件记录不存在: ossId=" + fw.getOssId());
        }
        OssClient ossClient = OssFactory.instance(ossVo.getService());
        String fileName = "firmware-" + fw.getFirmwareCode() + "-v" + fw.getVersion();
        try (InputStream in = ossClient.getObjectContent(ossVo.getFileName())) {
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
            if (fw.getFileSize() != null && fw.getFileSize() > 0) {
                response.setContentLengthLong(fw.getFileSize());
            }
            IoUtil.copy(in, response.getOutputStream());
            response.flushBuffer();
            log.info("固件下载完成: firmwareId={}, code={}, version={}, md5={}",
                firmwareId, fw.getFirmwareCode(), fw.getVersion(), fw.getFileMd5());
        } catch (Exception e) {
            throw new ServiceException("固件文件读取失败: " + e.getMessage());
        }
    }

    /** 是否固件文件类刷写类型(下载端点仅对固件文件类有意义)。 */
    private boolean isFirmwareType(String typeCode) {
        return "firmware_file".equals(typeCode);
    }

    /**
     * API 密钥校验(fail-closed): 与 FlashReportController/StationDeviceController 一致。
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
