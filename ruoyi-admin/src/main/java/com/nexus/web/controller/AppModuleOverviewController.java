package com.nexus.web.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.core.domain.R;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdProductionPlanMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.domain.EqPatrolTask;
import com.nexus.mes.eq.domain.EqRepair;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.eq.mapper.EqPatrolTaskMapper;
import com.nexus.mes.eq.mapper.EqRepairMapper;
import com.nexus.mes.qc.domain.QcIqc;
import com.nexus.mes.qc.domain.QcIpqc;
import com.nexus.mes.qc.domain.QcOqc;
import com.nexus.mes.qc.mapper.QcIqcMapper;
import com.nexus.mes.qc.mapper.QcIpqcMapper;
import com.nexus.mes.qc.mapper.QcOqcMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * App 业务模块概览 Controller
 * <p>补齐《接口状态与后端缺口清单》§5.4 缺失的模块入口概览接口：
 * {@code /app/prd/overview}（生产）、{@code /app/eam/overview}（设备）、
 * {@code /app/qm/overview}（质量）、{@code /app/report/menus}（报表中心菜单）。
 * <p>实现约定：
 * <ul>
 *   <li>只聚合、不新增表；全部走 MyBatis-Plus 条件构造器，自动继承逻辑删除与租户隔离</li>
 *   <li>每个指标独立降级：单项统计异常记 warn 并返回 0，不阻断整体响应</li>
 *   <li>比率类指标在分母为 0 时返回 0，避免出现 NaN</li>
 * </ul>
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/app")
public class AppModuleOverviewController {

    /** 生产工单状态：生产中 */
    private static final String WO_IN_PROGRESS = "IN_PROGRESS";
    /** 生产工单状态：已完工 */
    private static final String WO_COMPLETED = "COMPLETED";
    /** 生产工单状态：已关闭（终态） */
    private static final String WO_CLOSED = "CLOSED";
    /** 设备状态：故障 */
    private static final String EQ_STATUS_FAULT = "FAULT";
    /** 点检任务状态：待执行 */
    private static final String PATROL_PENDING = "PENDING";
    /** 点检任务状态：执行中 */
    private static final String PATROL_IN_PROGRESS = "IN_PROGRESS";
    /** 维修状态：已报修 */
    private static final String REPAIR_REPORTED = "REPORTED";
    /** 维修状态：维修中 */
    private static final String REPAIR_REPAIRING = "REPAIRING";
    /** 检验结果：不合格 */
    private static final String QC_UNQUALIFIED = "UNQUALIFIED";
    /** 检验结果：待检验 */
    private static final String QC_PENDING = "PENDING";
    /** 百分比基数 */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** 生产计划 Mapper */
    private final PrdProductionPlanMapper prdProductionPlanMapper;
    /** 生产工单 Mapper */
    private final PrdWorkOrderMapper prdWorkOrderMapper;
    /** 设备台账 Mapper */
    private final EqEquipmentMapper eqEquipmentMapper;
    /** 设备点检任务 Mapper */
    private final EqPatrolTaskMapper eqPatrolTaskMapper;
    /** 设备维修单 Mapper */
    private final EqRepairMapper eqRepairMapper;
    /** 来料检验 Mapper */
    private final QcIqcMapper qcIqcMapper;
    /** 过程检验 Mapper */
    private final QcIpqcMapper qcIpqcMapper;
    /** 出货检验 Mapper */
    private final QcOqcMapper qcOqcMapper;

    // ==================== 生产管理 ====================

