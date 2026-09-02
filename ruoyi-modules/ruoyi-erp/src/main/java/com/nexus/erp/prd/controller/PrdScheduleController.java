package com.nexus.erp.prd.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.erp.prd.domain.bo.PrdSchedulePlanBo;
import com.nexus.erp.prd.domain.vo.PrdSchedulePlanVo;
import com.nexus.erp.prd.domain.vo.PrdScheduleTaskVo;
import com.nexus.erp.prd.domain.vo.ScheduleGanttVo;
import com.nexus.erp.prd.service.IPrdScheduleService;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * APS 智能排产 Controller
 * <p>
 * 提供排程方案查询、执行排程、甘特图数据、发布与删除接口。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/erp/prd/schedule")
public class PrdScheduleController extends BaseController {

    private final IPrdScheduleService scheduleService;

    /**
     * 分页查询排程方案列表
     * <p>权限标识：{@code prd:schedule:list}</p>
     */
    @SaCheckPermission("prd:schedule:list")
    @GetMapping("/list")
    public TableDataInfo<PrdSchedulePlanVo> list(PrdSchedulePlanBo bo, PageQuery pageQuery) {
        return scheduleService.selectPageList(bo, pageQuery);
    }

    /**
     * 查询方案下全部工序任务
     * <p>权限标识：{@code prd:schedule:query}</p>
     *
     * @param planId 方案ID
     * @return 工序任务列表
     */
    @SaCheckPermission("prd:schedule:query")
    @GetMapping("/task/{planId}")
    public R<List<PrdScheduleTaskVo>> tasks(@NotNull(message = "方案ID不能为空") @PathVariable Long planId) {
        return R.ok(scheduleService.selectTasksByPlanId(planId));
    }

    /**
     * 查询方案甘特图聚合数据
     * <p>权限标识：{@code prd:schedule:query}</p>
     *
     * @param planId 方案ID
     * @return 甘特图数据(工单行+工序条+资源利用率)
     */
    @SaCheckPermission("prd:schedule:query")
    @GetMapping("/gantt/{planId}")
    public R<ScheduleGanttVo> gantt(@NotNull(message = "方案ID不能为空") @PathVariable Long planId) {
        return R.ok(scheduleService.selectGantt(planId));
    }

    /**
     * 执行排程
     * <p>权限标识：{@code prd:schedule:run}</p>
     * <p>日志记录：title="智能排产-执行"，businessType=INSERT</p>
     *
     * @param bo 排程参数(起始日/跨度/策略/车间过滤)
     * @return 生成的排程方案
     */
    @SaCheckPermission("prd:schedule:run")
    @Log(title = "智能排产-执行", businessType = BusinessType.INSERT)
    @PostMapping("/run")
    public R<PrdSchedulePlanVo> run(@Validated(PrdSchedulePlanBo.Run.class) @RequestBody PrdSchedulePlanBo bo) {
        return R.ok(scheduleService.runSchedule(bo));
    }

    /**
     * 发布排程方案(回写工单计划起止日期)
     * <p>权限标识：{@code prd:schedule:publish}</p>
     * <p>日志记录：title="智能排产-发布"，businessType=UPDATE</p>
     *
     * @param planId 方案ID
     * @return 操作结果
     */
    @SaCheckPermission("prd:schedule:publish")
    @Log(title = "智能排产-发布", businessType = BusinessType.UPDATE)
    @PutMapping("/publish/{planId}")
    public R<Void> publish(@NotNull(message = "方案ID不能为空") @PathVariable Long planId) {
        return toAjax(scheduleService.publish(planId));
    }

    /**
     * 批量删除排程方案(级联删除任务)
     * <p>权限标识：{@code prd:schedule:remove}</p>
     * <p>日志记录：title="智能排产-删除"，businessType=DELETE</p>
     *
     * @param planIds 方案ID数组
     * @return 操作结果
     */
    @SaCheckPermission("prd:schedule:remove")
    @Log(title = "智能排产-删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{planIds}")
    public R<Void> remove(@NotEmpty(message = "方案ID不能为空") @PathVariable Long[] planIds) {
        return toAjax(scheduleService.deleteByIds(planIds));
    }
}
