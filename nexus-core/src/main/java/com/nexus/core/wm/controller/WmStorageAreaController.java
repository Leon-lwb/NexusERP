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
import com.nexus.core.wm.domain.WmStorageArea;
import com.nexus.core.wm.service.IWmStorageAreaService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 库区Controller
 *
 * @author nexus
 * @date 2025-12-01
 */
@Api(tags = "库区Controller")
@RestController
@RequestMapping("/wm/storagearea")
public class WmStorageAreaController extends BaseController
{
    @Autowired
    private IWmStorageAreaService wmStorageAreaService;

    /**
     * 查询库区列表
     */
    @ApiOperation("查询库区列表")
    @PreAuthorize("@ss.hasPermi('wm:storagearea:list')")
    @GetMapping("/list")
    public TableDataInfo list(WmStorageArea wmStorageArea)
    {
        startPage();
        List<WmStorageArea> list = wmStorageAreaService.selectWmStorageAreaList(wmStorageArea);
        return getDataTable(list);
    }

    /**
     * 导出库区列表
     */
    @ApiOperation("导出库区列表")
    @PreAuthorize("@ss.hasPermi('wm:storagearea:export')")
    @Log(title = "库区", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, WmStorageArea wmStorageArea)
    {
        List<WmStorageArea> list = wmStorageAreaService.selectWmStorageAreaList(wmStorageArea);
        ExcelUtil<WmStorageArea> util = new ExcelUtil<WmStorageArea>(WmStorageArea.class);
        util.exportExcel(response, list, "库区数据");
    }

    /**
     * 获取库区详细信息
     */
    @ApiOperation("获取库区列表")
    @PreAuthorize("@ss.hasPermi('wm:storagearea:query')")
    @GetMapping(value = "/{areaId}")
    public AjaxResult getInfo(@PathVariable("areaId") Long areaId)
    {
        return success(wmStorageAreaService.selectWmStorageAreaByAreaId(areaId));
    }

    /**
     * 新增库区
     */
    @ApiOperation("新增库区详细信息")
    @PreAuthorize("@ss.hasPermi('wm:storagearea:add')")
    @Log(title = "库区", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody WmStorageArea wmStorageArea)
    {
        return toAjax(wmStorageAreaService.insertWmStorageArea(wmStorageArea));
    }

    /**
     * 修改库区
     */
    @ApiOperation("修改库区详细信息")
    @PreAuthorize("@ss.hasPermi('wm:storagearea:edit')")
    @Log(title = "库区", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody WmStorageArea wmStorageArea)
    {
        return toAjax(wmStorageAreaService.updateWmStorageArea(wmStorageArea));
    }

    /**
     * 删除库区
     */
    @ApiOperation("删除库区信息")
    @PreAuthorize("@ss.hasPermi('wm:storagearea:remove')")
    @Log(title = "库区", businessType = BusinessType.DELETE)
    @DeleteMapping("/{areaIds}")
    public AjaxResult remove(@PathVariable Long[] areaIds)
    {
        return toAjax(wmStorageAreaService.deleteWmStorageAreaByAreaIds(areaIds));
    }
}
