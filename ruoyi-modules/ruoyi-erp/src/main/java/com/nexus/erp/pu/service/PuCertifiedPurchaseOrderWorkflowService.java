package com.nexus.erp.pu.service;

import static com.nexus.erp.pu.service.support.PuCertifiedPurchaseLifecycleSupport.*;

import cn.dev33.satoken.stp.StpUtil;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService;
import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import com.nexus.erp.pu.mapper.*;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper.*;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseOrderWorkflowMapper.*;
import com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.stereotype.Service;

import java.math.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 真实整订单：供应确认条款不可变版本、不同操作人独立确认、价格/批准/关闭及永久重放。
 * public写入口不能加@Transactional/@DS；ERP独占可重复读事务由executor持有，不参与master AutoCode/Flowable。
 * 报价来自授权采购人员对供应确认文档的持久业务确认，不接受AI/外部verified或客户端数量。
 * 仅根基本单位、CNY/汇率1、无折扣运费；发票/会计是独立域，不在此证明。
 */
@Service
public class PuCertifiedPurchaseOrderWorkflowService {
    private static final String ROUNDING="LINE_NET_TAX_HALF_UP_2_GROSS_UNIT_HALF_UP_4";
    private static final Set<String> RECEIVING=Set.of("APPROVED","PARTIALLY_RECEIVED","PARTIAL_RECEIVED","RECEIVED");
    public record SupplierTermsRequest(@NotBlank String requestKey,@NotNull @PositiveOrZero Integer expectedOrderVersion,
        @NotNull BigDecimal unitPriceExcludingTax,@NotNull BigDecimal taxRate,@NotBlank String currency,@NotNull BigDecimal exchangeRate,
        @NotNull LocalDate promisedArrivalDate,@NotNull LocalDate validFrom,@NotNull LocalDate validUntil,
        @NotBlank String supplierQuoteReference,@NotBlank String supplierContact,@NotBlank String evidenceReference,@NotBlank String evidenceSha256,
        @NotBlank String paymentTerms,@NotBlank String deliveryTerms) { }
    public record QuoteResult(Quote quote,Confirmation confirmation,boolean replayed) { }

    /** 原请求核对方式；必须显式选择，只读核对绝不默认升级为写入。 */
    public enum TermsRecoveryMode {
        /** 仅核对永久原键及当前合法性，不创建报价。 */
        CHECK_ONLY,
        /** 无原提交时，经全部正常门禁核对后在当前事务录入一次。 */
        SETTLE_IF_ABSENT
    }

    /** 本次处置结果；缺行不等于证明历史请求已回滚。 */
    public enum TermsRecoveryStatus {
        /** 当前无原报价，正常写入门禁已通过，但本次没有写入。 */
        ABSENT_REVALIDATED,
        /** 原键已有完整永久提交，本次仅读回原提交结果。 */
        EXISTING_COMMIT,
        /** 本次核对后新录入原键报价，不声明历史未知请求成功。 */
        RECORDED_AFTER_RECONCILIATION
    }

    /**
     * 原报价请求的显式处置合同。
     *
     * <p>摘要是调用方保留的审计线索，不是历史提交证明，也不代替当前权限、来源或额度校验。 原操作人必须等于当前登录主体，原报价全文仍按正常报价规则验证。
     *
     * @param originalTerms 原请求全部条款，沿用原请求键，不接受替代报价或客户端数量
     * @param originalActorId 原操作人断言，只能与真实登录主体相同
     * @param originalIntentSha256 原意图文件摘要，仅作为审计引用
     * @param expectedRequestHash 原报价逻辑合同摘要，服务端按全部字段重新计算
     * @param reason 本次人工核对处置原因
     * @param mode 显式只读核对或核对后录入模式
     */
    public record TermsRecoveryRequest(
            @NotNull @Valid SupplierTermsRequest originalTerms,
            @NotNull @Positive Long originalActorId,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String originalIntentSha256,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String expectedRequestHash,
            @NotBlank @Size(max = 500) String reason,
            @NotNull TermsRecoveryMode mode) {}

    /** 本次处置状态与不可变报价；只读缺行时 result 为空。 */
    public record TermsRecoveryResult(TermsRecoveryStatus status, QuoteResult result,
        String originalIntentSha256, String expectedRequestHash, Long actorId) { }
    public record Result(Long orderId,String orderNo,String requestKey,String status,Integer version,Long quoteId,String quoteHash,
        String expectedArrivalDate,BigDecimal totalAmount,BigDecimal amountExcludingTax,BigDecimal taxAmount,boolean replayed,List<EventReceipt> allocations) {
        public Result {allocations=List.copyOf(allocations);}
    }
    private final PuRequirementErpTransactionExecutor executor;
    private final PuRequirementErpTransactionQualification qualification;
    private final PuRequirementMaterialLockService locks;
    private final PuCertifiedPurchaseOrderWorkflowMapper mapper;
    private final PuCertifiedPurchaseLifecycleMapper sources;
    private final PuCertifiedPurchaseLifecycleService lifecycle;
    private final ErpPurchaseSourceCertificationService certification;
    private final PuCertifiedPurchaseLifecycleSourceGate sourceGate;
    public PuCertifiedPurchaseOrderWorkflowService(PuRequirementErpTransactionExecutor executor,PuRequirementErpTransactionQualification qualification,
        PuRequirementMaterialLockService locks,PuCertifiedPurchaseOrderWorkflowMapper mapper,PuCertifiedPurchaseLifecycleMapper sources,
        PuCertifiedPurchaseLifecycleService lifecycle,ErpPurchaseSourceCertificationService certification) {
        this.executor=Objects.requireNonNull(executor);this.qualification=Objects.requireNonNull(qualification);this.locks=Objects.requireNonNull(locks);
        this.mapper=Objects.requireNonNull(mapper);this.sources=Objects.requireNonNull(sources);this.lifecycle=Objects.requireNonNull(lifecycle);
        this.certification=Objects.requireNonNull(certification);this.sourceGate=new PuCertifiedPurchaseLifecycleSourceGate(sources,qualification);
    }
    /** 页面主体提示；query与写权限独立，实际写操作仍复用原actor、来源和事务守卫。 */
    public record WorkflowContext(String tenantId, Long userId, boolean termsAddAllowed,
        boolean termsConfirmAllowed, boolean approveAllowed, boolean closeAllowed,
        boolean cancelAllowed, boolean priceVisible) { }

    /**
     * 读取真实登录主体及按钮权限，不获取ERP连接，也不对采购来源签发认证。
     *
     * @return 当前主体和各操作提示；审批仍同时要求批准及价格权限
     */
    public WorkflowContext currentWorkflowContext() {
        Actor actor = actor("pu:order:query");
        boolean add = StpUtil.hasPermission("pu:order:terms:add");
        boolean confirm = StpUtil.hasPermission("pu:order:terms:confirm");
        boolean price = StpUtil.hasPermission("pu:order:price");
        return new WorkflowContext(actor.tenantId(), actor.userId(), add, confirm,
            StpUtil.hasPermission("pu:order:approve") && price,
            StpUtil.hasPermission("pu:order:close"), StpUtil.hasPermission("pu:order:cancel"),
            add || confirm || price);
    }

