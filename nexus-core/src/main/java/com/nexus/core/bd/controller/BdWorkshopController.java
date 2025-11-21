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
import com.nexus.core.bd.domain.BdWorkshop;
import com.nexus.core.bd.service.IBdWorkshopService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 车间设置Controller
 *
 * @author nexus
 * @date 2025-11-18
 */
@Api(tags = "车间设置Controller")
@RestController
@RequestMapping("/bd/workshop")
public class BdWorkshopController extends BaseController
{
    @Autowired
    private IBdWorkshopService bdWorkshopService;

    /**
     * 查询车间设置列表
     */
    @ApiOperation("查询车间设置列表")
    @PreAuthorize("@ss.hasPermi('bd:workshop:list')")
    @GetMapping("/list")
    public TableDataInfo list(BdWorkshop bdWorkshop)
    {
        startPage();
        List<BdWorkshop> list = bdWorkshopService.selectBdWorkshopList(bdWorkshop);
        return getDataTable(list);
    }

    /**
     * 导出车间设置列表
     */
    @ApiOperation("导出车间设置列表")
    @PreAuthorize("@ss.hasPermi('bd:workshop:export')")
    @Log(title = "车间设置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, BdWorkshop bdWorkshop)
    {
        List<BdWorkshop> list = bdWorkshopService.selectBdWorkshopList(bdWorkshop);
        ExcelUtil<BdWorkshop> util = new ExcelUtil<BdWorkshop>(BdWorkshop.class);
        util.exportExcel(response, list, "车间设置数据");
    }

    /**
     * 获取车间设置详细信息
     */
    @ApiOperation("获取车间设置列表")
    @PreAuthorize("@ss.hasPermi('bd:workshop:query')")
    @GetMapping(value = "/{workshopId}")
    public AjaxResult getInfo(@PathVariable("workshopId") Long workshopId)
    {
        return success(bdWorkshopService.selectBdWorkshopByWorkshopId(workshopId));
    }

    /**
     * 新增车间设置
     */
    @ApiOperation("新增车间设置详细信息")
    @PreAuthorize("@ss.hasPermi('bd:workshop:add')")
    @Log(title = "车间设置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody BdWorkshop bdWorkshop)
    {
        return toAjax(bdWorkshopService.insertBdWorkshop(bdWorkshop));
    }

    /**
     * 修改车间设置
     */
    @ApiOperation("修改车间设置详细信息")
    @PreAuthorize("@ss.hasPermi('bd:workshop:edit')")
    @Log(title = "车间设置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody BdWorkshop bdWorkshop)
    {
        return toAjax(bdWorkshopService.updateBdWorkshop(bdWorkshop));
    }

    /**
     * 删除车间设置
     */
    @ApiOperation("删除车间设置信息")
    @PreAuthorize("@ss.hasPermi('bd:workshop:remove')")
    @Log(title = "车间设置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{workshopIds}")
    public AjaxResult remove(@PathVariable Long[] workshopIds)
    {
        return toAjax(bdWorkshopService.deleteBdWorkshopByWorkshopIds(workshopIds));
    }
}
