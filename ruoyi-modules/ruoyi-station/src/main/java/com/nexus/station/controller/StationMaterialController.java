package com.nexus.station.controller;

import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.station.domain.dto.StationMaterialLoadDto;
import com.nexus.station.service.StationMaterialService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 工位物料上料防错接口 (nexus-station, M3 契约 C19/C20)
 * <p>
 * 免登录(security.excludes 已放行 /mes/station/**), X-API-KEY 鉴权, 租户固定 000000。
 * 扫批次码/条码按工单 MBOM 快照(含替代料)比对, 匹配登记上料台账(换料自动置 REPLACED);
 * 不匹配返回允许上料清单(客户端红屏展示)。
 * </p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/station/material")
public class StationMaterialController {

    private final StationMaterialService stationMaterialService;

    /** 设备上报密钥(与 StationDeviceController 共用) */
    @Value("${mes.api-key:}")
    private String apiKey;

    /**
     * 上料校验+登记(C19): 扫批次码(B)/条码(RC) → MBOM 快照(含替代料)比对 → 匹配写台账。
     */
    @PostMapping("/load")
    public R<Map<String, Object>> load(@RequestHeader(value = "X-API-KEY", required = false) String headerKey,
                                       @RequestBody StationMaterialLoadDto dto,
                                       HttpServletRequest request) {
        checkApiKey(headerKey, request);
        Map<String, Object> data = TenantHelper.dynamic("000000",
            () -> stationMaterialService.load(dto));
        return R.ok(data);
    }

    /**
     * 当前上料状态(C20): 应上物料清单(逐项合并已上批次) + 工位当前台账。
     */
    @GetMapping("/current")
    public R<Map<String, Object>> current(@RequestHeader(value = "X-API-KEY", required = false) String headerKey,
                                          @RequestParam String stationCode,
                                          HttpServletRequest request) {
        checkApiKey(headerKey, request);
        Map<String, Object> data = TenantHelper.dynamic("000000",
            () -> stationMaterialService.current(stationCode));
        return R.ok(data);
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