    /** 录入不等于批准：人工提供的完整条款以不可变记录保存，并绑定 ERP 读取的数量。 */
    public QuoteResult recordTerms(Long id,SupplierTermsRequest request) {
        Actor actor=actor("pu:order:terms:add");validateTerms(id,request);String requestHash=termsRequestHash(actor,id,request);
        return executor.executeRepeatableRead(actor.tenantId(),()->{
            recheckActor(actor);Document d=load(actor.tenantId(),id);Quote old=io(()->mapper.lockQuoteRequest(actor.tenantId(),request.requestKey()));
            if(old!=null) {
                require(old.orderId().equals(id)&&old.enteredBy().equals(actor.userId())&&old.requestHash().equals(requestHash),"报价永久请求键/actor/全字段合同冲突");
                requireQuote(d,old,false);return new QuoteResult(old,io(()->mapper.lockConfirmation(actor.tenantId(),old.quoteId())),true);
            }
            return recordTermsLocked(d,actor,id,request,requestHash,false);
        });
    }
    /**
     * 显式核对原报价未知结果，并按指定模式处置。
     * <p>既有提交只读返回；缺行仅表示当前未发现提交。核对后录入必须在原有 ERP 独占可重复读
     * 事务和既有物料锁内通过完整正常门禁，使用原键创建当前时点报价，不能覆盖任何其他报价版本。
     * 本入口不会创建缺失 guard，不改订单状态，也不以文件摘要授予权限或证明历史结果。</p>
     *
     * @param id 原可信采购订单 ID
     * @param request 保留原操作人、原键及完整条款的显式处置合同
     * @return 区分只读缺行、既有提交和本次新录入的结果
     */
    public TermsRecoveryResult reconcileTerms(Long id, TermsRecoveryRequest request) {
        Actor actor = actor("pu:order:terms:add");
        StpUtil.checkPermission("pu:order:query");
        require(request != null && request.mode() != null && actor.userId().equals(request.originalActorId())
            && digest(request.originalIntentSha256()) && digest(request.expectedRequestHash())
            && text(request.reason(),500), "原请求处置模式/主体/摘要/审计原因无效");
        SupplierTermsRequest terms = request.originalTerms();
        validateTerms(id,terms);
        String requestHash = termsRequestHash(actor,id,terms);
        require(requestHash.equals(request.expectedRequestHash()), "原报价全文与冻结逻辑合同摘要不符");
        return executor.executeRepeatableRead(actor.tenantId(), () -> {
            recheckActor(actor);
            StpUtil.checkPermission("pu:order:query");
            Document d = load(actor.tenantId(),id,true);
            Quote old = io(() -> mapper.lockQuoteRequest(actor.tenantId(),terms.requestKey()));
            if (old != null) {
                require(id.equals(old.orderId()) && actor.userId().equals(old.enteredBy())
                    && requestHash.equals(old.requestHash()), "原报价永久请求键/actor/全字段合同冲突");
                requireQuote(d,old,false);
                SupplierTermsRequest stored = new SupplierTermsRequest(old.requestKey(),terms.expectedOrderVersion(),
                    old.unitPriceExcludingTax(),old.taxRate(),old.currency(),old.exchangeRate(),date(old.promisedArrivalDate()),
                    date(old.validFrom()),date(old.validUntil()),old.supplierQuoteReference(),old.supplierContact(),
                    old.evidenceReference(),old.evidenceSha256(),old.paymentTerms(),old.deliveryTerms());
                require(requestHash.equals(termsRequestHash(actor,id,stored)), "原报价持久全文与原逻辑摘要冲突");
                require(quoteVersions(actor.tenantId(),id).contains(old.quoteVersion()), "原报价不在完整永久版本序列中");
                Confirmation confirmation = io(() -> mapper.lockConfirmation(actor.tenantId(),old.quoteId()));
                if (confirmation != null) requireConfirmation(old,confirmation);
                return recoveryResult(request,actor,TermsRecoveryStatus.EXISTING_COMMIT,
                    new QuoteResult(old,confirmation,true));
            }
            if (request.mode() == TermsRecoveryMode.CHECK_ONLY) {
                validateNewTermsLocked(d,actor,terms,true);
                require(quoteVersions(actor.tenantId(),id).isEmpty(), "原键缺行但已有其他报价，禁止替代原未知请求");
                validateRequestedAmounts(d,terms);
                qualification.requireSourceChainTransaction();
                return recoveryResult(request,actor,TermsRecoveryStatus.ABSENT_REVALIDATED,null);
            }
            QuoteResult result = recordTermsLocked(d,actor,id,terms,requestHash,true);
            return recoveryResult(request,actor,TermsRecoveryStatus.RECORDED_AFTER_RECONCILIATION,result);
        });
    }

    private static TermsRecoveryResult recoveryResult(TermsRecoveryRequest request, Actor actor,
        TermsRecoveryStatus status, QuoteResult result) {
        return new TermsRecoveryResult(status,result,request.originalIntentSha256(),request.expectedRequestHash(),actor.userId());
    }

    /** 正常报价及显式处置共用同事务门禁，禁止调用公开方法另起事务。 */
    private void validateNewTermsLocked(Document d, Actor actor, SupplierTermsRequest request, boolean existingGuardOnly) {
        requireDraft(d,request.expectedOrderVersion());
        OrderCommand command = orderCommand(d,actor,Operation.APPROVE_ORDER);
        if (existingGuardOnly) lifecycle.validateOrderForWorkflowExistingGuardLocked(command);
        else lifecycle.validateOrderForWorkflowLocked(command);
        requireCurrentCapacityAndCatalog(d,existingGuardOnly);
        requireDates(request.promisedArrivalDate(),request.validFrom(),request.validUntil(),databaseDate());
    }

    /** 在已锁定的原订单内保存不可变报价，不修改订单及价税事实。 */
    private QuoteResult recordTermsLocked(Document d, Actor actor, Long id, SupplierTermsRequest request,
        String requestHash, boolean requireFirstVersion) {
        validateNewTermsLocked(d,actor,request,requireFirstVersion);
        List<Integer> versions = quoteVersions(actor.tenantId(),id);
        require(!requireFirstVersion || versions.isEmpty(), "原键缺行但已有其他报价，禁止替代原未知请求");
        Date now = databaseNow();
        Quote q = new Quote(IdWorker.getId(),actor.tenantId(),id,versions.size()+1,request.requestKey(),requestHash,actor.userId(),now,
            d.head().vendorId(),d.source().materialId(),d.rows().get(0).getBaseUomId(),d.source().requestKey(),d.source().sourceFingerprint(),d.source().catalogFingerprint(),
            orderFingerprint(d),totalQuantity(d),request.unitPriceExcludingTax(),request.taxRate(),request.currency(),request.exchangeRate(),request.promisedArrivalDate().toString(),
            request.validFrom().toString(),request.validUntil().toString(),request.supplierQuoteReference(),request.supplierContact(),request.evidenceReference(),request.evidenceSha256(),
            request.paymentTerms(),request.deliveryTerms(),ROUNDING,null);
        Quote frozen = withQuoteHash(q,quoteHash(q));
        calculatePrices(d,frozen);
        require(io(() -> mapper.insertQuote(frozen)) == 1, "不可变报价版本写入失败");
        qualification.requireSourceChainTransaction();
        return new QuoteResult(frozen,null,false);
    }

