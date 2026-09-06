package com.nexus.erp.fin.arap.mq;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.domain.PayReceivedMessage;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.erp.fin.arap.constant.ArapConstants;
import com.nexus.erp.fin.arap.domain.FinReceipt;
import com.nexus.erp.fin.arap.domain.bo.FinSettlementBo;
import com.nexus.erp.fin.arap.service.IFinReceiptService;
import com.nexus.erp.fin.arap.service.IFinSettlementService;
import com.nexus.erp.fin.gl.constant.GlConstants;
import com.nexus.erp.fin.gl.mq.GlPostingMessage;
import com.nexus.erp.so.domain.bo.SoInvoiceBo;
import com.nexus.erp.so.domain.vo.SoInvoiceVo;
import com.nexus.erp.so.service.ISoInvoiceService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * 支付收款通知消费者:pay 回调 -> 建 AR 收款单 -> GL 过账 -> FIFO 自动核销该客户未清发票。
 * <p>租户上下文由 BaseMessageConsumer 自动恢复。</p>
 *
 * @author nexus
 * @date 2026-07-30
 */
@Slf4j
@RequiredArgsConstructor
@Component
@DS("erp")
public class PayReceivedConsumer extends BaseMessageConsumer<PayReceivedMessage> {

    private final IFinReceiptService receiptService;
    private final IFinSettlementService settlementService;
    private final ISoInvoiceService soInvoiceService;
    private final ReliableMessageProducer messageProducer;

    /** 返回 PayReceivedMessage 类型,供 BaseMessageConsumer 反序列化 */
    @Override
    protected Class<PayReceivedMessage> getMessageClass() {
        return PayReceivedMessage.class;
    }

    /** 消费入口:委托 doReceive 执行 */
    @Override
    protected void doProcess(PayReceivedMessage message) {
        log.info("处理支付收款通知: payOrderNumber={}, amount={}, clientId={}",
            message.getPayOrderNumber(), message.getAmount(), message.getClientId());
        doReceive(message);
    }

    /**
     * 解析租户ID：使用 tenantIdStr 避免 Long 精度丢失（"000000" → 0 → "0" 不匹配数据库）
     */
    @Override
    protected String resolveTenantId(PayReceivedMessage message) {
        String tid = message.getTenantIdStr();
        return tid != null && !tid.isEmpty() ? tid : "000000";
    }

    /**
     * 支付回调处理:自动建 AR 收款单(PAY_CALLBACK,CONFIRMED) + 发 GlPostingMessage(RECEIPT) 异步过账 +
     * FIFO 按发票日期排序自动核销该客户未清 AR 发票,直至金额耗尽。
     */
    private void doReceive(PayReceivedMessage message) {
        if (message.getClientId() == null) {
            log.warn("支付收款通知无 clientId,跳过自动核销: payOrderNumber={}", message.getPayOrderNumber());
            return;
        }
        BigDecimal amount = message.getAmount() != null ? message.getAmount() : BigDecimal.ZERO;
        if (amount.signum() <= 0) {
            log.warn("支付收款通知金额<=0,跳过: payOrderNumber={}", message.getPayOrderNumber());
            return;
        }

        // 0. 幂等防重:同一支付订单仅建一张收款单(重复回调/MQ 重投直接跳过,防重复建单+重复核销)
        if (StringUtils.isNotBlank(message.getPayOrderNumber())
            && receiptService.existsByPayOrderNumber(message.getPayOrderNumber())) {
            log.warn("该支付订单已存在收款单,跳过重复建单: payOrderNumber={}", message.getPayOrderNumber());
            return;
        }

        // 1. 建 AR 收款单(pay 回调直接 CONFIRMED)
        FinReceipt receipt = new FinReceipt();
        receipt.setReceiptNo("RC" + IdWorker.getId());
        receipt.setReceiptType(mapReceiptType(message.getPayType()));
        receipt.setDirection(ArapConstants.DIRECTION_AR);
        receipt.setClientId(message.getClientId());
        receipt.setAmount(amount);
        receipt.setSettledAmount(BigDecimal.ZERO);
        receipt.setCurrency("CNY");
        receipt.setExchangeRate(BigDecimal.ONE);
        receipt.setThirdPartyNo(message.getThirdPartyNo());
        receipt.setPayOrderNumber(message.getPayOrderNumber());
        receipt.setSource(ArapConstants.SOURCE_PAY_CALLBACK);
        receipt.setStatus(ArapConstants.RECEIPT_STATUS_CONFIRMED);
        receipt.setReceiptDate(new Date());
        receipt.setVersion(0);
        try {
            receiptService.save(receipt);
        } catch (DuplicateKeyException e) {
            // 并发重复回调被 uk_pay_order 唯一索引拦截,对方消费者已建单,本消息直接确认
            log.warn("并发重复建单被唯一索引拦截: payOrderNumber={}", message.getPayOrderNumber());
            return;
        }

        // 2. 触发 GL 过账(ReceiptPoster 制单)
        GlPostingMessage glMsg = new GlPostingMessage();
        glMsg.setSourceType(GlConstants.SOURCE_RECEIPT);
        glMsg.setSourceId(receipt.getReceiptId());
        glMsg.setSourceNo(receipt.getReceiptNo());
        glMsg.setVoucherDate(receipt.getReceiptDate());
        glMsg.setTenantIdStr(message.getTenantIdStr());
        glMsg.setSourceModule("fin");
        messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE, RoutingKeyConstants.GL_POSTING_RECEIPT, glMsg);

