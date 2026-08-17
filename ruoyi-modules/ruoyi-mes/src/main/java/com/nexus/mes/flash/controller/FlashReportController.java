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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * MES 固件刷写/测试自动化上报接口 (AUTO 对接, 产线设备/上位机)
 * <p>
 * 免登录开放接口(security.excludes 已放行 /mes/flash/report/**), 通过请求头 X-API-KEY 鉴权
 * (密钥配置项 mes.api-key, 默认 nexus-mes-api-2026, 生产环境务必修改)。
 * 上报数据按当前租户 000000 落库。宽松模式: 单条失败不中断整批, 返回失败明细。
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

    /** 设备上报密钥(application.yml 配置, 生产务必修改) */
    @Value("${mes.api-key:nexus-mes-api-2026}")
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
        Map<String, Object> map = TenantHelper.dynamic("000000",
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
        Map<String, Object> map = TenantHelper.dynamic("000000",
            () -> mesTestRecordService.reportTestAutoBatch(list));
        return R.ok(map);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * API 密钥校验: 请求头 X-API-KEY 须与配置 mes.api-key 一致
     */
    private void checkApiKey(String headerKey, HttpServletRequest request) {
        if (StringUtils.isBlank(headerKey) || !apiKey.equals(headerKey.trim())) {
            log.warn("上报接口密钥校验失败: ip={}, uri={}, key={}", request.getRemoteAddr(), request.getRequestURI(), headerKey);
            throw new ServiceException("无效的API密钥");
        }
    }
}