    /** 由另一名已授权操作人核验精确的持久化哈希；不对报价/审计记录执行 UPDATE。 */
    public QuoteResult confirmTerms(Long id,Long quoteId,String key,String expectedQuoteHash,String reviewNote) {
        Actor actor=actor("pu:order:terms:confirm");validateId(id);require(positive(quoteId)&&requestKey(key)&&digest(expectedQuoteHash)&&text(reviewNote,500),"独立报价确认合同无效");
        String requestHash=confirmationHash(actor.tenantId(),actor.userId(),id,quoteId,key,expectedQuoteHash,reviewNote);
        return executor.executeRepeatableRead(actor.tenantId(),()->{
            recheckActor(actor);Document d=load(actor.tenantId(),id);Quote q=io(()->mapper.lockQuote(actor.tenantId(),quoteId));requireQuote(d,q,false);
            require(expectedQuoteHash.equals(q.quoteHash())&&!actor.userId().equals(q.enteredBy()),"报价摘要变化或录入actor不能独立自批");
            Confirmation old=io(()->mapper.lockConfirmationRequest(actor.tenantId(),key));
            if(old!=null) {
                require(old.quoteId().equals(quoteId)&&old.actorId().equals(actor.userId())&&requestHash.equals(old.requestHash()),"确认永久请求键/actor/审查合同冲突");
                requireConfirmation(q,old);return new QuoteResult(q,old,true);
            }
            require("DRAFT".equals(d.head().status())&&d.head().quoteId()==null,"已批准/终态订单不能确认新报价");
            require(io(()->mapper.lockConfirmation(actor.tenantId(),quoteId))==null,"该报价已有独立确认，不能覆盖审计");
            requireQuote(d,q,true);requireLatestQuote(q);requireCurrentCapacityAndCatalog(d);
            lifecycle.validateOrderForWorkflowLocked(orderCommand(d,actor,Operation.APPROVE_ORDER));Date now=databaseNow();require(!now.before(q.enteredAt()),"确认时钟早于报价录入");
            Confirmation confirmation=new Confirmation(IdWorker.getId(),actor.tenantId(),quoteId,q.quoteHash(),key,requestHash,actor.userId(),actor.permission(),now,reviewNote);
            require(io(()->mapper.insertConfirmation(confirmation))==1,"独立确认审计写入失败");qualification.requireSourceChainTransaction();return new QuoteResult(q,confirmation,false);
        });
    }
    public Result approve(Long id,Integer version,Long quoteId,String key) {
        Actor actor=actor("pu:order:approve");StpUtil.checkPermission("pu:order:price");validateVersion(id,version,key);require(positive(quoteId),"批准必须引用真实已确认报价ID");
        return change(actor,id,version,quoteId,key,Operation.APPROVE_ORDER);
    }
    public Result close(Long id,Integer version,String key){Actor a=actor("pu:order:close");validateVersion(id,version,key);return change(a,id,version,null,key,Operation.CLOSE_ORDER);}
    public Result cancel(Long id,Integer version,String key){Actor a=actor("pu:order:cancel");validateVersion(id,version,key);return change(a,id,version,null,key,Operation.CANCEL_ORDER);}
    /**
     * 物理结果生成方门禁：加入已有的ERP独占可重复读事务/物料守卫锁，绝不自行启动事务。
     * 批准后报价的采购有效期届满，不会使已冻结的供应商义务失效。
     * 要求原始批准回执/完整占用事件，不能仅凭 APPROVED 状态与正价格判定。
     */
    public void requireApprovedLocked(String tenantId,Long orderId) {
        requireTenant(tenantId);qualification.requireSourceChainTransaction();validateId(orderId);Document d=load(tenantId,orderId,true);
        require(RECEIVING.contains(d.head().status()),"订单不在实际接收状态");verifyApproved(d,false);qualification.requireSourceChainTransaction();
    }
    /** 仅用于恢复永久过账结果；绝不授权对终态订单执行新的物理写入。 */
    public void requireApprovedReplayLocked(String tenantId,Long orderId) {
        requireTenant(tenantId);qualification.requireSourceChainTransaction();validateId(orderId);Document d=load(tenantId,orderId,true);
        if(RECEIVING.contains(d.head().status())){verifyApproved(d,false);return;}
        require(Set.of("CLOSED","CANCELLED").contains(d.head().status()),"永久posting恢复订单状态不支持");Quote q=verifyApproved(d,true);
        Operation op="CLOSED".equals(d.head().status())?Operation.CLOSE_ORDER:Operation.CANCEL_ORDER;
        OperationRecord terminal=io(()->mapper.lockTerminalOperation(tenantId,orderId));
        require(terminal!=null&&positive(terminal.commandId())&&tenantId.equals(terminal.tenantId())&&orderId.equals(terminal.orderId())&&positive(terminal.actorId())&&op.name().equals(terminal.operation())
            &&d.head().status().equals(terminal.resultStatus())&&terminal.expectedVersion()!=null&&terminal.expectedVersion()>0&&terminal.expectedVersion()<Integer.MAX_VALUE
            &&Integer.valueOf(1).equals(terminal.expectedVersion())&&Integer.valueOf(2).equals(terminal.resultVersion())
            &&d.head().version().equals(terminal.resultVersion())&&terminal.resultVersion()==terminal.expectedVersion()+1&&q.quoteId().equals(terminal.quoteId())&&q.quoteHash().equals(terminal.quoteHash())
            &&q.promisedArrivalDate().equals(terminal.expectedArrivalDate())&&equal(d.head().totalAmount(),terminal.totalAmount())&&equal(d.head().amountExcludingTax(),terminal.amountExcludingTax())&&equal(d.head().taxAmount(),terminal.taxAmount())
            &&requestKey(terminal.requestKey())&&digest(terminal.resultFingerprint())&&Objects.equals(terminal.requestHash(),operationHash(new Actor(tenantId,terminal.actorId(),""),orderId,terminal.expectedVersion(),null,terminal.requestKey(),op))
            &&terminal.occurredAt()!=null&&!terminal.occurredAt().after(databaseNow()),"终态缺少与原报价金额一致的ERP永久关闭结果");
        List<EventReceipt> events=lifecycle.settledOrderEventsLocked(tenantId,d.source().materialId(),d.source().requestKey(),orderId);
        require(events!=null&&events.size()==d.rows().size()&&events.stream().allMatch(e->e.operation()==op&&terminal.actorId().equals(e.actorId())),"终态physical/ledger未完整对齐");
        Result original=new Result(orderId,d.head().orderNo(),terminal.requestKey(),terminal.resultStatus(),terminal.resultVersion(),terminal.quoteId(),terminal.quoteHash(),terminal.expectedArrivalDate(),terminal.totalAmount(),terminal.amountExcludingTax(),terminal.taxAmount(),true,events);
        require(terminal.resultFingerprint().equals(resultFingerprint(tenantId,original)),"终态ERP结果与全部释放/履约事件不一致");qualification.requireSourceChainTransaction();
    }
    /** 未批准草稿/终态只读门禁；完整金额行读取不能被空报价头代替。 */
    public void requireUnpricedReplayLocked(String tenantId,Long orderId) {
        requireTenant(tenantId);qualification.requireSourceChainTransaction();validateId(orderId);Document d=load(tenantId,orderId,true);
        require(Set.of("DRAFT","CLOSED","CANCELLED").contains(d.head().status()),"未定价原创建订单状态不支持");
        require("CNY".equals(d.head().currency())&&equal(BigDecimal.ONE,d.head().exchangeRate()),"未定价原始币种/汇率不一致");
        requireUnpricedTermsAndMoney(d,true);qualification.requireSourceChainTransaction();
    }

