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
import com.nexus.core.so.domain.SoCustomerOrder;
import com.nexus.core.so.service.ISoCustomerOrderService;
import com.nexus.common.utils.poi.ExcelUtil;
import com.nexus.common.core.page.TableDataInfo;

/**
 * 客户订单Controller
 *
 * @author nexus
 * @date 2025-10-21
 */
@Api(tags = "客户订单Controller")
@RestController
@RequestMapping("/so/customerOrder")
public class SoCustomerOrderController extends BaseController
{
    @Autowired
    private ISoCustomerOrderService soCustomerOrderService;

    /**
     * 查询客户订单列表
     */
    @ApiOperation("查询客户订单列表")
    @PreAuthorize("@ss.hasPermi('so:customerOrder:list')")
    @GetMapping("/list")
    public TableDataInfo list(SoCustomerOrder soCustomerOrder)
    {
        startPage();
        List<SoCustomerOrder> list = soCustomerOrderService.selectSoCustomerOrderList(soCustomerOrder);
        return getDataTable(list);
    }

    /**
     * 导出客户订单列表
     */
    @ApiOperation("导出客户订单列表")
    @PreAuthorize("@ss.hasPermi('so:customerOrder:export')")
    @Log(title = "客户订单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SoCustomerOrder soCustomerOrder)
    {
        List<SoCustomerOrder> list = soCustomerOrderService.selectSoCustomerOrderList(soCustomerOrder);
        ExcelUtil<SoCustomerOrder> util = new ExcelUtil<SoCustomerOrder>(SoCustomerOrder.class);
        util.exportExcel(response, list, "客户订单数据");
    }

    /**
     * 获取客户订单详细信息
     */
    @ApiOperation("获取客户订单列表")
    @PreAuthorize("@ss.hasPermi('so:customerOrder:query')")
    @GetMapping(value = "/{coId}")
    public AjaxResult getInfo(@PathVariable("coId") Long coId)
    {
        return success(soCustomerOrderService.selectSoCustomerOrderByCoId(coId));
    }

    /**
     * 新增客户订单
     */
    @ApiOperation("新增客户订单详细信息")
    @PreAuthorize("@ss.hasPermi('so:customerOrder:add')")
    @Log(title = "客户订单", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SoCustomerOrder soCustomerOrder)
    {
        return toAjax(soCustomerOrderService.insertSoCustomerOrder(soCustomerOrder));
    }

    /**
     * 修改客户订单
     */
    @ApiOperation("修改客户订单详细信息")
    @PreAuthorize("@ss.hasPermi('so:customerOrder:edit')")
    @Log(title = "客户订单", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SoCustomerOrder soCustomerOrder)
    {
        return toAjax(soCustomerOrderService.updateSoCustomerOrder(soCustomerOrder));
    }

    /**
     * 删除客户订单
     */
    @ApiOperation("删除客户订单信息")
    @PreAuthorize("@ss.hasPermi('so:customerOrder:remove')")
    @Log(title = "客户订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{coIds}")
    public AjaxResult remove(@PathVariable Long[] coIds)
    {
        return toAjax(soCustomerOrderService.deleteSoCustomerOrderByCoIds(coIds));
    }
}
