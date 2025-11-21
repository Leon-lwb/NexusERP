package com.nexus.core.bd.controller;

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
import com.nexus.core.bd.domain.BdWorkstation;
import com.nexus.core.bd.service.IBdWorkstationService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 工作站Controller
 *
 * @author nexus
 * @date 2025-11-19
 */
@Api(tags = "工作站Controller")
@RestController
@RequestMapping("/bd/workstation")
public class BdWorkstationController extends BaseController
{
    @Autowired
    private IBdWorkstationService bdWorkstationService;

    /**
     * 查询工作站列表
     */
    @ApiOperation("查询工作站列表")
    @PreAuthorize("@ss.hasPermi('bd:workstation:list')")
    @GetMapping("/list")
    public TableDataInfo list(BdWorkstation bdWorkstation)
    {
        startPage();
        List<BdWorkstation> list = bdWorkstationService.selectBdWorkstationList(bdWorkstation);
        return getDataTable(list);
    }

    /**
     * 导出工作站列表
     */
    @ApiOperation("导出工作站列表")
    @PreAuthorize("@ss.hasPermi('bd:workstation:export')")
    @Log(title = "工作站", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, BdWorkstation bdWorkstation)
    {
        List<BdWorkstation> list = bdWorkstationService.selectBdWorkstationList(bdWorkstation);
        ExcelUtil<BdWorkstation> util = new ExcelUtil<BdWorkstation>(BdWorkstation.class);
        util.exportExcel(response, list, "工作站数据");
    }

    /**
     * 获取工作站详细信息
     */
    @ApiOperation("获取工作站列表")
    @PreAuthorize("@ss.hasPermi('bd:workstation:query')")
    @GetMapping(value = "/{workstationId}")
    public AjaxResult getInfo(@PathVariable("workstationId") Long workstationId)
    {
        return success(bdWorkstationService.selectBdWorkstationByWorkstationId(workstationId));
    }

    /**
     * 新增工作站
     */
    @ApiOperation("新增工作站详细信息")
    @PreAuthorize("@ss.hasPermi('bd:workstation:add')")
    @Log(title = "工作站", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody BdWorkstation bdWorkstation)
    {
        return toAjax(bdWorkstationService.insertBdWorkstation(bdWorkstation));
    }

    /**
     * 修改工作站
     */
    @ApiOperation("修改工作站详细信息")
    @PreAuthorize("@ss.hasPermi('bd:workstation:edit')")
    @Log(title = "工作站", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BdWorkstation bdWorkstation)
    {
        return toAjax(bdWorkstationService.updateBdWorkstation(bdWorkstation));
    }

    /**
     * 删除工作站
     */
    @ApiOperation("删除工作站信息")
    @PreAuthorize("@ss.hasPermi('bd:workstation:remove')")
    @Log(title = "工作站", businessType = BusinessType.DELETE)
    @DeleteMapping("/{workstationIds}")
    public AjaxResult remove(@PathVariable Long[] workstationIds)
    {
        return toAjax(bdWorkstationService.deleteBdWorkstationByWorkstationIds(workstationIds));
    }
}
