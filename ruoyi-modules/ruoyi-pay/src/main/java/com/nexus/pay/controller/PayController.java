package com.nexus.pay.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.web.core.BaseController;
import com.nexus.pay.domain.PayOrder;
import com.nexus.pay.service.IPayOrderService;
import com.nexus.pay.service.PayService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 支付业务Controller
 *
 * @author Dftre
 */
@Slf4j
@Tag(name = "支付业务")
@RequestMapping("/pay")
@RestController
@RequiredArgsConstructor
public class PayController extends BaseController {

    /** 订单业务服务，提供按订单号查询支付订单等能力。 */
    private final IPayOrderService payOrderService;

    /** 支付通道服务映射，key 为 "pay:service:{channel}"，由 Spring 容器按 bean 名称装配。 */
    private final Map<String, PayService> payServiceMap;

    @PostConstruct
    public void init() {
        if (payServiceMap == null || payServiceMap.isEmpty()) {
            log.warn("请注意，没有加载任何支付服务");
        } else {
            payServiceMap.forEach((k, v) -> log.info("已加载支付服务 {}", k));
        }
    }

    /**
     * 根据支付通道与订单号获取支付链接或表单。
     *
     * @param channel 支付方式（支付通道标识，如 alipay、wechat、sqb）
     * @param orderNumber 商户订单号
     * @return 支付链接或表单字符串（如支付宝当面付二维码链接或 PC 网页支付表单）
     * @throws Exception 调用支付 SDK 异常时抛出
     */
    @SaCheckPermission("pay:order:query")
    @Operation(summary = "获取支付链接", description = "也不一定是链接，比如支付宝是一串表单代码")
    @Parameters({
            @Parameter(name = "channel", description = "支付方式", required = true),
            @Parameter(name = "orderNumber", description = "订单号", required = true)
    })
    @GetMapping("/{channel}/url/{orderNumber}")
    public R<String> url(@PathVariable String channel, @PathVariable String orderNumber) throws Exception {
        PayService payService = payServiceMap.get("pay:service:" + channel);
        PayOrder payOrder = payOrderService.selectPayOrderByOrderNumber(orderNumber);
        String url = payService.payUrl(payOrder);
        return R.ok("操作成功", url);
    }

    /**
     * 接收支付通道的支付/退款回调通知并委托对应支付服务处理。
     *
     * @param channel 支付方式（支付通道标识）
     * @param orderNumber 商户订单号（回调未携带时可空）
     * @param type 通知类型，缺省按 "pay" 处理，可为 "refund"
     * @param request HTTP 请求，承载通道回调参数
     * @param response HTTP 响应
     * @return 回调处理结果字符串（如 "success" / "fail"）
     * @throws Exception 调用支付 SDK 异常时抛出
     */
    @Operation(summary = "支付/退款回调")
    @Parameters({
            @Parameter(name = "channel", description = "支付方式", required = true),
            @Parameter(name = "type", description = "通知类型", required = false)
    })
    @RequestMapping({
            "/{channel}/notify",
            "/{channel}/notify/{orderNumber}",
            "/{channel}/notify/{orderNumber}/{type}"
    })
    public String notify(
            @PathVariable String channel,
            @PathVariable(required = false) String orderNumber,
            @PathVariable(required = false) String type,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {
        log.info("notify type:{} channel: {}, orderNumber: {}", type, channel, orderNumber);
        if (type == null) {
            type = "pay";
        }
        PayOrder payOrder = null;
        if (StringUtils.isNotBlank(orderNumber)) {
            payOrder = payOrderService.selectPayOrderByOrderNumber(orderNumber);
            payOrder.setPayType(channel);
        }
        PayService payService = payServiceMap.get("pay:service:" + channel);
        return payService.notify(request, response, payOrder, type);
    }

    /**
     * 查询指定订单的支付状态（委托对应支付通道实时查询）。
     *
     * @param orderNumber 商户订单号
     * @return 刷新支付状态后的订单对象
     * @throws Exception 调用支付 SDK 异常时抛出
     */
    @SaCheckPermission("pay:order:query")
    @Operation(summary = "查询支付状态")
    @Parameters({
            @Parameter(name = "orderNumber", description = "订单号", required = true)
    })
    @PostMapping("/query/{orderNumber}")
    public R<PayOrder> query(@PathVariable String orderNumber) throws Exception {
        PayOrder payOrder = payOrderService.selectPayOrderByOrderNumber(orderNumber);
        String channel = payOrder.getPayType();
        if (StringUtils.isBlank(channel)) {
            return R.ok(payOrder);
        }
        PayService payService = payServiceMap.get("pay:service:" + channel);
        return R.ok(payService.query(payOrder));
    }

    /**
     * 对指定订单发起退款（委托对应支付通道处理）。
     *
     * @param orderNumber 商户订单号
     * @return 退款后的订单对象；订单尚未支付时返回失败信息
     */
    @SaCheckPermission("pay:order:refund")
    @Operation(summary = "退款")
    @Parameters({
            @Parameter(name = "orderNumber", description = "订单号", required = true)
    })
    @PostMapping("/refund/{orderNumber}")
    public R<PayOrder> refund(@PathVariable String orderNumber) {
        PayOrder payOrder = payOrderService.selectPayOrderByOrderNumber(orderNumber);
        String channel = payOrder.getPayType();
        if (StringUtils.isBlank(channel)) {
            return R.fail("该订单尚未支付，无法退款");
        }
        PayService payService = payServiceMap.get("pay:service:" + channel);
        return R.ok(payService.refund(payOrder));
    }

}
