package com.nexus.mes.eq.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.mes.eq.domain.bo.EqPatrolRecordBo;
import com.nexus.mes.eq.domain.bo.EqPatrolRouteBo;
import com.nexus.mes.eq.domain.bo.EqPatrolTaskBo;
import com.nexus.mes.eq.domain.vo.EqPatrolRecordVo;
import com.nexus.mes.eq.domain.vo.EqPatrolRouteVo;
import com.nexus.mes.eq.domain.vo.EqPatrolTaskVo;
import com.nexus.mes.eq.service.IEqPatrolService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/eq/patrol")
public class EqPatrolController extends BaseController {

    private final IEqPatrolService patrolService;

    // ==================== 路线 ====================

    @SaCheckPermission("mes:eq:patrol:route:list")
    @GetMapping("/route/list")
    public TableDataInfo<EqPatrolRouteVo> routeList(EqPatrolRouteBo bo, PageQuery pageQuery) {
        return patrolService.selectRoutePage(bo, pageQuery);
    }

    @SaCheckPermission("mes:eq:patrol:route:query")
    @GetMapping("/route/{routeId}")
    public R<EqPatrolRouteVo> routeInfo(@PathVariable Long routeId) {
        return R.ok(patrolService.selectRouteById(routeId));
    }

    @SaCheckPermission("mes:eq:patrol:route:query")
    @GetMapping("/route/listAll")
    public R<List<EqPatrolRouteVo>> routeListAll(EqPatrolRouteBo bo) {
        return R.ok(patrolService.selectRouteList(bo));
    }

    @SaCheckPermission("mes:eq:patrol:route:add")
    @Log(title = "巡检路线", businessType = BusinessType.INSERT)
    @PostMapping("/route")
    public R<EqPatrolRouteVo> routeAdd(@Validated @RequestBody EqPatrolRouteBo bo) {
        return R.ok(patrolService.insertRouteByBo(bo));
    }

    @SaCheckPermission("mes:eq:patrol:route:edit")
    @Log(title = "巡检路线", businessType = BusinessType.UPDATE)
    @PutMapping("/route")
    public R<Void> routeEdit(@Validated @RequestBody EqPatrolRouteBo bo) {
        return toAjax(patrolService.updateRouteByBo(bo));
    }

    @SaCheckPermission("mes:eq:patrol:route:remove")
    @Log(title = "巡检路线", businessType = BusinessType.DELETE)
    @DeleteMapping("/route/{ids}")
    public R<Void> routeRemove(@PathVariable Long[] ids) {
        return toAjax(patrolService.deleteRouteByIds(ids));
    }

    // ==================== 任务 ====================

    @SaCheckPermission("mes:eq:patrol:task:list")
    @GetMapping("/task/list")
    public TableDataInfo<EqPatrolTaskVo> taskList(EqPatrolTaskBo bo, PageQuery pageQuery) {
        return patrolService.selectTaskPage(bo, pageQuery);
    }

    @SaCheckPermission("mes:eq:patrol:task:query")
    @GetMapping("/task/{taskId}")
    public R<EqPatrolTaskVo> taskInfo(@PathVariable Long taskId) {
        return R.ok(patrolService.selectTaskById(taskId));
    }

    @SaCheckPermission("mes:eq:patrol:task:add")
    @Log(title = "巡检任务", businessType = BusinessType.INSERT)
    @PostMapping("/task")
    public R<EqPatrolTaskVo> taskAdd(@Validated @RequestBody EqPatrolTaskBo bo) {
        return R.ok(patrolService.insertTaskByBo(bo));
    }

    @SaCheckPermission("mes:eq:patrol:task:edit")
    @Log(title = "巡检任务", businessType = BusinessType.UPDATE)
    @PutMapping("/task")
    public R<Void> taskEdit(@Validated @RequestBody EqPatrolTaskBo bo) {
        return toAjax(patrolService.updateTaskByBo(bo));
    }

    @SaCheckPermission("mes:eq:patrol:task:remove")
    @Log(title = "巡检任务", businessType = BusinessType.DELETE)
    @DeleteMapping("/task/{ids}")
    public R<Void> taskRemove(@PathVariable Long[] ids) {
        return toAjax(patrolService.deleteTaskByIds(ids));
    }

    @SaCheckPermission("mes:eq:patrol:task:execute")
    @Log(title = "巡检任务完成", businessType = BusinessType.UPDATE)
    @PutMapping("/task/complete/{taskId}")
    public R<Void> taskComplete(@PathVariable Long taskId) {
        return toAjax(patrolService.completeTask(taskId));
    }

    // ==================== 记录 ====================

    @SaCheckPermission("mes:eq:patrol:record:list")
    @GetMapping("/record/list")
    public TableDataInfo<EqPatrolRecordVo> recordList(EqPatrolRecordBo bo, PageQuery pageQuery) {
        return patrolService.selectRecordPage(bo, pageQuery);
    }

    @SaCheckPermission("mes:eq:patrol:record:query")
    @GetMapping("/record/{recordId}")
    public R<EqPatrolRecordVo> recordInfo(@PathVariable Long recordId) {
        return R.ok(patrolService.selectRecordById(recordId));
    }

    @SaCheckPermission("mes:eq:patrol:record:query")
    @GetMapping("/record/task/{taskId}")
    public R<List<EqPatrolRecordVo>> recordByTask(@PathVariable Long taskId) {
        return R.ok(patrolService.selectRecordByTask(taskId));
    }

    @SaCheckPermission("mes:eq:patrol:task:execute")
    @Log(title = "巡检记录", businessType = BusinessType.INSERT)
    @PostMapping("/record")
    public R<EqPatrolRecordVo> recordAdd(@RequestBody EqPatrolRecordBo bo) {
        return R.ok(patrolService.insertRecordByBo(bo));
    }

    @SaCheckPermission("mes:eq:patrol:task:execute")
    @Log(title = "批量巡检", businessType = BusinessType.INSERT)
    @PostMapping("/record/batch")
    public R<List<EqPatrolRecordVo>> recordBatch(@RequestBody List<EqPatrolRecordBo> bos) {
        return R.ok(patrolService.batchSubmitRecords(bos));
    }

    @SaCheckPermission("mes:eq:patrol:record:remove")
    @Log(title = "巡检记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/record/{ids}")
    public R<Void> recordRemove(@PathVariable Long[] ids) {
        return toAjax(patrolService.deleteRecordByIds(ids));
    }

    @SaCheckPermission("mes:eq:patrol:record:list")
    @PostMapping("/record/export")
    @Log(title = "巡检记录", businessType = BusinessType.EXPORT)
    public void recordExport(EqPatrolRecordBo bo, HttpServletResponse response) {
        List<EqPatrolRecordVo> list = patrolService.selectRecordPage(bo, new PageQuery(1, 10000)).getRows();
        ExcelUtil.exportExcel(list, "巡检记录", EqPatrolRecordVo.class, response);
    }
}
