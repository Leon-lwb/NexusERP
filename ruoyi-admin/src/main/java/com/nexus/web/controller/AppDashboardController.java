package com.nexus.web.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.nexus.common.core.domain.R;
import com.nexus.common.flowable.core.domain.ProcessQuery;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.dashboard.domain.vo.ErpDashboardStatsVo;
import com.nexus.erp.dashboard.service.IErpDashboardService;
import com.nexus.im.domain.vo.ImConversationVo;
import com.nexus.im.service.impl.support.ImConversationService;
import com.nexus.workflow.domain.vo.WfTaskVo;
import com.nexus.workflow.service.IWfProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * App 首页驾驶舱 Controller
 * <p>为移动端首页/工作台提供跨模块的聚合只读接口，补齐原《接口状态与后端缺口清单》§5.1
 * 中缺失的 {@code /app/dashboard/*} 与 {@code /app/workbench/reminders}。</p>
 * <p>设计约定：
 * <ul>
 *   <li>只聚合、不新增业务表；销售/库存等统计直接复用 {@link IErpDashboardService}，避免双份口径</li>
 *   <li>待办数复用 Flowable 的待办分页接口（只取 total），与审批中心口径一致</li>
 *   <li>未读数复用 IM 会话列表的未读汇总，与消息 Tab 角标口径一致</li>
 *   <li>任一子统计失败不阻断整体响应（降级为 0 并记录告警），保证首页首屏可用</li>
 * </ul>
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/app")
public class AppDashboardController {

    /** ERP 首页统计服务（销售/主数据/库存预警/销售趋势）。 */
    private final IErpDashboardService erpDashboardService;
    /** 工作流流程服务，用于取待办任务总数。 */
    private final IWfProcessService wfProcessService;
    /** IM 会话服务，用于汇总各会话未读数。 */
    private final ImConversationService imConversationService;

    /**
     * 首页今日总览。
     * <p>销售金额/订单数/客户数/物料数/库存预警数 + 近 12 个月销售趋势 + 近期订单与预警明细。
     *
     * @return 总览数据
     */
    @SaCheckLogin
    @GetMapping("/dashboard/overview")
    public R<Map<String, Object>> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        ErpDashboardStatsVo stats = safeStats();
        if (stats == null) {
            // ERP 统计不可用时返回空骨架，避免前端首页整页报错
            data.put("salesAmount", 0);
            data.put("salesCount", 0);
            data.put("openOrderCount", 0);
            data.put("clientCount", 0);
            data.put("materialCount", 0);
            data.put("stockAlertCount", 0);
            data.put("salesTrend", List.of());
            data.put("recentOrders", List.of());
            data.put("stockAlerts", List.of());
            return R.ok(data);
        }
        data.put("salesAmount", stats.getMonthSalesAmount());
        data.put("salesCount", stats.getMonthSalesCount());
        data.put("openOrderCount", stats.getOpenSalesOrderCount());
        data.put("clientCount", stats.getClientCount());
        data.put("materialCount", stats.getMaterialCount());
        data.put("stockAlertCount", stats.getStockAlertCount());
        data.put("salesTrend", stats.getSalesTrend());
        data.put("recentOrders", stats.getRecentOrders());
        data.put("stockAlerts", stats.getStockAlerts());
        return R.ok(data);
    }

    /**
     * 首页「我的数据」。
     * <p>approvalCount=待办审批数；unreadMessageCount=IM 未读消息数；stockAlertCount=未处理库存预警数。
     * <p>说明：{@code todayPunchStatus}（今日打卡）与 {@code expenseMonthAmount}（本月报销）
     * 暂不返回——前者依赖考勤模块的登录用户-工人绑定修复（见缺口清单 §6），
     * 后者后端尚无费用报销模块，待补齐后再纳入，避免返回失真数据。
     *
     * @return 我的数据
     */
    @SaCheckLogin
    @GetMapping("/dashboard/my-workbench")
    public R<Map<String, Object>> myWorkbench() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("approvalCount", countTodoApproval());
        data.put("unreadMessageCount", countUnreadMessage());
        ErpDashboardStatsVo stats = safeStats();
        data.put("stockAlertCount", stats == null || stats.getStockAlertCount() == null ? 0L : stats.getStockAlertCount());
        return R.ok(data);
    }

    /**
     * 工作台待办提醒条。
     * <p>仅使用低成本的计数来源（待办审批 / 未读消息），避免为提醒条触发首页级别的重统计。
     *
     * @return 提醒列表，可能为空数组
     */
    @SaCheckLogin
    @GetMapping("/workbench/reminders")
    public R<List<Map<String, Object>>> reminders() {
        List<Map<String, Object>> list = new ArrayList<>();
        long todo = countTodoApproval();
        if (todo > 0) {
            list.add(reminder("workflow_todo", "待办审批", todo, "warning", "/pages/wf/todo/list"));
        }
        long unread = countUnreadMessage();
        if (unread > 0) {
            list.add(reminder("im_unread", "未读消息", unread, "info", "/pages/im/conversation/index"));
        }
        return R.ok(list);
    }

    // ==================== 内部辅助 ====================

    /**
     * 组装单条提醒。
     *
     * @param type  提醒类型标识
     * @param text  展示文案
     * @param count 数量
     * @param level 级别（info/warning/danger，前端据此选色）
     * @param route 点击跳转的前端路由
     */
    private Map<String, Object> reminder(String type, String text, long count, String level, String route) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", type);
        item.put("type", type);
        item.put("content", text);
        item.put("count", count);
        item.put("level", level);
        item.put("route", route);
        return item;
    }

    /**
     * 取 ERP 首页统计，异常时降级返回 null（不阻断整体响应）。
     */
    private ErpDashboardStatsVo safeStats() {
        try {
            return erpDashboardService.getStats();
        } catch (Exception e) {
            log.warn("[AppDashboard] ERP 统计查询失败, 已降级: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 待办审批数（与审批中心「待办」页签同口径）。
     */
    private long countTodoApproval() {
        try {
            PageQuery pageQuery = new PageQuery(1, 1);
            TableDataInfo<WfTaskVo> page = wfProcessService.selectPageTodoProcessList(new ProcessQuery(), pageQuery);
            return page == null ? 0L : page.getTotal();
        } catch (Exception e) {
            log.warn("[AppDashboard] 待办审批数查询失败, 已降级为 0: {}", e.getMessage(), e);
            return 0L;
        }
    }

    /**
     * 未读消息数（与消息 Tab 角标同口径：汇总各会话未读数）。
     */
    private long countUnreadMessage() {
        try {
            List<ImConversationVo> conversations = imConversationService.listConversations();
            if (conversations == null || conversations.isEmpty()) {
                return 0L;
            }
            return conversations.stream()
                .map(ImConversationVo::getUnreadCount)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        } catch (Exception e) {
            log.warn("[AppDashboard] 未读消息数查询失败, 已降级为 0: {}", e.getMessage(), e);
            return 0L;
        }
    }
}
