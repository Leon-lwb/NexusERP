package com.nexus.core.prd.controller;

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
import com.nexus.core.prd.domain.PrdWorkReport;
import com.nexus.core.prd.service.IPrdWorkReportService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 生产报工Controller
 *
 * @author nexus
 * @date 2026-03-27
 */
@Api(tags = "生产报工Controller")
@RestController
@RequestMapping("/prd/report")
public class PrdWorkReportController extends BaseController
{
    @Autowired
    private IPrdWorkReportService prdWorkReportService;

    /**
     * 查询生产报工列表
     */
    @ApiOperation("查询生产报工列表")
    @PreAuthorize("@ss.hasPermi('prd:report:list')")
    @GetMapping("/list")
    public TableDataInfo list(PrdWorkReport prdWorkReport)
    {
        startPage();
        List<PrdWorkReport> list = prdWorkReportService.selectPrdWorkReportList(prdWorkReport);
        return getDataTable(list);
    }

    /**
     * 导出生产报工列表
     */
    @ApiOperation("导出生产报工列表")
    @PreAuthorize("@ss.hasPermi('prd:report:export')")
    @Log(title = "生产报工", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, PrdWorkReport prdWorkReport)
    {
        List<PrdWorkReport> list = prdWorkReportService.selectPrdWorkReportList(prdWorkReport);
        ExcelUtil<PrdWorkReport> util = new ExcelUtil<PrdWorkReport>(PrdWorkReport.class);
        util.exportExcel(response, list, "生产报工数据");
    }

    /**
     * 获取生产报工详细信息
     */
    @ApiOperation("获取生产报工列表")
    @PreAuthorize("@ss.hasPermi('prd:report:query')")
    @GetMapping(value = "/{reportId}")
    public AjaxResult getInfo(@PathVariable("reportId") Long reportId)
    {
        return success(prdWorkReportService.selectPrdWorkReportByReportId(reportId));
    }

    /**
     * 新增生产报工
     */
    @ApiOperation("新增生产报工详细信息")
    @PreAuthorize("@ss.hasPermi('prd:report:add')")
    @Log(title = "生产报工", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody PrdWorkReport prdWorkReport)
    {
        return toAjax(prdWorkReportService.insertPrdWorkReport(prdWorkReport));
    }

    /**
     * 修改生产报工
     */
    @ApiOperation("修改生产报工详细信息")
    @PreAuthorize("@ss.hasPermi('prd:report:edit')")
    @Log(title = "生产报工", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody PrdWorkReport prdWorkReport)
    {
        return toAjax(prdWorkReportService.updatePrdWorkReport(prdWorkReport));
    }

    /**
     * 删除生产报工
     */
    @ApiOperation("删除生产报工信息")
    @PreAuthorize("@ss.hasPermi('prd:report:remove')")
    @Log(title = "生产报工", businessType = BusinessType.DELETE)
    @DeleteMapping("/{reportIds}")
    public AjaxResult remove(@PathVariable Long[] reportIds)
    {
        return toAjax(prdWorkReportService.deletePrdWorkReportByReportIds(reportIds));
    }
}
