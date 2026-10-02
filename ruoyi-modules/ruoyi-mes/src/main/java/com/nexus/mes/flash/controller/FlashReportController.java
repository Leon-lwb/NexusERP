package com.nexus.mes.flash.controller;

import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.flash.domain.bo.FlashRecordBo;
import com.nexus.mes.flash.domain.bo.TestReportBo;
import com.nexus.mes.flash.service.IFlashRecordService;
import com.nexus.mes.flash.service.ITestRecordService;
import com.nexus.mes.flash.service.impl.MesReportTenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * MES 固件刷写/测试自动化上报接口 (AUTO 对接, 产线设备/上位机)
 * <p>
 * 免登录开放接口(security.excludes 已放行 /mes/flash/report/**), 通过请求头 X-API-KEY 鉴权
 * (密钥配置项 mes.api-key, 无代码内默认值——未配置时所有上报接口 fail-closed 拒绝, 生产环境通过环境变量 MES_API_KEY 注入)。
 * 上报数据按解析出的<b>设备所属租户</b>落库(MES-FLASH-10: 按 equipmentCode/SN 反查, 不再硬编码 000000)。
 * 宽松模式: 单条失败不中断整批, 返回失败明细。
 * </p>
 * <ul>
 *   <li>POST /mes/flash/report/flash/auto — 批量刷写结果上报: [{snCode, contentRefId, equipmentId?, result: PASS/FAIL, contentChecksum?, retryCount?, processCode?, processName?, remark?}]</li>
 *   <li>POST /mes/flash/report/test/auto — 批量测试结果上报: [{snCode, contentRefId?, equipmentCode?, items: [{itemCode, value}]}]</li>
 * </ul>
 *
 * @author Nexus
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/flash/report")
public class FlashReportController extends BaseController {

    private final IFlashRecordService mesFlashRecordService;
    private final ITestRecordService mesTestRecordService;
    /** MES-FLASH-10: 设备上报租户解析(按设备/SN 记录, 替代硬编码 000000) */
    private final MesReportTenantResolver mesReportTenantResolver;

    /** 设备上报密钥(application.yml/环境变量 MES_API_KEY 配置, 未配置时 fail-closed 拒绝所有上报) */
    @Value("${mes.api-key:}")
    private String apiKey;

    /**
     * 批量刷写结果上报(设备刷写固件后回调)
     */
    @Log(title = "刷写AUTO上报", businessType = BusinessType.INSERT)
    @PostMapping("/flash/auto")
    public R<Map<String, Object>> reportFlash(@RequestHeader(value = "X-API-KEY", required = false) String apiKey,
                                              @RequestBody @Valid List<FlashRecordBo> list,
                                              HttpServletRequest request) {
        checkApiKey(apiKey, request);
        String tenantId = mesReportTenantResolver.resolveFlashTenant(list);
        Map<String, Object> map = TenantHelper.dynamic(tenantId,
            () -> mesFlashRecordService.reportFlashBatch(list));
        return R.ok(map);
    }

    /**
     * 批量测试结果上报(测试设备/综测仪测毕回调, 一 SN 一单多行自动判定)
     */
    @Log(title = "测试AUTO上报", businessType = BusinessType.INSERT)
    @PostMapping("/test/auto")
    public R<Map<String, Object>> reportTest(@RequestHeader(value = "X-API-KEY", required = false) String apiKey,
                                             @RequestBody @Valid List<TestReportBo> list,
                                             HttpServletRequest request) {
        checkApiKey(apiKey, request);
        String tenantId = mesReportTenantResolver.resolveTestTenant(list);
        Map<String, Object> map = TenantHelper.dynamic(tenantId,
            () -> mesTestRecordService.reportTestAutoBatch(list));
        return R.ok(map);
    }

    /**
     * 工位编排: 产线工位拉取 SN 待刷清单(X-API-KEY 鉴权, 返回工单刷写工序+每绑定项最新结果)
     */
    @GetMapping("/station/pending")
    public R<List<Map<String, Object>>> stationPending(@RequestHeader(value = "X-API-KEY", required = false) String apiKey,
                                                       @RequestParam String snCode,
                                                       HttpServletRequest request) {
        checkApiKey(apiKey, request);
        return R.ok(mesFlashRecordService.selectStationPending(snCode));
    }

    // ==================== 私有辅助方法 ====================

    /**
     * API 密钥校验: 请求头 X-API-KEY 须与配置 mes.api-key 一致。
     * <p>安全: 配置为空/未配置时直接拒绝（fail-closed）——该接口在免登录白名单内，
     * 密钥是唯一防线；空密钥若放行等于无防护（可伪造任意 SN 的 PASS 刷写记录绕过门禁）。</p>
     */
    private void checkApiKey(String headerKey, HttpServletRequest request) {
        if (StringUtils.isBlank(apiKey)) {
            log.error("mes.api-key 未配置（生产环境请通过环境变量 MES_API_KEY 注入），拒绝所有上报请求");
            throw new ServiceException("上报服务未配置访问密钥，请联系管理员");
        }
        if (StringUtils.isBlank(headerKey) || !apiKey.equals(headerKey.trim())) {
            log.warn("上报接口密钥校验失败: ip={}, uri={}", request.getRemoteAddr(), request.getRequestURI());
            throw new ServiceException("无效的API密钥");
        }
    }
}