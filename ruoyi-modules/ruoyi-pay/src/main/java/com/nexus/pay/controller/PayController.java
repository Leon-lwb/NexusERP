package com.nexus.pay.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    private final IPayOrderService payOrderService;
    private final Map<String, PayService> payServiceMap;

    @PostConstruct
    public void init() {
        if (payServiceMap == null || payServiceMap.isEmpty()) {
            log.warn("请注意，没有加载任何支付服务");
        } else {
            payServiceMap.forEach((k, v) -> log.info("已加载支付服务 {}", k));
        }
    }

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
