package com.nexus.station.controller;

import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.station.mapper.StationSchWorkerMapper;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.service.ISysUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 工位终端设备通道接口 (nexus-station 工位机)
 * <p>
 * 免登录开放接口(security.excludes 已放行 /mes/station/**), 通过请求头 X-API-KEY 鉴权
 * (密钥配置项 mes.api-key, 与 FlashReportController 共用)。
 * 查询按固定租户 000000 执行, 与刷写/测试上报通道对齐。
 * </p>
 *
 * <ul>
 *   <li>GET /mes/station/operator?code=工号 — 扫员工工号条码上岗校验(M2 契约 C1)</li>
 * </ul>
 *
 * @author Nexus
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/station")
public class StationDeviceController {

    private final StationSchWorkerMapper schWorkerMapper;
    private final ISysUserService sysUserService;

    /** 设备上报密钥(application.yml 配置, 生产务必修改) */
    @Value("${mes.api-key:nexus-mes-api-2026}")
    private String apiKey;

    /**
     * 扫工号上岗校验(C1): 员工工号 → 工人档案 → 关联系统账号, 返回用户标识。
     * <p>流程: ① sch_worker 按 employee_id 查工人档案(不存在/已离职拒绝);
     * ② 档案关联 user_id 查 sys_user 账号(未关联/已停用拒绝)。</p>
     * <p>userName 返回昵称(无昵称回退档案姓名), 供工位机顶栏直接显示;
     * 资质校验 M3 再加(档案 work_category/skill_level 已一并返回)。</p>
     */
    @GetMapping("/operator")
    public R<Map<String, Object>> operatorLookup(@RequestHeader(value = "X-API-KEY", required = false) String headerKey,
                                                 @RequestParam String code,
                                                 HttpServletRequest request) {
        checkApiKey(headerKey, request);
        String employeeId = StringUtils.trim(code);
        Map<String, Object> worker = TenantHelper.dynamic("000000",
            () -> schWorkerMapper.selectByEmployeeId(employeeId));
        if (worker == null) {
            throw new ServiceException("工号不存在: " + employeeId);
        }
        if ("departure".equals(worker.get("work_status"))) {
            throw new ServiceException("该员工已离职, 无法上岗: " + worker.get("user_name"));
        }
        Long userId = toLong(worker.get("user_id"));
        if (userId == null) {
            throw new ServiceException("工人档案未关联系统账号, 请先在员工档案中绑定: " + worker.get("user_name"));
        }
        SysUserVo user = TenantHelper.dynamic("000000", () -> sysUserService.selectUserById(userId));
        if (user == null) {
            throw new ServiceException("工人档案关联的账号不存在(userId=" + userId + ")");
        }
        if (!"0".equals(user.getStatus())) {
            throw new ServiceException("关联账号已停用, 无法上岗: " + user.getUserName());
        }
        Map<String, Object> data = new HashMap<>(4);
        data.put("userId", user.getUserId());
        data.put("userName", StringUtils.isNotBlank(user.getNickName()) ? user.getNickName() : worker.get("user_name"));
        data.put("employeeId", worker.get("employee_id"));
        data.put("workshopName", worker.get("workshop_name"));
        return R.ok(data);
    }

    // ==================== 私有辅助方法 ==================

    private Long toLong(Object value) {
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    /**
     * API 密钥校验: 请求头 X-API-KEY 须与配置 mes.api-key 一致。
     * <p>安全: 配置为空/未配置时直接拒绝（fail-closed）——该接口在免登录白名单内，
     * 密钥是唯一防线; 空密钥若放行等于任何人可冒用任意工号上岗。</p>
     */
    private void checkApiKey(String headerKey, HttpServletRequest request) {
        if (StringUtils.isBlank(apiKey)) {
            log.error("mes.api-key 未配置（生产环境请通过环境变量 MES_API_KEY 注入），拒绝工位设备通道请求");
            throw new ServiceException("工位服务未配置访问密钥，请联系管理员");
        }
        if (StringUtils.isBlank(headerKey) || !apiKey.equals(headerKey.trim())) {
            log.warn("工位设备通道密钥校验失败: ip={}, uri={}, key={}", request.getRemoteAddr(), request.getRequestURI(), headerKey);
            throw new ServiceException("无效的API密钥");
        }
    }
}
