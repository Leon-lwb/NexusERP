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

/**
 * 属于MES 设备管理模块，提供巡检相关的 REST 接口。
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mes/eq/patrol")
public class EqPatrolController extends BaseController {

    private final IEqPatrolService patrolService;

    // ==================== 路线 ====================

    /**
     * 查询路线列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    @SaCheckPermission("mes:eq:patrol:route:list")
    @GetMapping("/route/list")
    public TableDataInfo<EqPatrolRouteVo> routeList(EqPatrolRouteBo bo, PageQuery pageQuery) {
        return patrolService.selectRoutePage(bo, pageQuery);
    }

    /**
     * 获取路线详情
     *
     * @param routeId 巡检路线ID
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:patrol:route:query")
    @GetMapping("/route/{routeId}")
    public R<EqPatrolRouteVo> routeInfo(@PathVariable Long routeId) {
        return R.ok(patrolService.selectRouteById(routeId));
    }

    /**
     * 查询路线列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:patrol:route:query")
    @GetMapping("/route/listAll")
    public R<List<EqPatrolRouteVo>> routeListAll(EqPatrolRouteBo bo) {
        return R.ok(patrolService.selectRouteList(bo));
    }

    /**
     * 新增路线
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:patrol:route:add")
    @Log(title = "巡检路线", businessType = BusinessType.INSERT)
    @PostMapping("/route")
    public R<EqPatrolRouteVo> routeAdd(@Validated @RequestBody EqPatrolRouteBo bo) {
        return R.ok(patrolService.insertRouteByBo(bo));
    }

    /**
     * 更新路线
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:patrol:route:edit")
    @Log(title = "巡检路线", businessType = BusinessType.UPDATE)
    @PutMapping("/route")
    public R<Void> routeEdit(@Validated @RequestBody EqPatrolRouteBo bo) {
        return toAjax(patrolService.updateRouteByBo(bo));
    }

    /**
     * 删除路线
     *
     * @param ids ID数组
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:patrol:route:remove")
    @Log(title = "巡检路线", businessType = BusinessType.DELETE)
    @DeleteMapping("/route/{ids}")
    public R<Void> routeRemove(@PathVariable Long[] ids) {
        return toAjax(patrolService.deleteRouteByIds(ids));
    }

    // ==================== 任务 ====================

    /**
     * 查询任务列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    @SaCheckPermission("mes:eq:patrol:task:list")
    @GetMapping("/task/list")
    public TableDataInfo<EqPatrolTaskVo> taskList(EqPatrolTaskBo bo, PageQuery pageQuery) {
        return patrolService.selectTaskPage(bo, pageQuery);
    }

    /**
     * 获取任务详情
     *
     * @param taskId 巡检任务ID
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:patrol:task:query")
    @GetMapping("/task/{taskId}")
    public R<EqPatrolTaskVo> taskInfo(@PathVariable Long taskId) {
        return R.ok(patrolService.selectTaskById(taskId));
    }

    /**
     * 新增任务
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:patrol:task:add")
    @Log(title = "巡检任务", businessType = BusinessType.INSERT)
    @PostMapping("/task")
    public R<EqPatrolTaskVo> taskAdd(@Validated @RequestBody EqPatrolTaskBo bo) {
        return R.ok(patrolService.insertTaskByBo(bo));
    }

    /**
     * 更新任务
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:patrol:task:edit")
    @Log(title = "巡检任务", businessType = BusinessType.UPDATE)
    @PutMapping("/task")
    public R<Void> taskEdit(@Validated @RequestBody EqPatrolTaskBo bo) {
        return toAjax(patrolService.updateTaskByBo(bo));
    }

    /**
     * 删除任务
     *
     * @param ids ID数组
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:patrol:task:remove")
    @Log(title = "巡检任务", businessType = BusinessType.DELETE)
    @DeleteMapping("/task/{ids}")
    public R<Void> taskRemove(@PathVariable Long[] ids) {
        return toAjax(patrolService.deleteTaskByIds(ids));
    }

    /**
     * 完成任务
     *
     * @param taskId 巡检任务ID
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:patrol:task:execute")
    @Log(title = "巡检任务完成", businessType = BusinessType.UPDATE)
    @PutMapping("/task/complete/{taskId}")
    public R<Void> taskComplete(@PathVariable Long taskId) {
        return toAjax(patrolService.completeTask(taskId));
    }

    // ==================== 记录 ====================

    /**
     * 查询记录列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    @SaCheckPermission("mes:eq:patrol:record:list")
    @GetMapping("/record/list")
    public TableDataInfo<EqPatrolRecordVo> recordList(EqPatrolRecordBo bo, PageQuery pageQuery) {
        return patrolService.selectRecordPage(bo, pageQuery);
    }

    /**
     * 获取记录详情
     *
     * @param recordId 数据记录ID
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:patrol:record:query")
    @GetMapping("/record/{recordId}")
    public R<EqPatrolRecordVo> recordInfo(@PathVariable Long recordId) {
        return R.ok(patrolService.selectRecordById(recordId));
    }

    /**
     * 按任务查询记录
     *
     * @param taskId 巡检任务ID
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:patrol:record:query")
    @GetMapping("/record/task/{taskId}")
    public R<List<EqPatrolRecordVo>> recordByTask(@PathVariable Long taskId) {
        return R.ok(patrolService.selectRecordByTask(taskId));
    }

    /**
     * 新增记录
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:patrol:task:execute")
    @Log(title = "巡检记录", businessType = BusinessType.INSERT)
    @PostMapping("/record")
    public R<EqPatrolRecordVo> recordAdd(@RequestBody EqPatrolRecordBo bo) {
        return R.ok(patrolService.insertRecordByBo(bo));
    }

    /**
     * 批量处理记录
     *
     * @param bos 参数 bos
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:patrol:task:execute")
    @Log(title = "批量巡检", businessType = BusinessType.INSERT)
    @PostMapping("/record/batch")
    public R<List<EqPatrolRecordVo>> recordBatch(@RequestBody List<EqPatrolRecordBo> bos) {
        return R.ok(patrolService.batchSubmitRecords(bos));
    }

    /**
     * 删除记录
     *
     * @param ids ID数组
     * @return 操作结果
     */
    @SaCheckPermission("mes:eq:patrol:record:remove")
    @Log(title = "巡检记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/record/{ids}")
    public R<Void> recordRemove(@PathVariable Long[] ids) {
        return toAjax(patrolService.deleteRecordByIds(ids));
    }

    /**
     * 导出记录
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param response HTTP 响应对象
     */
    @SaCheckPermission("mes:eq:patrol:record:list")
    @PostMapping("/record/export")
    @Log(title = "巡检记录", businessType = BusinessType.EXPORT)
    public void recordExport(EqPatrolRecordBo bo, HttpServletResponse response) {
        // 分批拉取全量（PageQuery 全局上限 500 会截断单次大分页，导出走专用聚合方法）
        List<EqPatrolRecordVo> list = patrolService.selectRecordExportList(bo);
        ExcelUtil.exportExcel(list, "巡检记录", EqPatrolRecordVo.class, response);
    }
}
