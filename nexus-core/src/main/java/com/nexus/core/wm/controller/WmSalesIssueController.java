package com.nexus.core.wm.controller;

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
import com.nexus.core.wm.domain.WmSalesIssue;
import com.nexus.core.wm.service.IWmSalesIssueService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 销售出库Controller
 *
 * @author nexus
 * @date 2026-04-03
 */
@Api(tags = "销售出库Controller")
@RestController
@RequestMapping("/wm/salesIssue")
public class WmSalesIssueController extends BaseController
{
    @Autowired
    private IWmSalesIssueService wmSalesIssueService;

    /**
     * 查询销售出库列表
     */
    @ApiOperation("查询销售出库列表")
    @PreAuthorize("@ss.hasPermi('wm:salesIssue:list')")
    @GetMapping("/list")
    public TableDataInfo list(WmSalesIssue wmSalesIssue)
    {
        startPage();
        List<WmSalesIssue> list = wmSalesIssueService.selectWmSalesIssueList(wmSalesIssue);
        return getDataTable(list);
    }

    /**
     * 导出销售出库列表
     */
    @ApiOperation("导出销售出库列表")
    @PreAuthorize("@ss.hasPermi('wm:salesIssue:export')")
    @Log(title = "销售出库", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, WmSalesIssue wmSalesIssue)
    {
        List<WmSalesIssue> list = wmSalesIssueService.selectWmSalesIssueList(wmSalesIssue);
        ExcelUtil<WmSalesIssue> util = new ExcelUtil<WmSalesIssue>(WmSalesIssue.class);
        util.exportExcel(response, list, "销售出库数据");
    }

    /**
     * 获取销售出库详细信息
     */
    @ApiOperation("获取销售出库列表")
    @PreAuthorize("@ss.hasPermi('wm:salesIssue:query')")
    @GetMapping(value = "/{issueId}")
    public AjaxResult getInfo(@PathVariable("issueId") Long issueId)
    {
        return success(wmSalesIssueService.selectWmSalesIssueByIssueId(issueId));
    }

    /**
     * 新增销售出库
     */
    @ApiOperation("新增销售出库详细信息")
    @PreAuthorize("@ss.hasPermi('wm:salesIssue:add')")
    @Log(title = "销售出库", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody WmSalesIssue wmSalesIssue)
    {
        return toAjax(wmSalesIssueService.insertWmSalesIssue(wmSalesIssue));
    }

    /**
     * 修改销售出库
     */
    @ApiOperation("修改销售出库详细信息")
    @PreAuthorize("@ss.hasPermi('wm:salesIssue:edit')")
    @Log(title = "销售出库", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody WmSalesIssue wmSalesIssue)
    {
        return toAjax(wmSalesIssueService.updateWmSalesIssue(wmSalesIssue));
    }

    /**
     * 删除销售出库
     */
    @ApiOperation("删除销售出库信息")
    @PreAuthorize("@ss.hasPermi('wm:salesIssue:remove')")
    @Log(title = "销售出库", businessType = BusinessType.DELETE)
    @DeleteMapping("/{issueIds}")
    public AjaxResult remove(@PathVariable Long[] issueIds)
    {
        return toAjax(wmSalesIssueService.deleteWmSalesIssueByIssueIds(issueIds));
    }

    /**
     * 确认销售出库
     */
    @ApiOperation("确认销售出库")
    @PreAuthorize("@ss.hasPermi('wm:salesIssue:confirm')")
    @Log(title = "销售出库", businessType = BusinessType.UPDATE)
    @PutMapping("/confirm/{issueId}")
    public AjaxResult confirm(@PathVariable("issueId") Long issueId)
    {
        return toAjax(wmSalesIssueService.confirmSalesIssue(issueId));
    }

    /**
     * 完成销售出库
     */
    @ApiOperation("完成销售出库")
    @PreAuthorize("@ss.hasPermi('wm:salesIssue:complete')")
    @Log(title = "销售出库", businessType = BusinessType.UPDATE)
    @PutMapping("/complete/{issueId}")
    public AjaxResult complete(@PathVariable("issueId") Long issueId)
    {
        return toAjax(wmSalesIssueService.completeSalesIssue(issueId));
    }
}
