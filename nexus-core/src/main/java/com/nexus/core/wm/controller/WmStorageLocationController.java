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
import com.nexus.core.wm.domain.WmStorageLocation;
import com.nexus.core.wm.service.IWmStorageLocationService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 库位Controller
 *
 * @author nexus
 * @date 2025-12-02
 */
@Api(tags = "库位Controller")
@RestController
@RequestMapping("/wm/storagelocation")
public class WmStorageLocationController extends BaseController
{
    @Autowired
    private IWmStorageLocationService wmStorageLocationService;

    /**
     * 查询库位列表
     */
    @ApiOperation("查询库位列表")
    @PreAuthorize("@ss.hasPermi('wm:storagelocation:list')")
    @GetMapping("/list")
    public TableDataInfo list(WmStorageLocation wmStorageLocation)
    {
        startPage();
        List<WmStorageLocation> list = wmStorageLocationService.selectWmStorageLocationList(wmStorageLocation);
        return getDataTable(list);
    }

    /**
     * 导出库位列表
     */
    @ApiOperation("导出库位列表")
    @PreAuthorize("@ss.hasPermi('wm:storagelocation:export')")
    @Log(title = "库位", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, WmStorageLocation wmStorageLocation)
    {
        List<WmStorageLocation> list = wmStorageLocationService.selectWmStorageLocationList(wmStorageLocation);
        ExcelUtil<WmStorageLocation> util = new ExcelUtil<WmStorageLocation>(WmStorageLocation.class);
        util.exportExcel(response, list, "库位数据");
    }

    /**
     * 获取库位详细信息
     */
    @ApiOperation("获取库位列表")
    @PreAuthorize("@ss.hasPermi('wm:storagelocation:query')")
    @GetMapping(value = "/{locationId}")
    public AjaxResult getInfo(@PathVariable("locationId") Long locationId)
    {
        return success(wmStorageLocationService.selectWmStorageLocationByLocationId(locationId));
    }

    /**
     * 新增库位
     */
    @ApiOperation("新增库位详细信息")
    @PreAuthorize("@ss.hasPermi('wm:storagelocation:add')")
    @Log(title = "库位", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody WmStorageLocation wmStorageLocation)
    {
        return toAjax(wmStorageLocationService.insertWmStorageLocation(wmStorageLocation));
    }

    /**
     * 修改库位
     */
    @ApiOperation("修改库位详细信息")
    @PreAuthorize("@ss.hasPermi('wm:storagelocation:edit')")
    @Log(title = "库位", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody WmStorageLocation wmStorageLocation)
    {
        return toAjax(wmStorageLocationService.updateWmStorageLocation(wmStorageLocation));
    }

    /**
     * 删除库位
     */
    @ApiOperation("删除库位信息")
    @PreAuthorize("@ss.hasPermi('wm:storagelocation:remove')")
    @Log(title = "库位", businessType = BusinessType.DELETE)
    @DeleteMapping("/{locationIds}")
    public AjaxResult remove(@PathVariable Long[] locationIds)
    {
        return toAjax(wmStorageLocationService.deleteWmStorageLocationByLocationIds(locationIds));
    }
}