        // 3. FIFO 自动核销该客户最旧未清 AR 发票
        SoInvoiceBo soBo = new SoInvoiceBo();
        soBo.setClientId(message.getClientId());
        soBo.setStatus(com.nexus.erp.so.service.ISoInvoiceService.STATUS_ISSUED);
        List<SoInvoiceVo> invoices = soInvoiceService.selectList(soBo);
        invoices.sort(Comparator.comparing(v -> v.getInvoiceDate() != null ? v.getInvoiceDate() : new Date(0)));
        BigDecimal remaining = amount;
        for (SoInvoiceVo inv : invoices) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal open = nz(inv.getTotalAmount()).subtract(nz(inv.getVerifiedAmount()));
            if (open.signum() <= 0) {
                continue;
            }
            BigDecimal settle = remaining.min(open);
            try {
                FinSettlementBo sbo = new FinSettlementBo();
                sbo.setDirection(ArapConstants.DIRECTION_AR);
                sbo.setInvoiceId(inv.getInvoiceId());
                sbo.setReceiptId(receipt.getReceiptId());
                sbo.setSettleAmount(settle);
                settlementService.settle(sbo);
                remaining = remaining.subtract(settle);
                log.info("支付回调自动核销: invoiceId={}, settle={}, remaining={}", inv.getInvoiceId(), settle, remaining);
            } catch (Exception e) {
                log.error("支付回调自动核销失败: invoiceId={}", inv.getInvoiceId(), e);
                break;
            }
        }
        log.info("支付收款通知处理完成: payOrderNumber={}, receiptId={}, 未核销余额={}",
            message.getPayOrderNumber(), receipt.getReceiptId(), remaining);
    }

    /** 支付渠道类型映射:alipay->ALIPAY,wechat/wx->WECHAT,其他->OTHER */
    private String mapReceiptType(String payType) {
        if (payType == null) {
            return "OTHER";
        }
        return switch (payType) {
            case "alipay" -> "ALIPAY";
            case "wechat", "wx" -> "WECHAT";
            default -> "OTHER";
        };
    }

    /** RabbitMQ 监听 PAY_RECEIVED_QUEUE,委托 BaseMessageConsumer.processMessage 处理(含重试/死信) */
    @RabbitListener(queues = com.nexus.common.mq.constant.QueueConstants.PAY_RECEIVED_QUEUE)
    public void onMessage(PayReceivedMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }

    /** null->BigDecimal.ZERO,防空指针 */
    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
