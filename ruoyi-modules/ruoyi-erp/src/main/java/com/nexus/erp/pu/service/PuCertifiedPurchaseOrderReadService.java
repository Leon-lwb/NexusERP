package com.nexus.erp.pu.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseOrderReadMapper;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseOrderReadMapper.OrderHead;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseOrderReadMapper.AllocationView;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseOrderWorkflowMapper.Quote;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseOrderWorkflowMapper.Confirmation;
import com.nexus.erp.pu.service.PuCertifiedPurchaseOrderWorkflowService.WorkflowContext;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

/** 页面原生读取不调用写状态机；金额用原始十进制文本，不在PC计算采购金额。 */
@Service
public class PuCertifiedPurchaseOrderReadService {
    private final PuCertifiedPurchaseOrderWorkflowService workflow;
    private final PuCertifiedPurchaseOrderReadMapper mapper;

    public PuCertifiedPurchaseOrderReadService(PuCertifiedPurchaseOrderWorkflowService workflow,
        PuCertifiedPurchaseOrderReadMapper mapper) {
        this.workflow = Objects.requireNonNull(workflow);
        this.mapper = Objects.requireNonNull(mapper);
    }

    /** 展示不可变报价；只携带原生字段，确认信息为空时不猜已确认。 */
    public record QuoteView(Long quoteId, Long orderId, Integer quoteVersion, Long enteredBy,
        String requestKey, String quoteHash, String quantity, String unitPriceExcludingTax,
        String taxRate, String currency, String exchangeRate, String promisedArrivalDate,
        String validFrom, String validUntil, String supplierQuoteReference, String supplierContact,
        String evidenceReference, String evidenceSha256, String paymentTerms, String deliveryTerms,
        Long confirmationId, Long confirmedBy, String confirmationHash, String reviewNote) { }

    /** genericEditAllowedHint仅用于关闭旧UI入口；不是来源或业务写资格认证。 */
    public record Detail(OrderHead order, List<QuoteView> quotes, List<AllocationView> allocations, boolean priceVisible,
        String serverDate, boolean genericEditAllowedHint) {
        public Detail { quotes = List.copyOf(quotes); allocations = List.copyOf(allocations); }
    }

    /**
     * 按实际登录租户读取当前订单及最多100个报价版本，不使用FOR UPDATE或写事务。
     *
     * @param id 原生订单ID
     * @return 当前原始版本、价格可见性与来源提示，NULL历史版本原样返回
     */
    @DS("erp")
    public Detail readDetail(Long id) {
        WorkflowContext actor = workflow.currentWorkflowContext();
        require(id != null && id > 0, "订单身份无效");
        OrderHead head = mapper.readHead(actor.tenantId(), id);
        require(head != null && actor.tenantId().equals(head.tenantId()) && id.equals(head.orderId()), "当前租户订单详情未确认");
        List<QuoteView> views = new ArrayList<>();
        if (actor.priceVisible()) {
            List<Quote> quotes = mapper.readQuotes(actor.tenantId(), id);
            List<Confirmation> confirmations = mapper.readConfirmations(actor.tenantId(), id);
            require(quotes != null && confirmations != null && quotes.size() <= 100 && confirmations.size() <= 100,
                "报价版本超过安全读取范围或来源未知");
            Map<Long, Confirmation> confirmed = new HashMap<>();
            for (Confirmation c : confirmations) {
                require(c != null && actor.tenantId().equals(c.tenantId()) && c.quoteId() != null
                    && confirmed.put(c.quoteId(), c) == null, "报价确认身份或版本归属未知");
            }
            Set<Long> identities = new HashSet<>();
            for (Quote q : quotes) {
                require(q != null && actor.tenantId().equals(q.tenantId()) && id.equals(q.orderId())
                    && q.quoteId() != null && q.quoteId() > 0 && identities.add(q.quoteId()), "报价身份或订单归属未知");
                Confirmation c = confirmed.remove(q.quoteId());
                if (c != null) require(Objects.equals(q.quoteHash(), c.quoteHash()) && c.actorId() != null
                    && c.actorId() > 0 && !Objects.equals(q.enteredBy(), c.actorId()), "报价确认与录入来源不一致");
                views.add(new QuoteView(q.quoteId(), q.orderId(), q.quoteVersion(), q.enteredBy(), q.requestKey(),
                    q.quoteHash(), text(q.quantity()), text(q.unitPriceExcludingTax()), text(q.taxRate()), q.currency(),
                    text(q.exchangeRate()), q.promisedArrivalDate(), q.validFrom(), q.validUntil(),
                    q.supplierQuoteReference(), q.supplierContact(), q.evidenceReference(), q.evidenceSha256(),
                    q.paymentTerms(), q.deliveryTerms(), c == null ? null : c.confirmationId(),
                    c == null ? null : c.actorId(), c == null ? null : c.quoteHash(), c == null ? null : c.reviewNote()));
            }
            require(confirmed.isEmpty(), "报价确认缺少对应不可变报价");
        }
        List<AllocationView> allocations = mapper.readAllocations(actor.tenantId(), id);
        require(allocations != null && allocations.size() <= 200, "订单占用身份超过安全读取范围或未知");
        String date = mapper.databaseDate();
        require(date != null && date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"), "数据库业务日期未确认");
        WorkflowContext after = workflow.currentWorkflowContext();
        require(actor.equals(after), "订单读取期间主体或权限变化");
        boolean legacy = allocations.isEmpty() && Boolean.FALSE.equals(head.originalCommandPresent()) && head.version() == null
            && head.quoteId() == null && head.decisionRunId() == null && head.decisionActionId() == null
            && head.sourceRequestKey() == null && (head.requisitionId() == null
                || "MANUAL".equals(head.requisitionSourceType()));
        return new Detail(head, views, allocations, actor.priceVisible(), date, legacy);
    }

    private static String text(BigDecimal value) { return value == null ? null : value.toPlainString(); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new ServiceException(message);
    }
}
