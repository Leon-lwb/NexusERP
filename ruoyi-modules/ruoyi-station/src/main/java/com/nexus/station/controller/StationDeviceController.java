package com.nexus.station.controller;

import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.station.domain.dto.StationAndonCallDto;
import com.nexus.station.domain.dto.StationBatchReportDto;
import com.nexus.station.domain.dto.StationInspectionSubmitDto;
import com.nexus.station.domain.dto.StationPassReportDto;
import com.nexus.station.domain.dto.StationPassValidateDto;
import com.nexus.station.domain.dto.StationSnDtos;
import com.nexus.station.mapper.StationDefectCodeMapper;
import com.nexus.station.mapper.StationSchWorkerMapper;
import com.nexus.station.service.StationAndonService;
import com.nexus.station.service.StationInspectionService;
import com.nexus.station.service.StationPassService;
import com.nexus.station.service.StationSnService;
import com.nexus.station.service.StationTraceService;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.service.ISysUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
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
 *   <li>POST /mes/station/pass/validate — SN 过站四重校验(C2)</li>
 *   <li>POST /mes/station/pass/report — SN 单件过站报工(C3, 幂等)</li>
 *   <li>POST /mes/station/pass/report-batch — 批量计数报工(C4, 幂等)</li>
 *   <li>POST /mes/station/sn/bind — 关键件批量绑定(C5, 防环)</li>
 *   <li>POST /mes/station/sn/unbind — 关键件解绑/换板(C6, 留痕)</li>
 *   <li>GET /mes/station/defect-codes — 不良代码字典(C9, ncr_defect_code 三级代码)</li>
 *   <li>POST /mes/station/andon/call — 安灯四键呼叫(C7)</li>
 *   <li>GET /mes/station/andon/latest?stationCode= — 工位最新安灯状态(C8)</li>
 *   <li>GET /mes/station/trace/{snCode} — SN 全履历追溯查询(M3 契约 C13)</li>
 *   <li>GET /mes/station/inspection/tasks?stationCode= — 工位当日点检任务(M3 契约 C14)</li>
 *   <li>POST /mes/station/inspection/submit — 点检提交留痕(M3 契约 C15, 不合格联动 FAULT+安灯)</li>
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
    private final StationDefectCodeMapper defectCodeMapper;
    private final ISysUserService sysUserService;
    private final StationAndonService stationAndonService;
    private final StationPassService stationPassService;
    private final StationSnService stationSnService;
    private final StationTraceService stationTraceService;
    private final StationInspectionService stationInspectionService;

    /** 设备上报密钥(application.yml/环境变量 MES_API_KEY 配置, 未配置时 fail-closed 拒绝所有请求) */
    @Value("${mes.api-key:}")
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

    /**
     * SN 过站四重校验(C2): PREV_STEP/STATION_MATCH/DUPLICATE/QUALITY_HOLD 一次返回,
     * 客户端按 pass 渲染红绿屏。
     */
    @PostMapping("/pass/validate")
    public R<Map<String, Object>> passValidate(@RequestHeader(value = "X-API-KEY", required = false) String headerKey,
                                               @RequestBody StationPassValidateDto dto,
                                               HttpServletRequest request) {
        checkApiKey(headerKey, request);
        Map<String, Object> data = TenantHelper.dynamic("000000",
            () -> stationPassService.validate(dto.getSnCode(), dto.getStationCode()));
        return R.ok(data);
    }

    /**
     * SN 单件过站报工(C3): 幂等键 snCode+stationCode+clientTs 落唯一索引,
     * 离线队列重放重复上报直接返回成功。
     * <p>操作员在事务外解析(sch_worker 在 ry-erp 库, 事务内连接已钉在 ry-mes)。</p>
     */
    @PostMapping("/pass/report")
    public R<Map<String, Object>> passReport(@RequestHeader(value = "X-API-KEY", required = false) String headerKey,
                                             @RequestBody StationPassReportDto dto,
                                             HttpServletRequest request) {
        checkApiKey(headerKey, request);
        try {
            Map<String, Object> data = TenantHelper.dynamic("000000", () -> {
                StationPassService.OperatorInfo operator = stationPassService.resolveOperator(
                    dto.getOperatorId(), dto.getOperatorCode());
                return stationPassService.reportSingle(dto, operator);
            });
            return R.ok(data);
        } catch (TransientDataAccessException | DataAccessResourceFailureException | RecoverableDataAccessException | TransactionException retryable) {
            // 已退出服务事务代理；回滚或提交结果不确定时只保持原载荷重试，不在失败事务内部重跑。
            return R.fail(503, "过站事务暂不可用或结果待确认，请保持原上报内容稍后重试");
        }
    }

    /**
     * 批量计数报工(C4): 幂等键 stationCode+clientTs, goodQty/defects 增量累加到工位在制工序。
     */
    @PostMapping("/pass/report-batch")
    public R<Map<String, Object>> passReportBatch(@RequestHeader(value = "X-API-KEY", required = false) String headerKey,
                                                  @RequestBody StationBatchReportDto dto,
                                                  HttpServletRequest request) {
        checkApiKey(headerKey, request);
        Map<String, Object> data = TenantHelper.dynamic("000000", () -> {
            StationPassService.OperatorInfo operator = stationPassService.resolveOperator(
                null, dto.getOperatorCode());
            return stationPassService.reportBatch(dto, operator);
        });
        return R.ok(data);
    }

    /**
     * 关键件批量绑定(C5): 组件挂到父SN(parent_sn_id), 防环校验(链上限10)。
     */
    @PostMapping("/sn/bind")
    public R<Map<String, Object>> snBind(@RequestHeader(value = "X-API-KEY", required = false) String headerKey,
                                         @RequestBody StationSnDtos.BindDto dto,
                                         HttpServletRequest request) {
        checkApiKey(headerKey, request);
        Map<String, Object> data = TenantHelper.dynamic("000000",
            () -> stationSnService.bind(dto));
        return R.ok(data);
    }

    /**
     * 关键件解绑/换板(C6): 解绑留痕; 传 newComponentSnCode 一步换板(解绑+重绑)。
     */
    @PostMapping("/sn/unbind")
    public R<Map<String, Object>> snUnbind(@RequestHeader(value = "X-API-KEY", required = false) String headerKey,
                                           @RequestBody StationSnDtos.UnbindDto dto,
                                           HttpServletRequest request) {
        checkApiKey(headerKey, request);
        Map<String, Object> data = TenantHelper.dynamic("000000",
            () -> stationSnService.unbind(dto));
        return R.ok(data);
    }

    /**
     * 不良代码字典(C9): ncr_defect_code 启用的三级代码, 映射 {code, label};
     * 空表返回空数组(客户端有静态兜底表)。
     */
    @GetMapping("/defect-codes")
    public R<List<Map<String, Object>>> defectCodes(@RequestHeader(value = "X-API-KEY", required = false) String headerKey,
                                                    HttpServletRequest request) {
        checkApiKey(headerKey, request);
        List<Map<String, Object>> data = TenantHelper.dynamic("000000", () ->
            defectCodeMapper.selectEnabledLeafCodes().stream()
                .map(c -> {
                    Map<String, Object> item = new HashMap<>(4);
                    item.put("code", c.getCode());
                    item.put("label", c.getName());
                    return item;
                })
                .toList());
        return R.ok(data);
    }

    /**
     * 安灯四键呼叫(C7): MATERIAL/REPAIR/PERSON/QUALITY 映射后走既有安灯逻辑, SSE 推送到看板。
     */
    @PostMapping("/andon/call")
    public R<Map<String, Object>> andonCall(@RequestHeader(value = "X-API-KEY", required = false) String headerKey,
                                            @RequestBody StationAndonCallDto dto,
                                            HttpServletRequest request) {
        checkApiKey(headerKey, request);
        if (StringUtils.isBlank(dto.getCallType())) {
            throw new ServiceException("呼叫类型不能为空");
        }
        Map<String, Object> data = TenantHelper.dynamic("000000",
            () -> stationAndonService.call(dto.getCallType(), dto.getCallReason(), dto.getStationCode(),
                dto.getOperatorCode(), dto.getWoNo(), dto.getCardNo()));
        return R.ok(data);
    }

    /**
     * 工位最新安灯状态查询(C8): 无记录时 data=null(客户端顶栏隐藏状态标签)。
     */
    @GetMapping("/andon/latest")
    public R<Map<String, Object>> andonLatest(@RequestHeader(value = "X-API-KEY", required = false) String headerKey,
                                              @RequestParam String stationCode,
                                              HttpServletRequest request) {
        checkApiKey(headerKey, request);
        Map<String, Object> data = TenantHelper.dynamic("000000",
            () -> stationAndonService.latest(stationCode));
        return R.ok(data);
    }

    /**
     * SN 全履历追溯查询(M3 契约 C13): SN 概要 + 工序链 + 四源记录时间线(倒序)。
     * <p>四源: 刷写/综测(按测试单分组)/过站报工/关键件绑定, 供工位端"这块板烧的什么版本"类现场问答。</p>
     */
    @GetMapping("/trace/{snCode}")
    public R<Map<String, Object>> trace(@RequestHeader(value = "X-API-KEY", required = false) String headerKey,
                                        @PathVariable String snCode,
                                        HttpServletRequest request) {
        checkApiKey(headerKey, request);
        Map<String, Object> data = TenantHelper.dynamic("000000",
            () -> stationTraceService.trace(snCode));
        return R.ok(data);
    }

    /**
     * 工位当日点检任务(M3 契约 C14): 联动链定位工位设备清单,
     * 逐台返回点检项(eq_equipment.inspection_items, 兼容新旧格式)与当日已检状态。
     */
    @GetMapping("/inspection/tasks")
    public R<Map<String, Object>> inspectionTasks(@RequestHeader(value = "X-API-KEY", required = false) String headerKey,
                                                  @RequestParam String stationCode,
                                                  HttpServletRequest request) {
        checkApiKey(headerKey, request);
        Map<String, Object> data = TenantHelper.dynamic("000000",
            () -> stationInspectionService.tasks(stationCode));
        return R.ok(data);
    }

    /**
     * 工位点检提交(M3 契约 C15): 留痕 eq_inspection_record + 同步 last_inspection;
     * 不合格联动 设备置 FAULT + REPAIR 安灯(均降级)。
     * <p>操作员在事务外解析(sch_worker 在 ry-erp 库, 事务内连接已钉在 ry-mes)。</p>
     */
    @PostMapping("/inspection/submit")
    public R<Map<String, Object>> inspectionSubmit(@RequestHeader(value = "X-API-KEY", required = false) String headerKey,
                                                   @RequestBody StationInspectionSubmitDto dto,
                                                   HttpServletRequest request) {
        checkApiKey(headerKey, request);
        Map<String, Object> data = TenantHelper.dynamic("000000", () -> {
            StationPassService.OperatorInfo operator = stationPassService.resolveOperator(
                null, dto.getOperatorCode());
            return stationInspectionService.submit(dto, operator);
        });
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
            log.warn("工位设备通道密钥校验失败: ip={}, uri={}", request.getRemoteAddr(), request.getRequestURI());
            throw new ServiceException("无效的API密钥");
        }
    }
}
