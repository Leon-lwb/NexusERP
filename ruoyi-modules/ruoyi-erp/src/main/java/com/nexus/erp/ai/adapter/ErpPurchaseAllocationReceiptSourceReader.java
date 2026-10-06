package com.nexus.erp.ai.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.mapper.ErpPurchaseAllocationReceiptSourceMapper;
import com.nexus.erp.ai.adapter.mapper.ErpPurchaseAllocationReceiptSourceMapper.*;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import com.nexus.erp.wm.service.WmCertifiedReceiptPostingService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import static com.nexus.erp.ai.adapter.ErpPurchaseAllocationLineageReader.*;

/** 同ERP独占来源事务中证明原收货履约；不写库存、计数、事件或通知，也不回调采购来源认证。 */
@Service
@DS("erp")
public class ErpPurchaseAllocationReceiptSourceReader {
    private static final String ORDER = "ORDER_COMMITTED_NOT_TIMELY_SUPPLY";
    private static final String INTENT = "REQUEST_INTENT";
    private static final Set<String> RECEIVING = Set.of("APPROVED", "PARTIALLY_RECEIVED", "PARTIAL_RECEIVED", "RECEIVED");
    private static final Set<String> UNPOSTED = Set.of("DRAFT", "SUBMITTED", "APPROVING", "APPROVED", "SHELVED");
    private static final BigDecimal MAX = new BigDecimal("99999999999999.9999");
    private static final DateTimeFormatter POSTED_AT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT);
    private final ErpPurchaseAllocationReceiptSourceMapper mapper;
    private final PuRequirementErpTransactionQualification qualification;
    private final ObjectProvider<WmCertifiedReceiptPostingService> posting;

    /** 延迟解析已有过账服务，避免其订单工作流经采购认证回到本读取器的构造依赖闭环。 */
    public ErpPurchaseAllocationReceiptSourceReader(ErpPurchaseAllocationReceiptSourceMapper mapper,
            PuRequirementErpTransactionQualification qualification,
            ObjectProvider<WmCertifiedReceiptPostingService> posting) {
        this.mapper = Objects.requireNonNull(mapper);
        this.qualification = Objects.requireNonNull(qualification);
        this.posting = posting;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ReceiptSourceSnapshot readLocked(String tenant, AllocationSource allocation,
            OrderLineSource orderLine, OrderHeadSource orderHead) {
        need(TenantHelper.isEnable() && tenant != null && tenant.equals(TenantHelper.getTenantId()), "请求租户不符");
        qualification.requireSourceChainTransaction();
        requireIdentity(tenant, allocation, orderLine, orderHead);
        need(posting != null, "缺少永久过账重放提供者");
        var heads = index(query(() -> mapper.lockHeads(orderHead.orderId(), orderLine.lineId())), ReceiptHead::receiveId, 1000);
        need(!heads.isEmpty(), "正履约缺少任何原收货头");
        var headIds = List.copyOf(heads.keySet());
        var lines = index(query(() -> mapper.lockLines(orderLine.lineId(), headIds)), ReceiptLine::lineId, 10000);
        var postings = index(query(() -> mapper.lockPostings(headIds)), Posting::receiveId, 1000);
        var events = List.copyOf(nonNull(query(() -> mapper.lockEvents(allocation.allocationId())), 10000));
        Map<String,String> versions = new TreeMap<>();
        Set<Long> purchaseIds = new TreeSet<>();
        for (ReceiptLine line : lines.values()) {
            need(positive(line.orderLineId()), "收货原行缺少明确采购父行"); purchaseIds.add(line.orderLineId());
        }
        need(!purchaseIds.isEmpty(), "收货原头没有任何采购行引用");
        var purchaseLines = index(query(() -> mapper.lockPurchaseLines(List.copyOf(purchaseIds))), PurchaseLine::lineId, 10000);
        need(purchaseLines.keySet().equals(purchaseIds), "真实采购父行全集缺失，不能跳过兄弟来源");
        for (PurchaseLine parent : purchaseLines.values()) {
            need(tenant.equals(parent.tenantId()) && orderHead.orderId().equals(parent.orderId())
                && allocation.materialId().equals(parent.materialId()) && allocation.baseUomId().toString().equals(parent.unit())
                && "0".equals(parent.delFlag()), "真实兄弟采购行跨父、跨租户、物料、单位或删除事实不符");
            versions.put("receipt-source:pu_purchase_order_line:" + parent.lineId(), rawFingerprint(parent));
        }
        Map<Long,ReceiptProof> receipts = new TreeMap<>();
        Map<Long,Posting> postingByLine = new TreeMap<>();
        Map<Long,List<ReceiptLine>> byHead = new TreeMap<>();
        for (ReceiptLine line : lines.values()) {
            need(tenant.equals(line.tenantId()) && heads.containsKey(line.receiveId())
                && allocation.materialId().equals(line.materialId()) && allocation.baseUomId().toString().equals(line.baseUomId())
                && "0".equals(line.delFlag()) && positive(line.orderLineId())
                && (line.sourceDocLineId() == null || line.orderLineId().equals(line.sourceDocLineId())), "原收货行身份、父头、单位或采购引用未知");
            quantity(line.quantity(), true);
            byHead.computeIfAbsent(line.receiveId(), ignored -> new ArrayList<>()).add(line);
            versions.put("receipt-source:wm_material_receive_line:" + line.lineId(), rawFingerprint(line));
        }
        for (ReceiptHead head : heads.values()) {
            need(tenant.equals(head.tenantId()) && orderHead.orderId().equals(head.orderId()) && "0".equals(head.delFlag())
                && orderHead.vendorId().equals(head.vendorId()) && text(head.receiveCode()), "原收货头租户、订单、供应商或删除事实不符");
            List<ReceiptLine> children = byHead.get(head.receiveId());
            need(children != null && !children.isEmpty(), "原收货头缺少全行，禁止跳过孤立来源");
            versions.put("receipt-source:wm_material_receive:" + head.receiveId(), rawFingerprint(head));
            Posting saved = postings.get(head.receiveId());
            if (!"FINISHED".equals(head.status())) {
                need(UNPOSTED.contains(head.status()) && saved == null, "未知收货状态或未完成头已有永久过账");
                // 所有兄弟原行已由实际采购父行独立核验；保留其摘要，不将未过账声明量计作目标履约。
                continue;
            }
            need(saved != null && tenant.equals(saved.tenantId()) && allocation.materialId().equals(saved.materialId())
                && positive(saved.actorId()) && digest(saved.sourceFingerprint())
                && Set.of("PENDING", "ENQUEUED").contains(saved.notificationStatus()), "缺少真正永久过账或其原始身份不符");
            quantity(saved.totalQuantity(), true);
            BigDecimal total = children.stream().map(ReceiptLine::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
            equal(total, saved.totalQuantity(), "永久过账总量与全原收货行不一致");
            need(head.receiveAt() != null && head.shelfAt() != null && head.receiveAt().signum() > 0
                && head.receiveAt().compareTo(head.shelfAt()) <= 0
                && head.shelfAt().compareTo(BigDecimal.valueOf(postedInstant(saved.postedAt()).toEpochMilli())) <= 0, "原收货、上架与过账时序未知");
            // 该入口先锁定真实posting，内部只允许完整replay；不调用公开post、消费收货或MQ。
            var checked = query(() -> posting.getObject().readPostedLocked(tenant, head.receiveId()));
            need(checked != null && orderHead.orderId().equals(checked.orderId()) && saved.postedAt().equals(checked.postedAt())
                && saved.notificationStatus().equals(checked.notificationStatus()) && checked.result() != null
                && checked.result().replay() && head.receiveId().equals(checked.result().receiveId())
                && allocation.materialId().equals(checked.result().materialId())
                && saved.sourceFingerprint().equals(checked.result().sourceFingerprint()), "完整原生重放与永久posting不同源");
            equal(saved.totalQuantity(), checked.result().quantity(), "完整重放数量不符");
            versions.put("receipt-posting:" + head.receiveId(), saved.sourceFingerprint());
            versions.put("receipt-source:wm_certified_receipt_posting:" + head.receiveId(), rawFingerprint(saved));
            for (ReceiptLine line : children) {
                if (!orderLine.lineId().equals(line.orderLineId())) continue;
                receipts.put(line.lineId(), new ReceiptProof(head.receiveId(), line.lineId(), line.quantity(), saved.sourceFingerprint(), saved.postedAt()));
                postingByLine.put(line.lineId(), saved);
            }
        }
        need(heads.keySet().containsAll(postings.keySet()) && !receipts.isEmpty(), "存在孤立永久posting或无目标原收货行");
        Clock clock = query(mapper::clock);
        need(clock != null && "+08:00".equals(clock.databaseTimeZone()) && clock.epochSeconds() != null
            && clock.epochSeconds().signum() > 0, "最终数据库时钟或时区未知");
        Instant now = secondsInstant(clock.epochSeconds());
        for (ReceiptProof receipt : receipts.values()) need(!postedInstant(receipt.postedAt()).isAfter(now), "原posting时点在未来");
        BigDecimal sum = receipts.values().stream().map(ReceiptProof::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        equal(sum, allocation.fulfilledBaseQty(), "原收货行全集与账本履约累计不一致");
        equal(sum, orderLine.receivedQuantity(), "原收货行全集与真实订单实收计数不一致");
        verifyEvents(tenant, allocation, orderHead, receipts, postingByLine, events, now, versions);
        var proof = new ReceiptFulfillmentProof(allocation.allocationId(), orderHead.orderId(), orderLine.lineId(),
            allocation.materialId(), allocation.baseUomId(), allocation.fulfilledBaseQty(), List.copyOf(receipts.values()), null);
        proof = new ReceiptFulfillmentProof(proof.allocationId(), proof.orderId(), proof.orderLineId(), proof.materialId(), proof.baseUomId(),
            proof.fulfilledBaseQty(), proof.receipts(), fulfillmentFingerprint(proof));
        versions.put("receipt-fulfillment:" + allocation.allocationId(), proof.sourceFingerprint());
        qualification.requireSourceChainTransaction();
        return new ReceiptSourceSnapshot(proof, versions);
    }

    private static void requireIdentity(String tenant, AllocationSource a, OrderLineSource l, OrderHeadSource h) {
        need(a != null && l != null && h != null && tenant.equals(a.tenantId()) && tenant.equals(l.tenantId()) && tenant.equals(h.tenantId())
            && positive(a.allocationId()) && positive(a.materialId()) && positive(a.baseUomId()) && ORDER.equals(a.allocationStatus())
            && a.orderLineId().equals(l.lineId()) && a.requisitionLineId().equals(l.requisitionLineId())
            && a.materialId().equals(l.materialId()) && a.baseUomId().toString().equals(l.unit()) && l.orderId().equals(h.orderId())
            && positive(h.vendorId()) && "0".equals(l.delFlag()) && "0".equals(h.delFlag()) && RECEIVING.contains(h.status()), "履约账本、真实订单或根单位身份不符");
        quantity(a.coveredDemandBaseQty(), true); quantity(a.fulfilledBaseQty(), true); quantity(a.occupiedBaseQty(), false);
        quantity(a.releasedBaseQty(), false); quantity(l.orderQuantity(), true); quantity(l.receivedQuantity(), true);
        need(zero(a.releasedBaseQty()) && zero(l.returnedQuantity()) && zero(l.invoicedQty()), "退货、发票或订单关闭释放仍未认证");
        equal(a.coveredDemandBaseQty(), a.occupiedBaseQty().add(a.fulfilledBaseQty()), "账本原四量不守恒");
        equal(a.coveredDemandBaseQty(), l.orderQuantity(), "原订购量与冻结继承量不一致");
        equal(a.fulfilledBaseQty(), l.receivedQuantity(), "账本履约与订单实收不一致");
        boolean full = l.receivedQuantity().compareTo(l.orderQuantity()) == 0;
        need(l.receivedQuantity().compareTo(l.orderQuantity()) <= 0
            && (full ? "FULL_RECEIVED".equals(l.receiveStatus()) : "PARTIAL_RECEIVED".equals(l.receiveStatus()))
            && (!"RECEIVED".equals(h.status()) || full), "收货计数、原行状态或全收头状态不一致");
        need(a.version() != null && a.version() >= 0 && a.createTime() != null && a.transferTime() != null
            && a.releasedAt() == null && a.releaseReason() == null && text(a.allocationKey()) && text(a.decisionIdempotencyKey()), "履约账本版本或原始来源未知");
    }

    private static void verifyEvents(String tenant, AllocationSource a, OrderHeadSource head, Map<Long,ReceiptProof> receipts,
            Map<Long,Posting> postings, List<AllocationEvent> events, Instant now, Map<String,String> versions) {
        need(!events.isEmpty() && (long)events.size() == (long)a.version() + 1, "原事件序列与账本版本未闭合");
        Set<Long> ids = new HashSet<>(); Set<String> keys = new HashSet<>(); Set<Long> matched = new HashSet<>();
        BigDecimal balance = BigDecimal.ZERO, fulfilled = BigDecimal.ZERO; String status = null; Instant previousAt = null;
        boolean transferred = false, approved = false;
        Map<String,ReceiptProof> byKey = new HashMap<>();
        receipts.values().forEach(r -> byKey.put(eventKey(tenant, a.allocationKey(), "RECEIPT_POSTED", r.receiveLineId()), r));
        for (int i = 0; i < events.size(); i++) {
            AllocationEvent e = events.get(i);
            need(e != null && positive(e.eventId()) && ids.add(e.eventId()) && text(e.eventKey()) && keys.add(e.eventKey())
                && tenant.equals(e.tenantId()) && a.allocationId().equals(e.allocationId()) && a.requisitionLineId().equals(e.requisitionLineId())
                && Integer.valueOf(i).equals(e.eventSequence()) && positive(e.actorId()) && e.occurredAt() != null, "原事件身份、序号、版本或来源重复/缺失");
            Instant at = millisInstant(e.occurredAt());
            need(!at.isAfter(now) && (previousAt == null || !at.isBefore(previousAt)), "原事件业务时间倒退或在未来");
            previousAt = at;
            quantity(e.occupiedBefore(), false); quantity(e.occupiedAfter(), false);
            equal(balance, e.occupiedBefore(), "原事件占用链前值断裂");
            need(Objects.equals(status, e.fromStatus()), "原事件状态链断裂");
            switch (e.eventType() == null ? "" : e.eventType()) {
                case "CREATE" -> {
                    need(i == 0 && INTENT.equals(e.toStatus()) && e.orderLineId() == null
                        && ("CREATE:" + a.allocationKey()).equals(e.eventKey()), "原出生事件不符");
                    equal(a.coveredDemandBaseQty(), e.occupiedAfter(), "出生覆盖量不符");
                }
                case "APPROVE" -> {
                    need(INTENT.equals(status) && INTENT.equals(e.toStatus()) && e.orderLineId() == null
                        && eventKey(tenant,a.allocationKey(),"APPROVE",head.requisitionId()).equals(e.eventKey()), "原申请批准事件不符");
                    equal(e.occupiedBefore(), e.occupiedAfter(), "批准改变占用");
                }
                case "TRANSFER_TO_ORDER" -> {
                    need(!transferred && INTENT.equals(status) && ORDER.equals(e.toStatus()) && a.orderLineId().equals(e.orderLineId())
                        && eventKey(tenant,a.allocationKey(),"TRANSFER_TO_ORDER",a.orderLineId()).equals(e.eventKey()), "原转单事件不符");
                    equal(e.occupiedBefore(), e.occupiedAfter(), "转单改变冻结继承量"); transferred = true;
                }
                case "APPROVE_ORDER" -> {
                    need(transferred && !approved && ORDER.equals(status) && ORDER.equals(e.toStatus()) && a.orderLineId().equals(e.orderLineId())
                        && eventKey(tenant,a.allocationKey(),"APPROVE_ORDER",head.orderId()).equals(e.eventKey()), "原订单批准事件不符");
                    equal(e.occupiedBefore(), e.occupiedAfter(), "订单批准改变占用"); approved = true;
                }
                case "RECEIPT_POSTED" -> {
                    ReceiptProof receipt = byKey.get(e.eventKey());
                    need(approved && ORDER.equals(status) && ORDER.equals(e.toStatus()) && a.orderLineId().equals(e.orderLineId())
                        && receipt != null && matched.add(receipt.receiveLineId()), "收货事件没有唯一真实原行");
                    Posting posting = postings.get(receipt.receiveLineId());
                    need(e.actorId().equals(posting.actorId()) && !at.isBefore(postedInstant(receipt.postedAt())), "收货事件操作人或真实过账时序不符");
                    BigDecimal delta = e.occupiedBefore().subtract(e.occupiedAfter()); quantity(delta, true);
                    equal(delta, receipt.quantity(), "收货事件占用减少与完整原行入账量不一致"); fulfilled = fulfilled.add(delta);
                }
                default -> throw reject("未知事件、退货或关闭释放仍未认证");
            }
            balance = e.occupiedAfter(); status = e.toStatus();
            need(balance.compareTo(a.coveredDemandBaseQty()) <= 0, "原事件占用超冻结覆盖量");
            versions.put("receipt-source:pu_requirement_allocation_event:" + e.eventId(), rawFingerprint(e));
        }
        need(transferred && approved && matched.equals(receipts.keySet()) && a.allocationStatus().equals(status), "收货原行、原批准事件或最终状态未全覆盖");
        equal(balance, a.occupiedBaseQty(), "末事件占用与账本不一致"); equal(fulfilled, a.fulfilledBaseQty(), "全部收货事件履约累计与账本不一致");
    }

    /** 主消费方可独立复算同一身份与逐原行证明摘要；原事件全字段摘要另纳入来源版本。 */
    public static String fulfillmentFingerprint(ReceiptFulfillmentProof proof) {
        List<String> fields = new ArrayList<>(List.of(proof.allocationId().toString(), proof.orderId().toString(),
            proof.orderLineId().toString(), proof.materialId().toString(), proof.baseUomId().toString(), normalized(proof.fulfilledBaseQty())));
        proof.receipts().stream().sorted(Comparator.comparing(ReceiptProof::receiveLineId)).forEach(r ->
            fields.addAll(List.of(r.receiveId().toString(),r.receiveLineId().toString(),normalized(r.quantity()),r.sourceFingerprint(),r.postedAt())));
        return hash(fields);
    }
    private static String eventKey(String tenant, String allocation, String operation, Long source) {
        need(positive(source), "事件业务来源身份未知"); return "LIFECYCLE:" + hash(List.of(tenant,allocation,operation,source.toString()));
    }
    private static String rawFingerprint(Object row) {
        List<String> fields = new ArrayList<>(); fields.add(row.getClass().getName());
        try { for(var field:row.getClass().getRecordComponents()) { Object value=field.getAccessor().invoke(row);fields.add(field.getName());fields.add(value==null?null:value.toString()); } }
        catch(ReflectiveOperationException failure) { throw reject("原始字段摘要不能复核"); }
        return hash(fields);
    }
    private static String hash(List<String> fields) {
        try {
            MessageDigest sha=MessageDigest.getInstance("SHA-256");
            for(String field:fields) {byte[] bytes=field==null?new byte[0]:field.getBytes(StandardCharsets.UTF_8);sha.update(ByteBuffer.allocate(4).putInt(field==null?-1:bytes.length).array());sha.update(bytes);}
            return HexFormat.of().formatHex(sha.digest());
        } catch(NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256不可用"); }
    }
    private static <T> Map<Long,T> index(List<T> rows, Function<T,Long> identity, int limit) {
        Map<Long,T> result=new TreeMap<>();for(T row:nonNull(rows,limit)) need(row!=null && positive(identity.apply(row)) && result.putIfAbsent(identity.apply(row),row)==null,"原始来源身份缺失或重复");return result;
    }
    private static <T> List<T> nonNull(List<T> rows,int limit) {need(rows!=null && rows.size()<=limit && rows.stream().allMatch(Objects::nonNull),"完整原始来源未知或超过有界范围");return rows;}
    private static BigDecimal quantity(BigDecimal q,boolean positive) {
        need(q!=null && q.signum()>=0 && (!positive || q.signum()>0) && q.compareTo(MAX)<=0,"原始数量未知、为负或超精度");
        try {return q.setScale(4,RoundingMode.UNNECESSARY);}catch(ArithmeticException failure){throw reject("原始数量不是精确DECIMAL(18,4)");}
    }
    private static Instant postedInstant(String at) {try {return LocalDateTime.parse(at,POSTED_AT).toInstant(ZoneOffset.ofHours(8));}catch(RuntimeException failure){throw reject("永久过账时点未知或不符合+08合同");}}
    /** 固定原字段为数据库毫秒；至多三位有效分数，分拆整数秒和分数纳秒避免总纳秒溢出。 */
    private static Instant millisInstant(BigDecimal at) {
        need(at != null && at.signum() > 0 && at.stripTrailingZeros().scale() <= 3,
            "数据库原毫秒时点超出固定正值与三位有效分数合同");
        try {
            BigDecimal perSecond = new BigDecimal("1000");
            long seconds = at.divideToIntegralValue(perSecond).longValueExact();
            int nanos = at.subtract(BigDecimal.valueOf(seconds).multiply(perSecond)).movePointRight(6).intValueExact();
            return Instant.ofEpochSecond(seconds, nanos);
        } catch (ArithmeticException | java.time.DateTimeException failure) { throw reject("数据库原毫秒整数或分数精度不支持"); }
    }
    private static Instant secondsInstant(BigDecimal at) {
        need(at != null && at.signum() > 0 && at.scale() <= 6, "数据库时钟超出固定正值与六位精度合同");
        try {
            long seconds = at.divideToIntegralValue(BigDecimal.ONE).longValueExact();
            int nanos = at.subtract(BigDecimal.valueOf(seconds)).movePointRight(9).intValueExact();
            return Instant.ofEpochSecond(seconds, nanos);
        } catch (ArithmeticException | java.time.DateTimeException failure) { throw reject("数据库时钟整数或分数精度不支持"); }
    }
    private static String normalized(BigDecimal q) {return q.stripTrailingZeros().toPlainString();}
    private static boolean positive(Long id) {return id!=null && id>0;}
    private static boolean zero(BigDecimal q) {return q!=null && q.signum()==0;}
    private static boolean text(String s) {return s!=null && !s.isBlank() && s.equals(s.strip());}
    private static boolean digest(String s) {return s!=null && s.matches("[0-9a-f]{64}");}
    private static void equal(BigDecimal a,BigDecimal b,String why) {need(a!=null && b!=null && a.compareTo(b)==0,why);}
    private static <T> T query(Supplier<T> action) {try{return action.get();}catch(RuntimeException failure){throw reject("固定原始来源读取或永久重放失败");}}
    private static void need(boolean condition,String why) {if(!condition) throw reject(why);}
    private static ServiceException reject(String why) {return new ServiceException("采购收货履约来源未核验："+why);}
    public record ReceiptSourceSnapshot(ReceiptFulfillmentProof proof, Map<String,String> sourceVersions) {
        public ReceiptSourceSnapshot { sourceVersions = Map.copyOf(sourceVersions); }
    }
}
