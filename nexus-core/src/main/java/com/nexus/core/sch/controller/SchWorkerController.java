package com.nexus.core.sch.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.nexus.common.annotation.Log;
import com.nexus.common.core.controller.BaseController;
import com.nexus.common.core.domain.AjaxResult;
import com.nexus.common.enums.BusinessType;
import com.nexus.core.sch.domain.SchWorker;
import com.nexus.core.sch.service.ISchWorkerService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 生产工人档案Controller
 *
 * @author nexus
 * @date 2025-12-19
 */
@Api(tags = "生产工人档案Controller")
@RestController
@RequestMapping("/sch/worker")
public class SchWorkerController extends BaseController
{
    @Autowired
    private ISchWorkerService schWorkerService;

    /**
     * 查询生产工人档案列表
     */
    @ApiOperation("查询生产工人档案列表")
    @PreAuthorize("@ss.hasPermi('sch:worker:list')")
    @GetMapping("/list")
    public TableDataInfo list(SchWorker schWorker)
    {
        startPage();
        List<SchWorker> list = schWorkerService.selectSchWorkerList(schWorker);
        return getDataTable(list);
    }

    /**
     * 导出生产工人档案列表
     */
    @ApiOperation("导出生产工人档案列表")
    @PreAuthorize("@ss.hasPermi('sch:worker:export')")
    @Log(title = "生产工人档案", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SchWorker schWorker)
    {
        List<SchWorker> list = schWorkerService.selectSchWorkerList(schWorker);
        ExcelUtil<SchWorker> util = new ExcelUtil<SchWorker>(SchWorker.class);
        util.exportExcel(response, list, "生产工人档案数据");
    }

    /**
     * 获取生产工人档案详细信息
     */
    @ApiOperation("获取生产工人档案列表")
    @PreAuthorize("@ss.hasPermi('sch:worker:query')")
    @GetMapping(value = "/{workerId}")
    public AjaxResult getInfo(@PathVariable("workerId") Long workerId)
    {
        return success(schWorkerService.selectSchWorkerByWorkerId(workerId));
    }

    /**
     * 新增生产工人档案
     */
    @ApiOperation("新增生产工人档案详细信息")
    @PreAuthorize("@ss.hasPermi('sch:worker:add')")
    @Log(title = "生产工人档案", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SchWorker schWorker)
    {
        return toAjax(schWorkerService.insertSchWorker(schWorker));
    }

    /**
     * 修改生产工人档案
     */
    @ApiOperation("修改生产工人档案详细信息")
    @PreAuthorize("@ss.hasPermi('sch:worker:edit')")
    @Log(title = "生产工人档案", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SchWorker schWorker)
    {
        return toAjax(schWorkerService.updateSchWorker(schWorker));
    }

    /**
     * 删除生产工人档案
     */
    @ApiOperation("删除生产工人档案信息")
    @PreAuthorize("@ss.hasPermi('sch:worker:remove')")
    @Log(title = "生产工人档案", businessType = BusinessType.DELETE)
    @DeleteMapping("/{workerIds}")
    public AjaxResult remove(@PathVariable Long[] workerIds)
    {
        return toAjax(schWorkerService.deleteSchWorkerByWorkerIds(workerIds));
    }
}
