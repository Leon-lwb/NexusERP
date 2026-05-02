package org.dromara.pay.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.domain.R;
import org.dromara.common.excel.utils.ExcelUtil;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.pay.domain.PayOrder;
import org.dromara.pay.domain.bo.PayOrderBo;
import org.dromara.pay.domain.vo.PayOrderVo;
import org.dromara.pay.service.IPayOrderService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单Controller
 *
 * @author Dftre
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/pay/order")
@Tag(name = "【订单】管理")
public class PayOrderController extends BaseController {

    private final IPayOrderService payOrderService;

    @Operation(summary = "查询订单列表")
    @SaCheckPermission("pay:order:list")
    @GetMapping("/list")
    public TableDataInfo<PayOrderVo> list(PayOrderBo bo, PageQuery pageQuery) {
        return payOrderService.selectPageList(bo, pageQuery);
    }

    @Operation(summary = "导出订单列表")
    @SaCheckPermission("pay:order:export")
    @Log(title = "订单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, PayOrderBo bo) {
        List<PayOrderVo> list = payOrderService.selectList(bo);
        ExcelUtil.exportExcel(list, "订单数据", PayOrderVo.class, response);
    }

    @Operation(summary = "获取订单详细信息")
    @SaCheckPermission("pay:order:query")
    @GetMapping(value = "/{orderId}")
    public R<PayOrderVo> getInfo(@PathVariable Long orderId) {
        return R.ok(payOrderService.selectById(orderId));
    }

    @Operation(summary = "新增订单")
    @SaCheckPermission("pay:order:add")
    @Log(title = "订单", businessType = BusinessType.INSERT)
    @PostMapping
    public R<PayOrder> add(@Validated @RequestBody PayOrderBo bo) {
        PayOrder payOrder = new PayOrder();
        payOrder.setOrderNumber(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        payOrder.setOrderContent(bo.getOrderContent());
        payOrder.setTotalAmount(bo.getTotalAmount());
        payOrder.setActualAmount(bo.getActualAmount() != null ? bo.getActualAmount() : bo.getTotalAmount());
        payOrder.setOrderMessage(bo.getOrderMessage());
        payOrder.setPayType(bo.getPayType());
        payOrder.setOrderStatus("待支付");
        payOrderService.insertPayOrder(payOrder);
        return R.ok(payOrder);
    }

    @Operation(summary = "修改订单")
    @SaCheckPermission("pay:order:edit")
    @Log(title = "订单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody PayOrder payOrder) {
        return toAjax(payOrderService.updatePayOrder(payOrder));
    }

    @Operation(summary = "通过订单号删除订单")
    @SaCheckPermission("pay:order:remove")
    @Log(title = "订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/orderNumber/{orderNumber}")
    public R<Void> removeByOrderNumber(@PathVariable String orderNumber) {
        return toAjax(payOrderService.deleteByOrderNumber(orderNumber));
    }

    @Operation(summary = "批量删除订单")
    @SaCheckPermission("pay:order:remove")
    @Log(title = "订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{orderIds}")
    public R<Void> remove(@PathVariable Long[] orderIds) {
        return toAjax(payOrderService.deleteByIds(orderIds));
    }

}