    /**
     * 生产管理入口概览
     * <p>指标口径：
     * <ul>
     *   <li>planCount：生产计划总数（未删除）</li>
     *   <li>workingOrderCount：工单状态 = IN_PROGRESS 的数量</li>
     *   <li>exceptionCount：逾期未完工工单（计划结束日期早于当前时间，且状态非 已完工/已关闭）</li>
     *   <li>completionRate：未关闭工单的 Σ已完成数量 / Σ计划数量（百分比，保留 2 位小数）</li>
     * </ul>
     *
     * @return 生产概览数据
     */
    @SaCheckLogin
    @GetMapping("/prd/overview")
    public R<Map<String, Object>> prdOverview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("planCount", safeCount(() -> prdProductionPlanMapper.selectCount(null)));
        data.put("workingOrderCount", safeCount(() -> prdWorkOrderMapper.selectCount(
            new LambdaQueryWrapper<PrdWorkOrder>().eq(PrdWorkOrder::getWoStatus, WO_IN_PROGRESS))));
        data.put("exceptionCount", safeCount(() -> prdWorkOrderMapper.selectCount(
            new LambdaQueryWrapper<PrdWorkOrder>()
                .lt(PrdWorkOrder::getPlanEndDate, new Date())
                .notIn(PrdWorkOrder::getWoStatus, WO_COMPLETED, WO_CLOSED))));
        data.put("completionRate", completionRate());
        return R.ok(data);
    }

    /**
     * 未关闭工单的完工进度：Σ已完成数量 / Σ计划数量。
     * <p>只取数量列，避免整表字段加载；异常时降级返回 0。
     */
    private BigDecimal completionRate() {
        try {
            List<PrdWorkOrder> orders = prdWorkOrderMapper.selectList(new LambdaQueryWrapper<PrdWorkOrder>()
                .select(PrdWorkOrder::getPlannedQty, PrdWorkOrder::getCompletedQty)
                .ne(PrdWorkOrder::getWoStatus, WO_CLOSED));
            BigDecimal planned = BigDecimal.ZERO;
            BigDecimal completed = BigDecimal.ZERO;
            for (PrdWorkOrder order : orders) {
                planned = planned.add(order.getPlannedQty() == null ? BigDecimal.ZERO : order.getPlannedQty());
                completed = completed.add(order.getCompletedQty() == null ? BigDecimal.ZERO : order.getCompletedQty());
            }
            if (planned.compareTo(BigDecimal.ZERO) <= 0) {
                return BigDecimal.ZERO;
            }
            return completed.multiply(HUNDRED).divide(planned, 2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("[AppOverview] 生产完工率计算失败, 已降级为 0: {}", e.getMessage(), e);
            return BigDecimal.ZERO;
        }
    }

    // ==================== 设备管理（EAM） ====================

    /**
     * 设备管理入口概览
     * <p>指标口径：
     * <ul>
     *   <li>inspectionTodoCount：待处理点检任务（状态 PENDING / IN_PROGRESS）</li>
     *   <li>repairCount：进行中维修单（状态 REPORTED / REPAIRING）</li>
     *   <li>faultEquipmentCount：故障中设备数（状态 FAULT）</li>
     *   <li>downtimeHours：本月维修单停机时长合计（直接汇总 eq_repair.downtime_hours，单位小时）</li>
     * </ul>
     *
     * @return 设备概览数据
     */
    @SaCheckLogin
    @GetMapping("/eam/overview")
    public R<Map<String, Object>> eamOverview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("inspectionTodoCount", safeCount(() -> eqPatrolTaskMapper.selectCount(
            new LambdaQueryWrapper<EqPatrolTask>()
                .in(EqPatrolTask::getStatus, PATROL_PENDING, PATROL_IN_PROGRESS))));
        data.put("repairCount", safeCount(() -> eqRepairMapper.selectCount(
            new LambdaQueryWrapper<EqRepair>()
                .in(EqRepair::getStatus, REPAIR_REPORTED, REPAIR_REPAIRING))));
        data.put("faultEquipmentCount", safeCount(() -> eqEquipmentMapper.selectCount(
            new LambdaQueryWrapper<EqEquipment>().eq(EqEquipment::getStatus, EQ_STATUS_FAULT))));
        data.put("downtimeHours", monthDowntimeHours());
        return R.ok(data);
    }

    /**
     * 本月停机时长合计（小时）。
     * <p>按本月报修的维修单汇总，避免全表扫描；异常时降级返回 0。
     */
    private BigDecimal monthDowntimeHours() {
        try {
            Date monthStart = Date.from(LocalDate.now().withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault()).toInstant());
            List<EqRepair> repairs = eqRepairMapper.selectList(new LambdaQueryWrapper<EqRepair>()
                .select(EqRepair::getDowntimeHours)
                .ge(EqRepair::getCreateTime, monthStart));
            BigDecimal total = BigDecimal.ZERO;
            for (EqRepair repair : repairs) {
                total = total.add(repair.getDowntimeHours() == null ? BigDecimal.ZERO : repair.getDowntimeHours());
            }
            return total.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("[AppOverview] 本月停机时长计算失败, 已降级为 0: {}", e.getMessage(), e);
            return BigDecimal.ZERO;
        }
    }

    // ==================== 质量管理（QM） ====================

    /**
     * 质量管理入口概览
     * <p>指标口径（IQC + IPQC + OQC 三表汇总）：
     * <ul>
     *   <li>inspectionCount：检验单总数</li>
     *   <li>pendingCount：待检验单数（检验结果 = PENDING）</li>
     *   <li>ngCount：不合格单数（检验结果 = UNQUALIFIED）</li>
     *   <li>passRate：合格率 = (已检 - 不合格) / 已检（百分比，已检 = 总数 - 待检验）</li>
     * </ul>
     * <p>说明：缺口清单中建议的 traceCount（追溯次数）后端无对应计数存储，
     * 需先建设追溯查询埋点，暂不返回，避免给出失真数据。
     *
     * @return 质量概览数据
     */
    @SaCheckLogin
    @GetMapping("/qm/overview")
    public R<Map<String, Object>> qmOverview() {
        long total = safeCount(() -> qcIqcMapper.selectCount(null))
            + safeCount(() -> qcIpqcMapper.selectCount(null))
            + safeCount(() -> qcOqcMapper.selectCount(null));

        long pending = safeCount(() -> qcIqcMapper.selectCount(
            new LambdaQueryWrapper<QcIqc>().eq(QcIqc::getInspectResult, QC_PENDING)))
            + safeCount(() -> qcIpqcMapper.selectCount(
            new LambdaQueryWrapper<QcIpqc>().eq(QcIpqc::getInspectResult, QC_PENDING)))
            + safeCount(() -> qcOqcMapper.selectCount(
            new LambdaQueryWrapper<QcOqc>().eq(QcOqc::getInspectResult, QC_PENDING)));

        long unqualified = safeCount(() -> qcIqcMapper.selectCount(
            new LambdaQueryWrapper<QcIqc>().eq(QcIqc::getInspectResult, QC_UNQUALIFIED)))
            + safeCount(() -> qcIpqcMapper.selectCount(
            new LambdaQueryWrapper<QcIpqc>().eq(QcIpqc::getInspectResult, QC_UNQUALIFIED)))
            + safeCount(() -> qcOqcMapper.selectCount(
            new LambdaQueryWrapper<QcOqc>().eq(QcOqc::getInspectResult, QC_UNQUALIFIED)));

        long inspected = total - pending;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("inspectionCount", total);
        data.put("pendingCount", pending);
        data.put("ngCount", unqualified);
        data.put("passRate", inspected <= 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(inspected - unqualified).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(inspected), 2, RoundingMode.HALF_UP));
        return R.ok(data);
    }

    // ==================== 报表中心 ====================

    /**
     * 报表中心菜单
     * <p>返回 App 已实现报表页面的静态菜单（路由均已在 pages.json 注册）。
     *
     * @return 报表菜单列表
     */
    @SaCheckLogin
    @GetMapping("/report/menus")
    public R<List<Map<String, Object>>> reportMenus() {
        List<Map<String, Object>> menus = new ArrayList<>();
        menus.add(menu("prd_report_chart", "生产报表", "生产", "bars", "/pages/erp/prd/report/chart"));
        menus.add(menu("prd_report_list", "报工记录", "生产", "compose", "/pages/erp/prd/report/list"));
        menus.add(menu("spc_chart_list", "SPC 控制图", "质量", "pulse", "/pages/mes/spc/chart/list"));
        return R.ok(menus);
    }

    /**
     * 组装单个报表菜单项。
     *
     * @param id       菜单标识
     * @param name     名称
     * @param category 分类（用于分组展示）
     * @param icon     图标标识
     * @param route    前端路由
     */
    private Map<String, Object> menu(String id, String name, String category, String icon, String route) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("name", name);
        item.put("category", category);
        item.put("icon", icon);
        item.put("route", route);
        return item;
    }

    // ==================== 内部辅助 ====================

    /**
     * 统计降级包装：单项异常返回 0，避免影响整体概览响应。
     *
     * @param supplier 统计逻辑（返回 count 结果）
     */
    private long safeCount(Supplier<Long> supplier) {
        try {
            Long value = supplier.get();
            return value == null ? 0L : value;
        } catch (Exception e) {
            log.warn("[AppOverview] 统计失败, 已降级为 0: {}", e.getMessage(), e);
            return 0L;
        }
    }
}