    private Result change(Actor actor,Long id,Integer version,Long quoteId,String key,Operation operation) {
        String requestHash=operationHash(actor,id,version,quoteId,key,operation);
        return executor.executeRepeatableRead(actor.tenantId(),()->{
            recheckActor(actor);if(operation==Operation.APPROVE_ORDER)StpUtil.checkPermission("pu:order:price");
            Document d=load(actor.tenantId(),id);OperationRecord old=io(()->mapper.lockOperation(actor.tenantId(),key));
            if(old!=null)return replay(d,actor,old,requestHash,operation);
            require(d.head().version().equals(version),"真实订单头版本变化");
            String next=operation==Operation.APPROVE_ORDER?"APPROVED":operation==Operation.CLOSE_ORDER?"CLOSED":"CANCELLED";
            require(operation==Operation.APPROVE_ORDER?"DRAFT".equals(d.head().status()):("DRAFT".equals(d.head().status())||RECEIVING.contains(d.head().status())),"真实整订单状态转换不支持");
            Quote q=null;Totals total=null;
            if(operation==Operation.APPROVE_ORDER) {
                requireDraft(d,version);q=io(()->mapper.lockQuote(actor.tenantId(),quoteId));requireQuote(d,q,true);requireLatestQuote(q);
                requireConfirmation(q,io(()->mapper.lockConfirmation(actor.tenantId(),quoteId)));lifecycle.validateOrderForWorkflowLocked(orderCommand(d,actor,operation));
                // 在同一ERP独占可重复读事务中，将完整全局池/当前目录与冻结命令进行核对。
                requireCurrentCapacityAndCatalog(d);Map<Long,LinePrice> prices=calculatePrices(d,q);total=totals(prices.values());Quote approved=q;Totals amounts=total;
                require(io(()->mapper.approveHead(actor.tenantId(),d.head(),approved,amounts,actor.userId()))==1,"整订单批准头状态/版本CAS失败");
                for(OrderLine line:d.lines())require(io(()->mapper.priceLine(actor.tenantId(),line,prices.get(line.lineId()),actor.userId()))==1,"订单行金额/冻结数量CAS失败");
            } else {
                if("DRAFT".equals(d.head().status()))requireDraft(d,version);else {q=verifyApproved(d,true);total=calculateTotals(d,q);}
                lifecycle.validateOrderForWorkflowLocked(orderCommand(d,actor,operation));
                require(io(()->mapper.closeHead(actor.tenantId(),d.head(),next,actor.userId()))==1,"整订单关闭/取消头状态版本CAS失败");
            }
            OrderReceipt receipt=lifecycle.applyOrderForWorkflowLocked(orderCommand(d,actor,operation));
            require(!receipt.replayed()&&receipt.orderId().equals(id)&&receipt.operation()==operation&&receipt.allocations().size()==d.rows().size(),"新订单转换没有完整新逐来源事件");
            Result result=result(d,key,next,version+1,q,total,receipt.allocations(),false);
            OperationRecord frozen=new OperationRecord(IdWorker.getId(),actor.tenantId(),id,key,requestHash,actor.userId(),operation.name(),version,version+1,next,
                result.quoteId(),result.quoteHash(),result.expectedArrivalDate(),result.totalAmount(),result.amountExcludingTax(),result.taxAmount(),resultFingerprint(actor.tenantId(),result),databaseNow());
            require(io(()->mapper.insertOperation(frozen))==1,"ERP结果永久重放合同写入失败");qualification.requireSourceChainTransaction();return result;
        });
    }
    private Result replay(Document d,Actor actor,OperationRecord old,String requestHash,Operation operation) {
        require(d.head().tenantId().equals(old.tenantId())&&d.head().orderId().equals(old.orderId())&&actor.userId().equals(old.actorId())&&operation.name().equals(old.operation())
            &&requestHash.equals(old.requestHash())&&positive(old.commandId())&&digest(old.resultFingerprint())&&old.expectedVersion()!=null&&old.expectedVersion()>=0&&old.expectedVersion()<Integer.MAX_VALUE
            &&old.resultVersion()!=null&&old.resultVersion()==old.expectedVersion()+1&&d.head().version()>=old.resultVersion()&&old.occurredAt()!=null&&!old.occurredAt().after(databaseNow()),"订单永久请求/actor/原始结果合同冲突");
        String state=operation==Operation.APPROVE_ORDER?"APPROVED":operation==Operation.CLOSE_ORDER?"CLOSED":"CANCELLED";require(state.equals(old.resultStatus()),"原始操作结果状态不符");
        if(old.quoteId()!=null) {
            Quote q=io(()->mapper.lockQuote(actor.tenantId(),old.quoteId()));requireQuote(d,q,false);require(Objects.equals(old.quoteHash(),q.quoteHash()),"原始结果报价引用变化");
            requireConfirmation(q,io(()->mapper.lockConfirmation(actor.tenantId(),old.quoteId())));
        }else require(old.quoteHash()==null&&old.expectedArrivalDate()==null&&old.totalAmount()==null&&old.amountExcludingTax()==null&&old.taxAmount()==null,"未定价草稿原始结果不能构造金额/ETA");
        OrderReceipt receipt=lifecycle.applyOrderForWorkflowLocked(orderCommand(d,actor,operation));
        require(receipt.replayed()&&receipt.orderId().equals(d.head().orderId())&&receipt.operation()==operation&&receipt.allocations().size()==d.rows().size(),"原始逐来源事件缺失");
        Result result=new Result(old.orderId(),d.head().orderNo(),old.requestKey(),old.resultStatus(),old.resultVersion(),old.quoteId(),old.quoteHash(),old.expectedArrivalDate(),
            old.totalAmount(),old.amountExcludingTax(),old.taxAmount(),true,receipt.allocations());
        require(old.resultFingerprint().equals(resultFingerprint(actor.tenantId(),result)),"原始结果/完整永久事件摘要不符");qualification.requireSourceChainTransaction();return result;
    }
    /** 重新核验报价、独立确认、全部原生金额、批准回执及承接的历史事件。 */
    private Quote verifyApproved(Document d,boolean allowTerminal) {
        require(RECEIVING.contains(d.head().status())||(allowTerminal&&Set.of("CLOSED","CANCELLED").contains(d.head().status())),"实际已批准订单状态不支持");
        require(positive(d.head().quoteId())&&d.head().version()>0,"缺少不可变报价引用/批准版本");
        Quote q=io(()->mapper.lockQuote(d.head().tenantId(),d.head().quoteId()));requireQuote(d,q,false);
        Confirmation confirmation=io(()->mapper.lockConfirmation(d.head().tenantId(),q.quoteId()));requireConfirmation(q,confirmation);
        Map<Long,LinePrice> prices=calculatePrices(d,q);Totals total=totals(prices.values());
        require(q.currency().equals(d.head().currency())&&equal(q.exchangeRate(),d.head().exchangeRate())&&q.promisedArrivalDate().equals(d.head().expectedArrivalDate())
            &&q.paymentTerms().equals(d.head().paymentTerms())&&q.deliveryTerms().equals(d.head().deliveryTerms())
            &&equal(total.gross(),d.head().totalAmount())&&equal(total.net(),d.head().amountExcludingTax())&&equal(total.tax(),d.head().taxAmount())
            &&zero(d.head().discountAmount())&&zero(d.head().freightAmount())&&zero(d.head().discountRate()),"订单头金额/税额/ETA/币种与已确认报价不一致");
        for(LineMoney line:d.money()) {
            LinePrice price=prices.get(line.lineId());require(price!=null&&equal(price.netUnit(),line.unitPriceExcludingTax())&&equal(price.grossUnit(),line.unitPrice())&&equal(price.rate(),line.taxRate())
                &&equal(price.net(),line.lineAmountExcludingTax())&&equal(price.tax(),line.lineTaxAmount())&&equal(price.gross(),line.lineAmount()),"真实全订单行定价与已确认报价不一致");
        }
        OperationRecord op=io(()->mapper.lockApprovalOperation(d.head().tenantId(),d.head().orderId()));
        require(op!=null&&positive(op.commandId())&&d.head().tenantId().equals(op.tenantId())&&d.head().orderId().equals(op.orderId())&&positive(op.actorId())&&"APPROVE_ORDER".equals(op.operation())&&"APPROVED".equals(op.resultStatus())
            &&Integer.valueOf(0).equals(op.expectedVersion())&&Integer.valueOf(1).equals(op.resultVersion())
            &&(allowTerminal?Integer.valueOf(1).equals(d.head().version())||Integer.valueOf(2).equals(d.head().version()):d.head().version().equals(op.resultVersion()))&&q.quoteId().equals(op.quoteId())&&q.quoteHash().equals(op.quoteHash())&&q.promisedArrivalDate().equals(op.expectedArrivalDate())
            &&equal(total.gross(),op.totalAmount())&&equal(total.net(),op.amountExcludingTax())&&equal(total.tax(),op.taxAmount())&&digest(op.resultFingerprint())&&requestKey(op.requestKey())
            &&Objects.equals(op.requestHash(),operationHash(new Actor(op.tenantId(),op.actorId(),"pu:order:approve"),op.orderId(),op.expectedVersion(),q.quoteId(),op.requestKey(),Operation.APPROVE_ORDER))
            &&op.occurredAt()!=null&&!op.occurredAt().before(confirmation.confirmedAt())&&!op.occurredAt().after(databaseNow()),"缺少真实ERP定价批准永久提交合同");
        List<EventReceipt> events=lifecycle.approvedOrderEventsLocked(d.head().tenantId(),d.source().materialId(),d.source().requestKey(),d.head().orderId());
        require(events!=null&&events.size()==d.rows().size()&&events.stream().allMatch(e->e.operation()==Operation.APPROVE_ORDER&&op.actorId().equals(e.actorId())),"缺少完整原始批准事件");
        Result original=new Result(op.orderId(),d.head().orderNo(),op.requestKey(),op.resultStatus(),op.resultVersion(),op.quoteId(),op.quoteHash(),op.expectedArrivalDate(),op.totalAmount(),op.amountExcludingTax(),op.taxAmount(),true,events);
        require(op.resultFingerprint().equals(resultFingerprint(d.head().tenantId(),original)),"批准结果及全来源事件摘要不符");return q;
    }
    private Document load(String tenant,Long id) { return load(tenant,id,false); }
    /** 内部已提交结果校验只锁既有guard；定价/批准等写入口保留原ensure分支。 */
    private Document load(String tenant,Long id,boolean existingGuardOnly) {
        requireTenant(tenant);qualification.requireSourceChainTransaction();Long material=io(()->mapper.findMaterial(tenant,id));require(positive(material),"订单缺少永久可信command，历史单据不可升级");
        Map<Long,Integer> guard=io(()->existingGuardOnly?locks.lockExisting(tenant,List.of(material)):locks.lock(tenant,List.of(material)));require(guard!=null&&guard.size()==1&&guard.get(material)!=null&&guard.get(material)>=0&&guard.get(material)<Integer.MAX_VALUE,"物料guard版本未知");
        sourceGate.requireOrderDefinitions();Head head=io(()->mapper.lockHead(tenant,id));
        require(head!=null&&tenant.equals(head.tenantId())&&id.equals(head.orderId())&&text(head.orderNo(),64)&&positive(head.requisitionId())&&positive(head.vendorId())
            &&head.version()!=null&&head.version()>=0&&head.version()<Integer.MAX_VALUE&&"0".equals(head.delFlag())&&head.selectFlowId()==null,"真实订单头/版本/租户/直接ERP审批范围未知");
        RequisitionHead req=io(()->sources.lockRequisitionHead(tenant,head.requisitionId()));
        require(req!=null&&tenant.equals(req.tenantId())&&head.requisitionId().equals(req.requisitionId())&&"ORDERED".equals(req.status())&&"0".equals(req.delFlag())&&text(req.requestKey(),128)&&text(req.requisitionNo(),64),"订单不是已认证整申请的真实转单");
        CommandSource command=io(()->sources.lockCommand(tenant,req.requestKey()));
        require(command!=null&&tenant.equals(command.tenantId())&&req.requestKey().equals(command.requestKey())&&req.requisitionId().equals(command.requisitionId())
            &&req.requisitionNo().equals(command.requisitionNo())&&Objects.equals(req.sourceType(),command.entryType())&&material.equals(command.materialId())&&head.vendorId().equals(command.vendorId())
            &&positive(command.actorId())&&"COMMITTED".equals(command.commandStatus())&&digest(command.requestHash())&&digest(command.sourceFingerprint())&&digest(command.resultFingerprint())&&digest(command.catalogFingerprint())&&text(command.windowEnd(),10),"永久command/供应商/冻结来源合同未知");
        List<RequisitionLine> reqLines=io(()->sources.lockRequisitionLines(tenant,head.requisitionId()));List<OrderLine> lines=io(()->sources.lockOrderLines(tenant,id));
        List<PuRequirementAllocationLedger> rows=io(()->sources.lockCommandAllocations(tenant,command.requestKey())),orderRows=io(()->sources.lockOrderAllocations(tenant,id));List<LineMoney> money=io(()->mapper.lockMoneyLines(tenant,id));
        require(lines!=null&&!lines.isEmpty()&&lines.size()<=1000&&reqLines!=null&&rows!=null&&orderRows!=null&&money!=null&&lines.size()==reqLines.size()&&lines.size()==rows.size()&&lines.size()==orderRows.size()&&lines.size()==money.size()
            &&lines.stream().allMatch(Objects::nonNull)&&reqLines.stream().allMatch(Objects::nonNull)&&rows.stream().allMatch(Objects::nonNull)&&orderRows.stream().allMatch(Objects::nonNull)&&money.stream().allMatch(Objects::nonNull),"整订单/申请/账本/金额来源未全覆盖");
        Map<Long,RequisitionLine> reqById=new HashMap<>();Set<Long> details=new HashSet<>();Map<Long,OrderLine> byReq=new HashMap<>();Set<Long> lineIds=new HashSet<>();Set<String> keys=new HashSet<>();Set<Long> allocationIds=new HashSet<>();
        for(var r:reqLines)require(tenant.equals(r.tenantId())&&head.requisitionId().equals(r.requisitionId())&&material.equals(r.materialId())&&positive(r.lineId())&&reqById.putIfAbsent(r.lineId(),r)==null
            &&"MRP".equals(r.sourceType())&&positive(r.sourceId())&&details.add(r.sourceId())&&"0".equals(r.delFlag())&&decimal(r.applyQuantity())&&r.applyQuantity().signum()>0&&equal(r.applyQuantity(),r.approvedQuantity()),"真实申请逐来源冻结量未知");
        for(var line:lines) {
            var r=reqById.get(line.requisitionLineId());require(r!=null&&tenant.equals(line.tenantId())&&id.equals(line.orderId())&&material.equals(line.materialId())&&positive(line.lineId())&&lineIds.add(line.lineId())
                &&byReq.putIfAbsent(line.requisitionLineId(),line)==null&&Objects.equals(r.unit(),line.unit())&&equal(r.applyQuantity(),line.orderQuantity())&&decimal(line.receivedQuantity())&&decimal(line.returnedQuantity())
                &&line.returnedQuantity().compareTo(line.receivedQuantity())<=0&&"0".equals(line.delFlag()),"订单行/根单位/继承量/计数归属未知");
            List<OrderLine> downstream=io(()->sources.lockOrdersForRequisitionLine(tenant,line.requisitionLineId()));require(downstream!=null&&downstream.size()==1&&downstream.get(0)!=null&&id.equals(downstream.get(0).orderId())&&line.lineId().equals(downstream.get(0).lineId()),"申请出现额外或未知真实订单承接");
        }
        Long root=null;
        for(var row:rows) {
            var reqLine=reqById.get(row.getRequisitionLineId());var line=byReq.get(row.getRequisitionLineId());
            require(reqLine!=null&&line!=null&&tenant.equals(row.getTenantId())&&material.equals(row.getMaterialId())&&positive(row.getAllocationId())&&allocationIds.add(row.getAllocationId())
                &&line.lineId().equals(row.getOrderLineId())&&reqLine.sourceId().equals(row.getDetailId())&&positive(row.getBaseUomId())&&row.getBaseUomId().toString().equals(line.unit())
                &&"MRP_DETAIL".equals(row.getSourceType())&&Integer.valueOf(1).equals(row.getAllocationNo())&&command.requestKey().equals(row.getDecisionIdempotencyKey())&&text(row.getAllocationKey(),128)&&keys.add(row.getAllocationKey())
                &&row.getVersion()!=null&&row.getVersion()>=0&&row.getVersion()<Integer.MAX_VALUE&&equal(line.orderQuantity(),row.getCoveredDemandBaseQty())&&decimal(row.getOccupiedBaseQty())&&decimal(row.getFulfilledBaseQty())&&decimal(row.getReleasedBaseQty())
                &&equal(row.getCoveredDemandBaseQty(),row.getOccupiedBaseQty().add(row.getFulfilledBaseQty()).add(row.getReleasedBaseQty())),"账本/四量/根单位/版本/永久来源不一致");
            if(root==null)root=row.getBaseUomId();else require(root.equals(row.getBaseUomId()),"整订单根单位不一致");
        }
        require(
                allocationIds.equals(
                                orderRows.stream()
                                        .map(PuRequirementAllocationLedger::getAllocationId)
                                        .collect(Collectors.toSet()))
                        && rows.stream()
                                        .map(PuRequirementAllocationLedger::getRequisitionLineId)
                                        .distinct()
                                        .count()
                                == reqLines.size(),
                "账本不完整/存在额外来源");
        Set<Long> moneyIds=new HashSet<>();for(var m:money)require(tenant.equals(m.tenantId())&&id.equals(m.orderId())&&lineIds.contains(m.lineId())&&moneyIds.add(m.lineId()),"金额行与订单全集不一致");
        return new Document(head,command,List.copyOf(reqLines),List.copyOf(lines),List.copyOf(rows),List.copyOf(money));
    }
    private void requireDraft(Document d,Integer version) {
        require("DRAFT".equals(d.head().status())&&d.head().version().equals(version),"订单不是原版本未定价草稿");
        requireUnpricedTermsAndMoney(d,false);
        for(var row:d.rows())require("ORDER_COMMITTED_NOT_TIMELY_SUPPLY".equals(row.getAllocationStatus())&&equal(row.getCoveredDemandBaseQty(),row.getOccupiedBaseQty())&&zero(row.getFulfilledBaseQty())&&zero(row.getReleasedBaseQty()),"草稿出现已履约/释放占额");
        for(var l:d.lines())require(zero(l.receivedQuantity())&&zero(l.returnedQuantity())&&"PENDING".equals(l.receiveStatus()),"草稿出现收货/退货事实");
    }
    /** 头条款与全行价税/金额只读事实；不把终态头/账本伪装为草稿。 */
    private static void requireUnpricedTermsAndMoney(Document d,boolean nullableInvoice) {
        require(d.head().quoteId()==null&&d.head().expectedArrivalDate()==null&&absentOrZero(d.head().totalAmount())&&absentOrZero(d.head().taxAmount())
            &&absentOrZero(d.head().amountExcludingTax())&&absentOrZero(d.head().discountAmount())&&absentOrZero(d.head().freightAmount())&&absentOrZero(d.head().discountRate())
            &&d.head().paymentTerms()==null&&d.head().deliveryTerms()==null,"订单不是完整未定价合同，不能覆盖旧条款");
        for(var m:d.money())require(absentOrZero(m.unitPrice())&&absentOrZero(m.unitPriceExcludingTax())&&absentOrZero(m.taxRate())&&absentOrZero(m.lineAmount())
            &&absentOrZero(m.lineAmountExcludingTax())&&absentOrZero(m.lineTaxAmount())&&(nullableInvoice?absentOrZero(m.invoicedQty()):zero(m.invoicedQty())),
            "未批准订单已有未经独立确认的价格/税/金额/发票事实");
    }
    private void requireCurrentCapacityAndCatalog(Document d) {
        requireCurrentCapacityAndCatalog(d,false);
    }
    /** 完整认证核心不变，显式处置仅采用不会创建 guard 的既有预览读取器。 */
    private void requireCurrentCapacityAndCatalog(Document d, boolean existingGuardOnly) {
        qualification.requireSourceChainTransaction();LocalDate end=date(d.source().windowEnd());Set<Long> details=new HashSet<>();for(var l:d.reqLines())require(details.add(l.sourceId()),"冻结MRP来源重复");
        var current=existingGuardOnly
            ? certification.previewLocked(d.head().tenantId(),d.source().materialId(),d.head().vendorId(),end,Set.copyOf(details))
            : certification.prepareLocked(d.head().tenantId(),d.source().materialId(),d.head().vendorId(),end,Set.copyOf(details));
        require(current!=null&&d.head().tenantId().equals(current.tenantId())&&d.source().materialId().equals(current.materialId())&&d.head().vendorId().equals(current.vendorId())&&end.equals(current.windowEnd())&&current.catalog()!=null,"当前全局占额认证作用域与冻结订单不一致");
        require(d.source().catalogFingerprint().equals(ErpPurchaseSourceCertificationService.catalogFingerprint(current.catalog())),"当前目录与永久command冻结合同不一致，必须重新审批");qualification.requireSourceChainTransaction();
    }
    private void requireQuote(Document d,Quote q,boolean current) {
        require(q!=null&&positive(q.quoteId())&&d.head().tenantId().equals(q.tenantId())&&d.head().orderId().equals(q.orderId())&&d.head().vendorId().equals(q.vendorId())&&d.source().materialId().equals(q.materialId())&&d.rows().get(0).getBaseUomId().equals(q.baseUomId())
            &&d.source().requestKey().equals(q.sourceRequestKey())&&d.source().sourceFingerprint().equals(q.sourceFingerprint())&&d.source().catalogFingerprint().equals(q.catalogFingerprint())&&orderFingerprint(d).equals(q.orderFingerprint())&&equal(totalQuantity(d),q.quantity())
            &&q.quoteVersion()!=null&&q.quoteVersion()>0&&requestKey(q.requestKey())&&digest(q.requestHash())&&positive(q.enteredBy())&&q.enteredAt()!=null&&!q.enteredAt().after(databaseNow())&&unitPrice(q.unitPriceExcludingTax())&&rate(q.taxRate())
            &&"CNY".equals(q.currency())&&equal(BigDecimal.ONE,q.exchangeRate())&&text(q.supplierQuoteReference(),128)&&text(q.supplierContact(),128)&&text(q.evidenceReference(),255)&&digest(q.evidenceSha256())&&text(q.paymentTerms(),255)&&text(q.deliveryTerms(),255)
            &&text(q.promisedArrivalDate(),10)&&text(q.validFrom(),10)&&text(q.validUntil(),10)&&ROUNDING.equals(q.roundingPolicy())&&digest(q.quoteHash())&&q.quoteHash().equals(quoteHash(q)),"报价缺少完整ERP持久来源/金额/税/根单位/供应承诺合同");
        LocalDate promise=date(q.promisedArrivalDate()),from=date(q.validFrom()),until=date(q.validUntil());require(!until.isBefore(from)&&!promise.isBefore(from),"报价原始日期合同矛盾");if(current)requireDates(promise,from,until,databaseDate());
    }
    private void requireConfirmation(Quote q,Confirmation c) {
        require(c!=null&&positive(c.confirmationId())&&q.tenantId().equals(c.tenantId())&&q.quoteId().equals(c.quoteId())&&q.quoteHash().equals(c.quoteHash())&&positive(c.actorId())&&!q.enteredBy().equals(c.actorId())&&"pu:order:terms:confirm".equals(c.permissionCode())
            &&requestKey(c.requestKey())&&text(c.reviewNote(),500)&&digest(c.requestHash())&&c.requestHash().equals(confirmationHash(c.tenantId(),c.actorId(),q.orderId(),q.quoteId(),c.requestKey(),c.quoteHash(),c.reviewNote()))
            &&c.confirmedAt()!=null&&!c.confirmedAt().before(q.enteredAt())&&!c.confirmedAt().after(databaseNow()),"报价没有不同actor的独立持久确认审计");
    }
    private List<Integer> quoteVersions(String tenant,Long id) {List<Integer> versions=io(()->mapper.lockQuoteVersions(tenant,id));require(versions!=null&&versions.size()<=10000,"不可变报价版本全集未知");for(int i=0;i<versions.size();i++)require(Integer.valueOf(i+1).equals(versions.get(i)),"报价版本缺口/重复，不能覆盖历史");return versions;}
    private void requireLatestQuote(Quote q){require(quoteVersions(q.tenantId(),q.orderId()).size()==q.quoteVersion(),"已被新供应确认版本替代，不能使用旧报价");}
    private static void validateTerms(Long id,SupplierTermsRequest r) {
        validateId(id);require(r!=null,"供应确认条款为空");validateVersion(id,r.expectedOrderVersion(),r.requestKey());
        require(unitPrice(r.unitPriceExcludingTax())&&rate(r.taxRate())&&"CNY".equals(r.currency())&&equal(BigDecimal.ONE,r.exchangeRate())&&r.promisedArrivalDate()!=null&&r.validFrom()!=null&&r.validUntil()!=null
            &&text(r.supplierQuoteReference(),128)&&text(r.supplierContact(),128)&&text(r.evidenceReference(),255)&&digest(r.evidenceSha256())&&text(r.paymentTerms(),255)&&text(r.deliveryTerms(),255),"价格/税/币种/供应承诺日期/文档全字段缺源或超出支持域");
    }
    private static void requireDates(LocalDate promise,LocalDate from,LocalDate until,LocalDate today) {
        require(promise!=null&&from!=null&&until!=null&&!from.isAfter(today)&&!until.isBefore(today)&&!until.isBefore(from)&&!promise.isBefore(today)&&!promise.isBefore(from),"报价未生效/已过期/供应承诺到货日无效");
        date(promise.toString());date(from.toString());date(until.toString());
    }
    /** 只读核对也覆盖正常录入的总量、逐行价税和整单金额门禁，不生成报价 ID 或时间。 */
    private void validateRequestedAmounts(Document d, SupplierTermsRequest request) {
        totalQuantity(d);
        List<LinePrice> prices = new ArrayList<>();
        for (OrderLine line : d.lines()) {
            prices.add(calculateLine(line.orderQuantity(),request.unitPriceExcludingTax(),request.taxRate()));
        }
        totals(prices);
    }
    private Map<Long,LinePrice> calculatePrices(Document d,Quote q) {
        Map<Long,LinePrice> prices=new LinkedHashMap<>();for(var l:d.lines())prices.put(l.lineId(),calculateLine(l.orderQuantity(),q.unitPriceExcludingTax(),q.taxRate()));totals(prices.values());return prices;
    }
    static LinePrice calculateLine(BigDecimal quantity,BigDecimal unit,BigDecimal taxRate) {
        require(decimal(quantity)&&quantity.signum()>0&&unitPrice(unit)&&rate(taxRate),"真实根单位数量/报价金额精度无效");
        BigDecimal grossUnit=unit.multiply(BigDecimal.ONE.add(taxRate)).setScale(4,RoundingMode.HALF_UP);
        BigDecimal net=quantity.multiply(unit).setScale(2,RoundingMode.HALF_UP);BigDecimal tax=net.multiply(taxRate).setScale(2,RoundingMode.HALF_UP);BigDecimal gross=net.add(tax);
        require(unitPrice(grossUnit)&&money(net)&&net.signum()>0&&money(tax)&&money(gross),"行金额溢出/不足最小金额，不能默认为零");return new LinePrice(unit,grossUnit,taxRate,net,tax,gross);
    }
    private Totals calculateTotals(Document d,Quote q){return totals(calculatePrices(d,q).values());}
    private static Totals totals(Collection<LinePrice> prices) {
        BigDecimal net=new BigDecimal("0.00"),tax=new BigDecimal("0.00"),gross=new BigDecimal("0.00");for(var p:prices){net=net.add(p.net());tax=tax.add(p.tax());gross=gross.add(p.gross());}
        require(money(net)&&money(tax)&&money(gross)&&net.signum()>0&&gross.compareTo(net.add(tax))==0,"整订单原生金额溢出/税额不守恒");return new Totals(net,tax,gross);
    }
    private static String orderFingerprint(Document d) {
        List<String> rows=new ArrayList<>();for(var l:d.lines().stream().sorted(Comparator.comparing(OrderLine::lineId)).toList())rows.add(hash(l.lineId().toString(),l.requisitionLineId().toString(),l.materialId().toString(),l.unit(),number(l.orderQuantity())));
        return hash(d.head().tenantId(),d.head().orderId().toString(),d.head().orderNo(),d.head().requisitionId().toString(),d.head().vendorId().toString(),hash(rows.toArray(String[]::new)));
    }
    private static BigDecimal totalQuantity(Document d){BigDecimal total=BigDecimal.ZERO;for(var l:d.lines())total=total.add(l.orderQuantity());require(decimal(total)&&total.signum()>0,"根单位总量超出原生精度");return total;}
    private static String termsRequestHash(Actor a,Long id,SupplierTermsRequest r){return hash("QUOTE_REQUEST",a.tenantId(),a.userId().toString(),id.toString(),r.expectedOrderVersion().toString(),r.requestKey(),number(r.unitPriceExcludingTax()),number(r.taxRate()),r.currency(),number(r.exchangeRate()),r.promisedArrivalDate().toString(),r.validFrom().toString(),r.validUntil().toString(),r.supplierQuoteReference(),r.supplierContact(),r.evidenceReference(),r.evidenceSha256(),r.paymentTerms(),r.deliveryTerms());}
    private static String quoteHash(Quote q){return hash("SUPPLIER_CONFIRMATION_V1",q.quoteId().toString(),q.tenantId(),q.orderId().toString(),q.quoteVersion().toString(),q.requestKey(),q.requestHash(),q.enteredBy().toString(),Long.toString(q.enteredAt().getTime()),q.vendorId().toString(),q.materialId().toString(),q.baseUomId().toString(),q.sourceRequestKey(),q.sourceFingerprint(),q.catalogFingerprint(),q.orderFingerprint(),number(q.quantity()),number(q.unitPriceExcludingTax()),number(q.taxRate()),q.currency(),number(q.exchangeRate()),q.promisedArrivalDate(),q.validFrom(),q.validUntil(),q.supplierQuoteReference(),q.supplierContact(),q.evidenceReference(),q.evidenceSha256(),q.paymentTerms(),q.deliveryTerms(),q.roundingPolicy());}
    private static Quote withQuoteHash(Quote q,String value){return new Quote(q.quoteId(),q.tenantId(),q.orderId(),q.quoteVersion(),q.requestKey(),q.requestHash(),q.enteredBy(),q.enteredAt(),q.vendorId(),q.materialId(),q.baseUomId(),q.sourceRequestKey(),q.sourceFingerprint(),q.catalogFingerprint(),q.orderFingerprint(),q.quantity(),q.unitPriceExcludingTax(),q.taxRate(),q.currency(),q.exchangeRate(),q.promisedArrivalDate(),q.validFrom(),q.validUntil(),q.supplierQuoteReference(),q.supplierContact(),q.evidenceReference(),q.evidenceSha256(),q.paymentTerms(),q.deliveryTerms(),q.roundingPolicy(),value);}
    private static String confirmationHash(String tenant,Long actor,Long order,Long quote,String key,String quoteHash,String note){return hash("QUOTE_CONFIRM",tenant,actor.toString(),order.toString(),quote.toString(),key,quoteHash,note);}
    private static String operationHash(Actor a,Long id,Integer version,Long quote,String key,Operation op){return hash("ORDER_OPERATION",a.tenantId(),a.userId().toString(),id.toString(),op.name(),version.toString(),value(quote),key);}
    private static String resultFingerprint(String tenant,Result r) {
        List<String> events=new ArrayList<>();for(var e:r.allocations().stream().sorted(Comparator.comparing(EventReceipt::allocationId)).toList())events.add(hash(value(e.eventId()),value(e.allocationId()),e.operation().name(),e.fromStatus(),e.toStatus(),number(e.occupiedBefore()),number(e.occupiedAfter()),value(e.requisitionLineId()),value(e.orderLineId()),value(e.actorId())));
        return hash("ORDER_RESULT",tenant,value(r.orderId()),r.orderNo(),r.requestKey(),r.status(),r.version().toString(),value(r.quoteId()),value(r.quoteHash()),value(r.expectedArrivalDate()),number(r.totalAmount()),number(r.amountExcludingTax()),number(r.taxAmount()),hash(events.toArray(String[]::new)));
    }
    private static Result result(Document d,String key,String state,Integer version,Quote q,Totals amounts,List<EventReceipt> events,boolean replay){return new Result(d.head().orderId(),d.head().orderNo(),key,state,version,q==null?null:q.quoteId(),q==null?null:q.quoteHash(),q==null?null:q.promisedArrivalDate(),amounts==null?null:amounts.gross(),amounts==null?null:amounts.net(),amounts==null?null:amounts.tax(),replay,events);}
    private static OrderCommand orderCommand(Document d,Actor a,Operation op){Map<String,Integer> versions=new LinkedHashMap<>();for(var r:d.rows())versions.put(r.getAllocationKey(),r.getVersion());return new OrderCommand(a.tenantId(),a.userId(),d.source().materialId(),d.source().requestKey(),d.head().orderId(),op,versions);}
    private LocalDate databaseDate(){return date(io(mapper::databaseDate));}
    private Date databaseNow(){Date now=io(sources::databaseNow);require(now!=null,"数据库业务时间未知");return now;}
    private static LocalDate date(String raw){try{LocalDate d=LocalDate.parse(raw);require(d.getYear()>=1000&&d.getYear()<=9999,"DATE范围无效");return d;}catch(RuntimeException e){throw failed("持久日期来源未知");}}
    private static boolean unitPrice(BigDecimal n){return decimal(n)&&n.signum()>0;}
    private static boolean rate(BigDecimal n){return n!=null&&n.signum()>=0&&n.compareTo(BigDecimal.ONE)<=0&&n.stripTrailingZeros().scale()<=4;}
    private static boolean money(BigDecimal n){if(n==null||n.signum()<0)return false;var v=n.stripTrailingZeros();return v.scale()<=2&&(long)v.precision()-v.scale()<=16;}
    private static boolean equal(BigDecimal a,BigDecimal b){return a!=null&&b!=null&&a.compareTo(b)==0;}
    private static boolean zero(BigDecimal n){return n!=null&&n.signum()==0;}
    private static boolean absentOrZero(BigDecimal n){return n==null||zero(n);}
    private static boolean requestKey(String s){return s!=null&&s.matches("[A-Za-z0-9:_-]{1,128}");}
    private static boolean digest(String s){return s!=null&&s.matches("[0-9a-f]{64}");}
    private static String number(BigDecimal n){return n==null?"-":n.stripTrailingZeros().toPlainString();}
    private static String value(Object n){return n==null?"-":n.toString();}
    private static Actor actor(String permission){StpUtil.checkLogin();StpUtil.checkPermission(permission);String tenant=TenantHelper.getTenantId();Long user=LoginHelper.getUserId();requireTenant(tenant);require(positive(user)&&tenant.equals(LoginHelper.getTenantId()),"当前actor/租户未知");return new Actor(tenant,user,permission);}
    private static void recheckActor(Actor a){StpUtil.checkLogin();StpUtil.checkPermission(a.permission());requireTenant(a.tenantId());require(a.userId().equals(LoginHelper.getUserId())&&a.tenantId().equals(LoginHelper.getTenantId()),"当前actor/租户变化");}
    private static void requireTenant(String t){require(TenantHelper.isEnable()&&t!=null&&t.matches("[0-9]{6,20}")&&t.equals(TenantHelper.getTenantId()),"当前租户未启用/不一致");}
    private static void validateId(Long id){require(positive(id),"真实订单ID无效");}
    private static void validateVersion(Long id,Integer version,String key){validateId(id);require(version!=null&&version>=0&&version<Integer.MAX_VALUE&&requestKey(key),"期望版本/永久请求键无效");}
    private static <T>T io(Supplier<T> call){try{return call.get();}catch(RuntimeException e){throw failed("持久来源读写失败，结果不可确认");}}
    private static void require(boolean yes,String message){if(!yes)throw failed(message);}
    private static ServiceException failed(String message){return new ServiceException("可信采购订单拒绝："+message);}
    private record Actor(String tenantId,Long userId,String permission) { }
    private record Document(Head head,CommandSource source,List<RequisitionLine> reqLines,List<OrderLine> lines,List<PuRequirementAllocationLedger> rows,List<LineMoney> money) { }
}
