package com.nexus.web.controller.system;

import com.nexus.common.annotation.Log;
import com.nexus.common.core.controller.BaseController;
import com.nexus.common.core.domain.AjaxResult;
import com.nexus.common.core.plus.response.Result;
import com.nexus.common.enums.BusinessType;
import com.nexus.common.utils.StringUtils;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.system.domain.SysDicArea;
import com.nexus.system.service.ISysDicAreaService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 行政区划Controller
 *
 * @author nexus
 * @date 2025-10-29
 */
@Api(tags = "行政区划Controller")
@RestController
@RequestMapping("/system/area")
public class SysDicAreaController extends BaseController {
    @Autowired
    private ISysDicAreaService sysDicAreaService;

    /**
     * 查询行政区划列表
     */
    @ApiOperation("查询行政区划列表")
    @PreAuthorize("@ss.hasPermi('system:area:list')")
    @GetMapping("/list")
    public AjaxResult list(SysDicArea sysDicArea) {
        List<SysDicArea> list = sysDicAreaService.selectSysDicAreaTreeList(sysDicArea);
        return success(list);
    }

    /**
     * 查询行政区划子节点
     */
    @ApiOperation("查询行政区划列表")
    @PreAuthorize("@ss.hasPermi('system:area:list')")
    @GetMapping("/listChildren")
    public AjaxResult listChildren(SysDicArea sysDicArea) {
        // 传入空的SysDicArea对象以获取所有数据
        List<SysDicArea> list = sysDicAreaService.selectChildrenTreeList(sysDicArea);
        return success(list);
    }

    /**
     * 获取所有省份
     */
    @ApiOperation("获取所有省份")
    @PreAuthorize("@ss.hasPermi('system:area:list')")
    @GetMapping("/provinces")
    public AjaxResult getProvinces() {
        List<SysDicArea> provinces = sysDicAreaService.selectProvinces();
        return success(provinces);
    }

    /**
     * 获取下一级行政区划信息
     */
    @ApiOperation("获取下一级行政区划信息")
    @PreAuthorize("@ss.hasPermi('system:area:list')")
    @GetMapping("/nextLevel/{parentAreaId}")
    public AjaxResult getNextLevel(@PathVariable Long parentAreaId) {
        // 参数校验
        if (parentAreaId == null) {
            return AjaxResult.error("参数错误：parentAreaId不能为空");
        }
        
        List<SysDicArea> nextLevel = sysDicAreaService.selectNextLevel(parentAreaId);
        return success(nextLevel);
    }

    /**
     * 查询行政区划列表（排除节点）
     */
    @ApiOperation("查询行政区划列表（排除节点）")
    @PreAuthorize("@ss.hasPermi('system:area:list')")
    @GetMapping("/list/exclude/{pkid}")
    public AjaxResult listAreaExcludeChild(@PathVariable(value = "pkid", required = false) Long pkid) {
        List<SysDicArea> list = sysDicAreaService.selectSysDicAreaTreeList(new SysDicArea());
        return success(list);
    }

    /**
     * 导出行政区划列表
     */
    @ApiOperation("导出行政区划列表")
    @PreAuthorize("@ss.hasPermi('system:area:export')")
    @Log(title = "行政区划", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysDicArea sysDicArea) {
        List<SysDicArea> list = sysDicAreaService.selectSysDicAreaList(sysDicArea);
        ExcelUtil<SysDicArea> util = new ExcelUtil<SysDicArea>(SysDicArea.class);
        util.exportExcel(response, list, "行政区划数据");
    }

    /**
     * 获取行政区划详细信息
     */
    @ApiOperation("获取行政区划列表")
    @PreAuthorize("@ss.hasPermi('system:area:query')")
    @GetMapping(value = "/{pkid}")
    public AjaxResult getInfo(@PathVariable("pkid") Long pkid) {
        return success(sysDicAreaService.selectSysDicAreaByPkid(pkid));
    }

    /**
     * 新增行政区划
     */
    @ApiOperation("新增行政区划详细信息")
    @PreAuthorize("@ss.hasPermi('system:area:add')")
    @Log(title = "行政区划", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SysDicArea sysDicArea) {
        return toAjax(sysDicAreaService.insertSysDicArea(sysDicArea));
    }

    /**
     * 修改行政区划
     */
    @ApiOperation("修改行政区划详细信息")
    @PreAuthorize("@ss.hasPermi('system:area:edit')")
    @Log(title = "行政区划", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SysDicArea sysDicArea) {
        return toAjax(sysDicAreaService.updateSysDicArea(sysDicArea));
    }

    /**
     * 删除行政区划
     */
    @ApiOperation("删除行政区划信息")
    @PreAuthorize("@ss.hasPermi('system:area:remove')")
    @Log(title = "行政区划", businessType = BusinessType.DELETE)
    @DeleteMapping("/{pkids}")
    public AjaxResult remove(@PathVariable Long[] pkids) {
        return toAjax(sysDicAreaService.deleteSysDicAreaByPkids(pkids));
    }
}