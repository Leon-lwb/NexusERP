package com.nexus.core.so.controller;

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
import com.nexus.core.so.domain.SoSalesOrderHeader;
import com.nexus.core.so.service.ISoSalesOrderHeaderService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 销售订单Controller
 *
 * @author nexus
 * @date 2025-10-22
 */
@Api(tags = "销售订单Controller")
@RestController
@RequestMapping("/so/orderheader")
public class SoSalesOrderHeaderController extends BaseController
{
    @Autowired
    private ISoSalesOrderHeaderService soSalesOrderHeaderService;

    /**
     * 查询销售订单列表
     */
    @ApiOperation("查询销售订单列表")
    @PreAuthorize("@ss.hasPermi('so:orderheader:list')")
    @GetMapping("/list")
    public TableDataInfo list(SoSalesOrderHeader soSalesOrderHeader)
    {
        startPage();
        List<SoSalesOrderHeader> list = soSalesOrderHeaderService.selectSoSalesOrderHeaderList(soSalesOrderHeader);
        return getDataTable(list);
    }

    /**
     * 导出销售订单列表
     */
    @ApiOperation("导出销售订单列表")
    @PreAuthorize("@ss.hasPermi('so:orderheader:export')")
    @Log(title = "销售订单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SoSalesOrderHeader soSalesOrderHeader)
    {
        List<SoSalesOrderHeader> list = soSalesOrderHeaderService.selectSoSalesOrderHeaderList(soSalesOrderHeader);
        ExcelUtil<SoSalesOrderHeader> util = new ExcelUtil<SoSalesOrderHeader>(SoSalesOrderHeader.class);
        util.exportExcel(response, list, "销售订单数据");
    }

    /**
     * 获取销售订单详细信息
     */
    @ApiOperation("获取销售订单列表")
    @PreAuthorize("@ss.hasPermi('so:orderheader:query')")
    @GetMapping(value = "/{soId}")
    public AjaxResult getInfo(@PathVariable("soId") Long soId)
    {
        return success(soSalesOrderHeaderService.selectSoSalesOrderHeaderBySoId(soId));
    }

    /**
     * 新增销售订单
     */
    @ApiOperation("新增销售订单详细信息")
    @PreAuthorize("@ss.hasPermi('so:orderheader:add')")
    @Log(title = "销售订单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SoSalesOrderHeader soSalesOrderHeader)
    {
        return toAjax(soSalesOrderHeaderService.insertSoSalesOrderHeader(soSalesOrderHeader));
    }

    /**
     * 修改销售订单
     */
    @ApiOperation("修改销售订单详细信息")
    @PreAuthorize("@ss.hasPermi('so:orderheader:edit')")
    @Log(title = "销售订单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SoSalesOrderHeader soSalesOrderHeader)
    {
        return toAjax(soSalesOrderHeaderService.updateSoSalesOrderHeader(soSalesOrderHeader));
    }

    /**
     * 删除销售订单
     */
    @ApiOperation("删除销售订单信息")
    @PreAuthorize("@ss.hasPermi('so:orderheader:remove')")
    @Log(title = "销售订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{soIds}")
    public AjaxResult remove(@PathVariable Long[] soIds)
    {
        return toAjax(soSalesOrderHeaderService.deleteSoSalesOrderHeaderBySoIds(soIds));
    }
}
