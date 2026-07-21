package com.nexus.pay.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.domain.R;
import com.nexus.common.excel.utils.ExcelUtil;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import com.nexus.pay.domain.PayOrder;
import com.nexus.pay.domain.bo.PayOrderBo;
import com.nexus.pay.domain.vo.PayOrderVo;
import com.nexus.pay.service.IPayOrderService;
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

    /** 订单业务服务，提供订单分页、增删改查等能力。 */
    private final IPayOrderService payOrderService;

    /**
     * 分页查询支付订单列表。
     *
     * @param bo 订单查询条件（订单号、第三方订单号、状态、支付方式等）
     * @param pageQuery 分页参数
     * @return 分页后的订单视图列表
     */
    @Operation(summary = "查询订单列表")
    @SaCheckPermission("pay:order:list")
    @GetMapping("/list")
    public TableDataInfo<PayOrderVo> list(PayOrderBo bo, PageQuery pageQuery) {
        return payOrderService.selectPageList(bo, pageQuery);
    }

    /**
     * 导出符合条件的支付订单列表为 Excel。
     *
     * @param response HTTP 响应，用于写入 Excel 文件流
     * @param bo 订单查询条件
     */
    @Operation(summary = "导出订单列表")
    @SaCheckPermission("pay:order:export")
    @Log(title = "订单", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, PayOrderBo bo) {
        List<PayOrderVo> list = payOrderService.selectList(bo);
        ExcelUtil.exportExcel(list, "订单数据", PayOrderVo.class, response);
    }

    /**
     * 获取指定支付订单的详细信息。
     *
     * @param orderId 订单 ID
     * @return 订单视图对象
     */
    @Operation(summary = "获取订单详细信息")
    @SaCheckPermission("pay:order:query")
    @GetMapping(value = "/{orderId}")
    public R<PayOrderVo> getInfo(@PathVariable Long orderId) {
        return R.ok(payOrderService.selectById(orderId));
    }

    /**
     * 新增支付订单，自动生成商户订单号并初始化为「待支付」状态。
     *
     * @param bo 订单业务对象，含订单内容、金额、支付方式等
     * @return 创建成功的订单对象（含生成的订单号）
     */
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

    /**
     * 修改支付订单信息。
     *
     * @param payOrder 待更新的订单实体
     * @return 操作结果
     */
    @Operation(summary = "修改订单")
    @SaCheckPermission("pay:order:edit")
    @Log(title = "订单", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> edit(@RequestBody PayOrder payOrder) {
        return toAjax(payOrderService.updatePayOrder(payOrder));
    }

    /**
     * 按商户订单号删除支付订单。
     *
     * @param orderNumber 商户订单号
     * @return 操作结果
     */
    @Operation(summary = "通过订单号删除订单")
    @SaCheckPermission("pay:order:remove")
    @Log(title = "订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/orderNumber/{orderNumber}")
    public R<Void> removeByOrderNumber(@PathVariable String orderNumber) {
        return toAjax(payOrderService.deleteByOrderNumber(orderNumber));
    }

    /**
     * 批量删除支付订单。
     *
     * @param orderIds 待删除的订单 ID 数组
     * @return 操作结果
     */
    @Operation(summary = "批量删除订单")
    @SaCheckPermission("pay:order:remove")
    @Log(title = "订单", businessType = BusinessType.DELETE)
    @DeleteMapping("/{orderIds}")
    public R<Void> remove(@PathVariable Long[] orderIds) {
        return toAjax(payOrderService.deleteByIds(orderIds));
    }

}
