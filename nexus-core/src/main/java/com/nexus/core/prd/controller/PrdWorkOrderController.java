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
import com.nexus.core.prd.domain.PrdWorkOrder;
import com.nexus.core.prd.service.IPrdWorkOrderService;
import com.nexus.common.utils.poi.ExcelUtil;

/**
 * 生产工单Controller
 *
 * @author nexus
 * @date 2025-12-16
 */
@Api(tags = "生产工单Controller")
@RestController
@RequestMapping("/prd/work")
public class PrdWorkOrderController extends BaseController
{
    @Autowired
    private IPrdWorkOrderService prdWorkOrderService;

    /**
     * 查询生产工单列表
     */
    @ApiOperation("查询生产工单列表")
    @PreAuthorize("@ss.hasPermi('prd:work:list')")
    @GetMapping("/list")
    public AjaxResult list(PrdWorkOrder prdWorkOrder)
    {
        List<PrdWorkOrder> list = prdWorkOrderService.selectPrdWorkOrderList(prdWorkOrder);
        return success(list);
    }

    /**
     * 导出生产工单列表
     */
    @ApiOperation("导出生产工单列表")
    @PreAuthorize("@ss.hasPermi('prd:work:export')")
    @Log(title = "生产工单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, PrdWorkOrder prdWorkOrder)
    {
        List<PrdWorkOrder> list = prdWorkOrderService.selectPrdWorkOrderList(prdWorkOrder);
        ExcelUtil<PrdWorkOrder> util = new ExcelUtil<PrdWorkOrder>(PrdWorkOrder.class);
        util.exportExcel(response, list, "生产工单数据");
    }

    /**
     * 获取生产工单详细信息
     */
    @ApiOperation("获取生产工单列表")
    @PreAuthorize("@ss.hasPermi('prd:work:query')")
    @GetMapping(value = "/{woId}")
    public AjaxResult getInfo(@PathVariable("woId") Long woId)
    {
        return success(prdWorkOrderService.selectPrdWorkOrderByWoId(woId));
    }

    /**
     * 新增生产工单
     */
    @ApiOperation("新增生产工单详细信息")
    @PreAuthorize("@ss.hasPermi('prd:work:add')")
    @Log(title = "生产工单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody PrdWorkOrder prdWorkOrder)
    {
        return toAjax(prdWorkOrderService.insertPrdWorkOrder(prdWorkOrder));
    }

    /**
     * 修改生产工单
     */
    @ApiOperation("修改生产工单详细信息")
    @PreAuthorize("@ss.hasPermi('prd:work:edit')")
    @Log(title = "生产工单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody PrdWorkOrder prdWorkOrder)
    {
        return toAjax(prdWorkOrderService.updatePrdWorkOrder(prdWorkOrder));
    }

    /**
     * 删除生产工单
     */
    @ApiOperation("删除生产工单信息")
    @PreAuthorize("@ss.hasPermi('prd:work:remove')")
    @Log(title = "生产工单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{woIds}")
    public AjaxResult remove(@PathVariable Long[] woIds)
    {
        return toAjax(prdWorkOrderService.deletePrdWorkOrderByWoIds(woIds));
    }
}
