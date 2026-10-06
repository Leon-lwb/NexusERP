package com.nexus.erp.pu.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService;
import com.nexus.erp.ai.adapter.ErpDecisionPurchaseScopeGuard;
import org.springframework.beans.factory.annotation.Autowired;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService.CertifiedPurchaseScope;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService.CertifiedLine;
import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import com.nexus.erp.pu.domain.PuRequirementAllocationEvent;
import com.nexus.erp.pu.domain.bo.PuCertifiedPurchaseCommand;
import com.nexus.erp.pu.domain.vo.PuCertifiedPurchaseResult;
import com.nexus.erp.pu.domain.vo.PuCertifiedPurchaseResult.Line;
import com.nexus.erp.pu.mapper.*;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseWriteMapper.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;

/**
 * 真正的采购执行入口：来源身份请求→恢复旧结果→事务外编号保留→ERP专属RR原子落账。
 * 只支持已能归属的根基本单位MRP采购；不接收客户端数量、库存或verified证书。
 * master编号/AI状态不属于本ERP事务，编号空洞允许，永久command是跨运行恢复依据。
 */
@Service
public class PuCertifiedPurchaseWriteService {
    private final PuCertifiedPurchaseAuthorizer authorizer;
    private final PuRequirementErpTransactionExecutor executor;
    private final PuRequirementErpTransactionQualification qualification;
    private final PuRequirementMaterialLockService locks;
    private final ErpPurchaseSourceCertificationService certification;
    private final PuCertifiedPurchaseWriteMapper mapper;
    private final PuRequirementAllocationLedgerMapper ledger;
    private final PuRequirementAllocationEventMapper events;
    private final PuCertifiedPurchaseNumberReservation numbers;
    private final PuCertifiedPurchaseLifecycleSourceGate allocationDefinitions;
    private final ErpDecisionPurchaseScopeGuard scopeGuard;

    /** 旧Java调用方仅兼容人工入口；没有归属守卫的新版AI新建仍失败关闭。 */
    public PuCertifiedPurchaseWriteService(PuCertifiedPurchaseAuthorizer authorizer,
        PuRequirementErpTransactionExecutor executor,PuRequirementErpTransactionQualification qualification,
        PuRequirementMaterialLockService locks,ErpPurchaseSourceCertificationService certification,
        PuCertifiedPurchaseWriteMapper mapper,PuRequirementAllocationLedgerMapper ledger,
        PuRequirementAllocationEventMapper events,PuCertifiedPurchaseNumberReservation numbers,PuCertifiedPurchaseLifecycleMapper lifecycleMapper) {
        this(authorizer,executor,qualification,locks,certification,mapper,ledger,events,numbers,lifecycleMapper,null);
    }

    @Autowired
    public PuCertifiedPurchaseWriteService(PuCertifiedPurchaseAuthorizer authorizer,
        PuRequirementErpTransactionExecutor executor,PuRequirementErpTransactionQualification qualification,
        PuRequirementMaterialLockService locks,ErpPurchaseSourceCertificationService certification,
        PuCertifiedPurchaseWriteMapper mapper,PuRequirementAllocationLedgerMapper ledger,
        PuRequirementAllocationEventMapper events,PuCertifiedPurchaseNumberReservation numbers,PuCertifiedPurchaseLifecycleMapper lifecycleMapper,
        ErpDecisionPurchaseScopeGuard scopeGuard) {
        this.scopeGuard=scopeGuard;
        this.authorizer=Objects.requireNonNull(authorizer);this.executor=Objects.requireNonNull(executor);
        this.qualification=Objects.requireNonNull(qualification);this.locks=Objects.requireNonNull(locks);
        this.certification=Objects.requireNonNull(certification);this.mapper=Objects.requireNonNull(mapper);
        this.ledger=Objects.requireNonNull(ledger);this.events=Objects.requireNonNull(events);this.numbers=Objects.requireNonNull(numbers);
        this.allocationDefinitions=new PuCertifiedPurchaseLifecycleSourceGate(lifecycleMapper,qualification);
    }

    /** 仅复核当前请求的实际身份，不写业务表、编号、guard，也不清除动态租户。 */
    public DecisionActor currentActorContext() { return authorizer.requireUser(); }

    public PuCertifiedPurchaseResult createManual(PuCertifiedPurchaseCommand command) {
        return execute(command,authorizer.requireUser(),"MANUAL",null,null,null,null);
    }

    /** 人工选择的低库存/MRP入口仍逐MRP认证，标签不能为未归属低库存采购授予额度。 */
    public PuCertifiedPurchaseResult createMrp(PuCertifiedPurchaseCommand command) {
        return execute(command,authorizer.requireUser(),"MRP",null,null,null,null);
    }
    public PuCertifiedPurchaseResult createLowStock(PuCertifiedPurchaseCommand command) {
        return execute(command,authorizer.requireUser(),"LOW_STOCK",null,null,null,null);
    }

    /** 审批数值只是待比对的冻结意图，不是业务数量事实或写入许可。 */
    public record FrozenLine(Long detailId,Long baseUomId,BigDecimal coveredDemandBase,Long orderUomId,
        BigDecimal orderQuantity,LocalDate requiredDate) { }

    /** 完整冻结合同由ERP adapter从已批准payload生成，不能作为数量或认证事实。 */
    public record FrozenContract(String payloadFingerprint,Map<String,String> metadata,Long rootWorkOrderId) {
        public FrozenContract { if(metadata!=null) metadata=Map.copyOf(metadata); }
        /** 仅保留Java兼容，缺根合同不授予新版AI新建授权。 */
        public FrozenContract(String payloadFingerprint,Map<String,String> metadata) {this(payloadFingerprint,metadata,null);}
    }
    public PuCertifiedPurchaseResult createForDecision(PuCertifiedPurchaseCommand command,DecisionActor actor,
        Long runId,Long actionId,List<FrozenLine> frozenLines,FrozenContract contract) {
        List<FrozenLine> frozen=validateDecision(command,actor,runId,actionId,frozenLines);
        validateContract(contract);
        return execute(command,actor,"AI_DECISION",runId,actionId,frozen,contract);
    }
    public PuCertifiedPurchaseResult recoverForDecision(PuCertifiedPurchaseCommand command,DecisionActor actor,
        Long runId,Long actionId,List<FrozenLine> frozenLines,FrozenContract contract) {
        List<FrozenLine> frozen=validateDecision(command,actor,runId,actionId,frozenLines);
        validateContract(contract);validate(command,actor);
        String requestHash=requestHash(command,actor,"AI_DECISION",runId,actionId,frozen,contract);
        return executor.executeRepeatableRead(actor.tenantId(),()-> {
            prepareStore(actor.tenantId(),command.materialId(),true);
            return recover(command,actor,"AI_DECISION",requestHash,runId,actionId);
        });
    }

    /** 仅供经过工业决策权限/状态机的ERP适配器调用；旧提案不能执行。 */
    public PuCertifiedPurchaseResult createForDecision(PuCertifiedPurchaseCommand command,DecisionActor actor,
        Long runId,Long actionId,List<FrozenLine> frozenLines) {
        throw failed("完整AI冻结合同缺失，禁止旧参数入口写入");
    }

    /** 仅恢复已有ERP提交；verify调用此法不会在记录不存在时创建采购申请。 */
    public PuCertifiedPurchaseResult recoverForDecision(PuCertifiedPurchaseCommand command,DecisionActor actor,
        Long runId,Long actionId,List<FrozenLine> frozenLines) {
        throw failed("完整AI冻结合同缺失，禁止旧参数入口恢复");
    }

    private static List<FrozenLine> validateDecision(PuCertifiedPurchaseCommand command,DecisionActor actor,
        Long runId,Long actionId,List<FrozenLine> lines) {
        require(command!=null&&command.expectedSourceFingerprint()!=null&&positive(runId)&&positive(actionId),"AI冻结来源与动作身份缺失");
        require(actor!=null&&actor.permissions()!=null&&(actor.permissions().contains("*:*:*")
            ||(actor.permissions().contains("ai:decision:execute")&&actor.permissions().contains("pu:requisition:add"))),"AI采购执行或业务权限缺失");
        require(lines!=null&&!lines.isEmpty()&&lines.size()<=1000&&lines.stream().allMatch(Objects::nonNull),"AI逐来源冻结申请量缺失");
        Set<Long> seen=new HashSet<>();
        for(FrozenLine line:lines) require(positive(line.detailId())&&seen.add(line.detailId())&&positive(line.baseUomId())
            &&positive(line.orderUomId())&&decimal(line.coveredDemandBase())&&decimal(line.orderQuantity())&&line.requiredDate()!=null,
            "AI冻结行身份、单位或精确数量无效");
        return List.copyOf(lines);
    }

    private PuCertifiedPurchaseResult execute(PuCertifiedPurchaseCommand command,DecisionActor actor,
        String entryType,Long runId,Long actionId,List<FrozenLine> frozenLines,FrozenContract contract) {
        validate(command,actor);
        // 冻结输入以免调用方在编号或事务等待期间改动；record已对来源集合防御复制。
        String requestHash=requestHash(command,actor,entryType,runId,actionId,frozenLines,contract);
        PuCertifiedPurchaseResult recovered=executor.executeRepeatableRead(actor.tenantId(),()-> {
            prepareStore(actor.tenantId(),command.materialId(),"AI_DECISION".equals(entryType));
            return recover(command,actor,entryType,requestHash,runId,actionId);
        });
        if(recovered!=null)return recovered;
        if("AI_DECISION".equals(entryType))require(contract!=null&&positive(contract.rootWorkOrderId())&&scopeGuard!=null,
            "完整AI冻结合同缺少可核验的工单根守卫");
        // 这里已经真实退出并清理前一个ERP事务；AutoCode的master事务不混入ERP原子性。
        String number=numbers.reserveRequisitionNumber();
        require(number!=null&&number.matches("[A-Za-z0-9_-]{1,64}"),"编号保留结果无效");
        return executor.executeRepeatableRead(actor.tenantId(),()-> {
            validate(command,actor);
            prepareStore(actor.tenantId(),command.materialId(),"AI_DECISION".equals(entryType));
            PuCertifiedPurchaseResult race=recover(command,actor,entryType,requestHash,runId,actionId);
            if(race!=null)return race;
            if("AI_DECISION".equals(entryType)) {
                require(contract!=null&&positive(contract.rootWorkOrderId())&&scopeGuard!=null,"完整AI冻结合同缺少可核验的工单根守卫");
                scopeGuard.requireWorkOrderSourcesLocked(actor.tenantId(),contract.rootWorkOrderId(),command.materialId(),
                    command.vendorId(),Set.copyOf(command.requirementDetailIds()),command.windowEnd());
            }
            CertifiedPurchaseScope scope=certification.prepareLocked(actor.tenantId(),command.materialId(),command.vendorId(),
                command.windowEnd(),Set.copyOf(command.requirementDetailIds()));
            validateScope(scope,command,actor);
            matchFrozen(scope,frozenLines);
            matchContract(scope,contract);
            List<CertifiedLine> certified=scope.lines().stream().sorted(Comparator.comparing(CertifiedLine::detailId)).toList();
            Long id=IdWorker.getId();
            String requiredAt=wall(certified.stream().map(CertifiedLine::requiredDate).min(LocalDate::compareTo).orElseThrow());
            require(mapper.insertDocument(id,actor.tenantId(),number,"可信来源采购申请-"+command.materialId(),actor.userId(),
                actor.userName(),requiredAt,entryType,runId,actionId,command.requestKey())==1,"申请头写入失败");
            List<Line> resultLines=new ArrayList<>();
            for(CertifiedLine line:certified) {
                Long lineId=IdWorker.getId();
                require(mapper.insertLine(lineId,actor.tenantId(),id,command.materialId(),line.baseUomId().toString(),
                    line.orderQuantity(),wall(line.requiredDate()),line.detailId(),actor.userId())==1,"申请行写入失败");
                var row=new PuRequirementAllocationLedger();
                row.setAllocationId(IdWorker.getId());row.setTenantId(actor.tenantId());row.setMaterialId(command.materialId());
                row.setSourceType("MRP_DETAIL");row.setDetailId(line.detailId());row.setRequisitionLineId(lineId);row.setAllocationNo(1);
                row.setBaseUomId(line.baseUomId());row.setCoveredDemandBaseQty(line.coveredDemandBase());row.setOccupiedBaseQty(line.coveredDemandBase());
                row.setFulfilledBaseQty(BigDecimal.ZERO);row.setReleasedBaseQty(BigDecimal.ZERO);row.setVersion(0);
                row.setAllocationStatus("REQUEST_INTENT");row.setAllocationKey(hash(actor.tenantId(),command.requestKey(),line.detailId().toString()));
                row.setDecisionIdempotencyKey(command.requestKey());
                require(ledger.insert(row)==1,"逐需求占用写入失败");
                var event=new PuRequirementAllocationEvent();
                event.setEventId(IdWorker.getId());event.setTenantId(actor.tenantId());event.setAllocationId(row.getAllocationId());
                event.setEventKey("CREATE:"+row.getAllocationKey());event.setEventType("CREATE");event.setEventSequence(0);event.setToStatus("REQUEST_INTENT");
                event.setOccupiedBaseBefore(BigDecimal.ZERO);event.setOccupiedBaseAfter(line.coveredDemandBase());
                event.setRequisitionLineId(lineId);event.setActorId(actor.userId());
                require(events.insert(event)==1,"逐需求追加事件写入失败");
                resultLines.add(new Line(lineId,line.detailId(),command.materialId(),line.baseUomId(),line.orderQuantity(),line.requiredDate()));
            }
            var receipt=new CommandReceipt(command.requestKey(),requestHash,actor.userId(),entryType,command.materialId(),command.vendorId(),
                id,number,scope.sourceFingerprint(),resultFingerprint(resultLines),"COMMITTED",
                ErpPurchaseSourceCertificationService.catalogFingerprint(scope.catalog()));
            require(mapper.insertCommand(actor.tenantId(),receipt,command.windowEnd().toString())==1,"永久采购请求落账失败");
            qualification.requireSourceChainTransaction();
            return new PuCertifiedPurchaseResult(id,number,"DRAFT",0,false,scope.sourceFingerprint(),resultLines);
        });
    }

    /** 恢复仅锁已有guard，既有人工创建仍保留原初始化行为。 */
    private void prepareStore(String tenantId,Long materialId,boolean existingOnly) {
        qualification.requireSourceChainTransaction();
        allocationDefinitions.requireAllocationDefinitions();
        // 物料guard在command读取前保持统一锁次序；同来源并发不会凭应用快照创建第二次占用。
        if(existingOnly) locks.lockExisting(tenantId,List.of(materialId));
        else locks.lock(tenantId,List.of(materialId));
        List<String> pinned=mapper.pinCommandDefinition();
        require(pinned!=null&&pinned.isEmpty(),"永久采购请求表定义缺失或空查询合同异常");
        require("InnoDB".equalsIgnoreCase(mapper.commandTableEngine()),"永久采购请求表不是已认证事务引擎");
        List<CommandUniqueKeyColumn> keys=mapper.commandUniqueKeyColumns();
        require(keys!=null&&keys.size()==4,"永久请求表真实唯一键缺失或未知");
        Map<String,List<String>> byName=new TreeMap<>();
        for(var key:keys) {
            require(key!=null&&Integer.valueOf(0).equals(key.nonUnique())&&key.prefixLength()==null
                &&key.indexName()!=null&&key.columnName()!=null&&key.sequenceNumber()!=null,"永久请求键不是完整唯一索引");
            List<String> columns=byName.computeIfAbsent(key.indexName(),ignored->new ArrayList<>());
            require(key.sequenceNumber()==columns.size()+1,"永久请求唯一键字段顺序未知");columns.add(key.columnName());
        }
        require(byName.equals(Map.of("PRIMARY",List.of("tenant_id","request_key"),
            "uk_certified_purchase_requisition",List.of("tenant_id","requisition_id"))),"永久请求唯一键作用域不正确");
    }

    private PuCertifiedPurchaseResult recover(PuCertifiedPurchaseCommand command,DecisionActor actor,String entryType,String requestHash,Long runId,Long actionId) {
        CommandReceipt old=mapper.lockCommand(actor.tenantId(),command.requestKey());
        if(old==null)return null;
        require(requestHash.equals(old.requestHash())&&actor.userId().equals(old.actorId())&&entryType.equals(old.entryType())
            &&command.materialId().equals(old.materialId())&&command.vendorId().equals(old.vendorId()),"请求键被不同身份或不同冻结输入占用");
        require("COMMITTED".equals(old.commandStatus()),"采购请求已补偿或终态未知，永久键不可重建");
        require(old.catalogFingerprint()!=null&&old.catalogFingerprint().matches("[0-9a-f]{64}"),"原采购请求冻结目录摘要缺失");
        require(old.sourceFingerprint()!=null&&old.sourceFingerprint().matches("[0-9a-f]{64}")
            &&(command.expectedSourceFingerprint()==null||command.expectedSourceFingerprint().equals(old.sourceFingerprint())),
            "已提交采购请求来源摘要与原意图不一致");
        DocumentReceipt doc=mapper.lockDocument(actor.tenantId(),old.requisitionId());
        require(doc!=null&&"0".equals(doc.delFlag())&&old.requisitionId().equals(doc.requisitionId())
            &&old.requisitionNo().equals(doc.requisitionNo())&&command.requestKey().equals(doc.requestKey())
            &&entryType.equals(doc.entryType())&&actor.userId().equals(doc.applicantId())
            &&Objects.equals(runId,doc.runId())&&Objects.equals(actionId,doc.actionId())
            &&doc.version()!=null&&doc.version()>=0&&Set.of("DRAFT","SUBMITTED","APPROVING","APPROVED","REJECTED","ORDERED","CLOSED","CANCELLED").contains(doc.status()),
            "已提交采购请求与实际单据不一致");
        List<LineReceipt> rows=mapper.lockLines(actor.tenantId(),old.requisitionId());
        require(rows!=null&&!rows.isEmpty(),"已提交采购请求缺少真实申请行");
        List<Line> lines=new ArrayList<>();Set<Long> ids=new HashSet<>();
        for(var row:rows) {
            require(row!=null&&positive(row.lineId())&&ids.add(row.lineId())&&command.materialId().equals(row.materialId())
                &&positive(row.detailId())&&command.requirementDetailIds().contains(row.detailId())&&"MRP".equals(row.sourceType())
                &&"0".equals(row.delFlag())&&row.baseUnit()!=null&&row.baseUnit().matches("[1-9][0-9]{0,18}")&&decimal(row.applyQuantity()),
                "已提交采购申请行的来源或精确数量改变");
            LocalDate date;
            try { require(row.requiredAt()!=null&&row.requiredAt().matches("[0-9]{4}-[0-9]{2}-[0-9]{2} 00:00:00"),"已提交来源日期合同未知");
                date=LocalDate.parse(row.requiredAt().substring(0,10));
                lines.add(new Line(row.lineId(),row.detailId(),row.materialId(),Long.valueOf(row.baseUnit()),row.applyQuantity(),date));
            } catch(java.time.DateTimeException|NumberFormatException invalid) { throw failed("已提交来源日期或基本单位未知"); }
        }
        require(resultFingerprint(lines).equals(old.resultFingerprint()),"已提交采购行与冻结结果摘要不一致");
        for(Line line:lines) validateRecoveredAllocation(command,actor,doc,line);
        qualification.requireSourceChainTransaction();
        return new PuCertifiedPurchaseResult(doc.requisitionId(),doc.requisitionNo(),doc.status(),doc.version(),true,old.sourceFingerprint(),lines);
    }

    private void validateRecoveredAllocation(PuCertifiedPurchaseCommand c,DecisionActor actor,DocumentReceipt doc,Line line) {
        String key=hash(actor.tenantId(),c.requestKey(),line.requirementDetailId().toString());
        var row=ledger.lockByKey(actor.tenantId(),key);
        require(row!=null&&positive(row.getAllocationId())&&actor.tenantId().equals(row.getTenantId())
            &&line.materialId().equals(row.getMaterialId())&&line.requirementDetailId().equals(row.getDetailId())
            &&line.lineId().equals(row.getRequisitionLineId())&&line.baseUomId().equals(row.getBaseUomId())
            &&"MRP_DETAIL".equals(row.getSourceType())&&key.equals(row.getAllocationKey())
            &&c.requestKey().equals(row.getDecisionIdempotencyKey())&&Integer.valueOf(1).equals(row.getAllocationNo())
            &&row.getVersion()!=null&&row.getVersion()>=0&&row.getCreateTime()!=null&&decimal(row.getCoveredDemandBaseQty())
            &&row.getCoveredDemandBaseQty().compareTo(line.applyQuantity())==0&&nonnegative(row.getOccupiedBaseQty())
            &&nonnegative(row.getFulfilledBaseQty())&&nonnegative(row.getReleasedBaseQty())
            &&row.getCoveredDemandBaseQty().compareTo(row.getOccupiedBaseQty().add(row.getFulfilledBaseQty()).add(row.getReleasedBaseQty()))==0,
            "已提交采购请求缺少完整守恒的真实占用");
        if("REQUEST_INTENT".equals(row.getAllocationStatus())) {
            require(Set.of("DRAFT","SUBMITTED","APPROVING","APPROVED","REJECTED").contains(doc.status())
                &&row.getOrderLineId()==null&&row.getTransferTime()==null&&row.getReleasedAt()==null&&row.getReleaseReason()==null
                &&row.getFulfilledBaseQty().signum()==0&&row.getReleasedBaseQty().signum()==0,"原申请意图与实际状态不一致");
        } else if("ORDER_COMMITTED_NOT_TIMELY_SUPPLY".equals(row.getAllocationStatus())) {
            require(positive(row.getOrderLineId())&&row.getTransferTime()!=null&&row.getReleasedBaseQty().signum()==0
                &&row.getReleasedAt()==null&&row.getReleaseReason()==null&&Set.of("ORDERED","CLOSED").contains(doc.status()),
                "订单承接恢复来源不完整");
        } else if("RELEASED".equals(row.getAllocationStatus())) {
            require(row.getOccupiedBaseQty().signum()==0&&row.getReleasedAt()!=null&&row.getReleaseReason()!=null
                &&!row.getReleaseReason().isBlank()&&((row.getOrderLineId()==null&&row.getTransferTime()==null
                &&Set.of("CLOSED","CANCELLED").contains(doc.status()))||positive(row.getOrderLineId())),"释放恢复来源不完整");
        } else throw failed("占用恢复状态未支持");
        var creates=mapper.lockCreationEvents(actor.tenantId(),row.getAllocationId());
        require(creates!=null&&creates.size()==1,"原始创建追加事件缺失或重复");
        var initial=creates.get(0);
        require(initial!=null&&positive(initial.getEventId())&&actor.tenantId().equals(initial.getTenantId())
            &&row.getAllocationId().equals(initial.getAllocationId())&&("CREATE:"+key).equals(initial.getEventKey())
            &&"CREATE".equals(initial.getEventType())&&Integer.valueOf(0).equals(initial.getEventSequence())&&initial.getFromStatus()==null&&"REQUEST_INTENT".equals(initial.getToStatus())
            &&initial.getOrderLineId()==null&&line.lineId().equals(initial.getRequisitionLineId())
            &&actor.userId().equals(initial.getActorId())&&initial.getOccurredAt()!=null&&nonnegative(initial.getOccupiedBaseBefore())
            &&initial.getOccupiedBaseBefore().signum()==0&&decimal(initial.getOccupiedBaseAfter())
            &&initial.getOccupiedBaseAfter().compareTo(line.applyQuantity())==0,"原始创建事件与冻结申请/占用不一致");
    }

    private static void validate(PuCertifiedPurchaseCommand c,DecisionActor actor) {
        require(actor!=null&&TenantHelper.isEnable()&&actor.tenantId()!=null&&actor.tenantId().matches("[0-9]{6,20}")
            &&actor.tenantId().equals(TenantHelper.getTenantId())&&positive(actor.userId())&&actor.userName()!=null
            &&!actor.userName().isBlank()&&actor.userName().length()<=64,"当前采购身份/租户未知");
        require(c!=null&&c.requestKey()!=null&&c.requestKey().matches("[A-Za-z0-9:_-]{16,128}")&&positive(c.materialId())
            &&positive(c.vendorId())&&c.windowEnd()!=null&&c.windowEnd().getYear()>=1000&&c.windowEnd().getYear()<=9999
            &&c.requirementDetailIds()!=null&&!c.requirementDetailIds().isEmpty()&&c.requirementDetailIds().size()<=1000
            &&c.requirementDetailIds().stream().allMatch(PuCertifiedPurchaseWriteService::positive)
            &&new HashSet<>(c.requirementDetailIds()).size()==c.requirementDetailIds().size(),"请求键/物料/供应商/窗口/逐需求身份无效");
        require(c.expectedSourceFingerprint()==null||c.expectedSourceFingerprint().matches("[0-9a-f]{64}"),"冻结来源摘要格式无效");
    }
    private static void validateScope(CertifiedPurchaseScope scope,PuCertifiedPurchaseCommand c,DecisionActor actor) {
        require(scope!=null&&actor.tenantId().equals(scope.tenantId())&&c.materialId().equals(scope.materialId())
            &&c.vendorId().equals(scope.vendorId())&&c.windowEnd().equals(scope.windowEnd())&&scope.sourceFingerprint()!=null
            &&scope.sourceFingerprint().matches("[0-9a-f]{64}"),"实际认证来源作用域无效");
        require(c.expectedSourceFingerprint()==null||c.expectedSourceFingerprint().equals(scope.sourceFingerprint()),"冻结来源已变化，须重新审批而非重算旧请求");
        require(scope.lines()!=null&&!scope.lines().isEmpty(),"当前全局额度无可新增采购量");
        Set<Long> seen=new HashSet<>();
        for(CertifiedLine line:scope.lines())require(line!=null&&positive(line.detailId())&&seen.add(line.detailId())
            &&c.requirementDetailIds().contains(line.detailId())&&positive(line.baseUomId())&&line.baseUomId().equals(line.orderUomId())
            &&decimal(line.coveredDemandBase())&&decimal(line.orderQuantity())&&line.coveredDemandBase().compareTo(line.orderQuantity())==0
            &&line.requiredDate()!=null&&!line.requiredDate().isAfter(c.windowEnd()),"仅支持已核根基本单位、无超额的逐MRP精确采购");
    }
    private static final Set<String> CONTRACT_KEYS=Set.of("materialVersion","vendorVersion","supplierTermsId","supplierTermsVersion",
        "baseUomId","baseUomVersion","orderUomId","orderUomVersion","conversionVersion");
    private static void validateContract(FrozenContract contract) {
        require(contract!=null&&contract.payloadFingerprint()!=null&&contract.payloadFingerprint().matches("[0-9a-f]{64}")
            &&contract.metadata()!=null&&contract.metadata().keySet().equals(CONTRACT_KEYS),"完整AI冻结条款/版本/目标摘要缺失");
        require(contract.metadata().values().stream().allMatch(v->v!=null&&!v.isBlank()&&v.equals(v.trim())&&v.length()<=256
            &&v.chars().noneMatch(Character::isISOControl)),"完整AI冻结合同字段无效");
    }
    private static String contractFingerprint(FrozenContract contract) {
        if(contract==null)return "";
        List<String> fields=new ArrayList<>();fields.add(contract.payloadFingerprint());
        new TreeMap<>(contract.metadata()).forEach((k,v)->{fields.add(k);fields.add(v);});
        // 缺根旧构造器保持旧摘要兼容；新版根以类型与ID绑定，不纳入展示编号。
        if(contract.rootWorkOrderId()!=null) {fields.add("WORK_ORDER");fields.add(contract.rootWorkOrderId().toString());}
        return hash(fields.toArray(String[]::new));
    }
    private static void matchContract(CertifiedPurchaseScope scope,FrozenContract contract) {
        if(contract==null)return;
        var catalog=scope.catalog();var m=contract.metadata();
        require(catalog!=null&&catalog.terms()!=null&&catalog.baseUnit()!=null&&catalog.orderUnit()!=null&&catalog.sourceVersions()!=null,
            "AI显式冻结版本缺少当前认证目录");
        require(m.get("supplierTermsId").equals(catalog.terms().termsId())&&m.get("supplierTermsVersion").equals(catalog.terms().termsVersion())
            &&m.get("materialVersion").equals(catalog.sourceVersions().get("material:"+scope.materialId()))
            &&m.get("vendorVersion").equals(catalog.sourceVersions().get("vendor:"+scope.vendorId()))
            &&m.get("baseUomId").equals(catalog.baseUnit().unitId())&&m.get("orderUomId").equals(catalog.orderUnit().unitId())
            &&m.get("baseUomVersion").equals(catalog.baseUnit().conversionVersion())
            &&m.get("orderUomVersion").equals(catalog.orderUnit().conversionVersion())
            &&m.get("conversionVersion").equals(catalog.orderUnit().conversionVersion()),"AI显式冻结条款、来源内容或UOM版本与实际目录不一致");
    }
    private static void matchFrozen(CertifiedPurchaseScope scope,List<FrozenLine> frozen) {
        if(frozen==null)return;
        require(frozen.size()==scope.lines().size(),"AI冻结来源集合与当前全局分配不一致");
        Map<Long,FrozenLine> byId=new HashMap<>();frozen.forEach(l->byId.put(l.detailId(),l));
        for(CertifiedLine line:scope.lines()) {
            FrozenLine expected=byId.get(line.detailId());
            require(expected!=null&&expected.baseUomId().equals(line.baseUomId())&&expected.orderUomId().equals(line.orderUomId())
                &&expected.coveredDemandBase().compareTo(line.coveredDemandBase())==0&&expected.orderQuantity().compareTo(line.orderQuantity())==0
                &&expected.requiredDate().equals(line.requiredDate()),"AI冻结数值或单位与事务内可信来源不一致");
        }
    }
    private static String frozenFingerprint(List<FrozenLine> frozen) {
        if(frozen==null)return "";
        return hash(frozen.stream().sorted(Comparator.comparing(FrozenLine::detailId)).map(l->hash(l.detailId().toString(),
            l.baseUomId().toString(),l.coveredDemandBase().stripTrailingZeros().toPlainString(),l.orderUomId().toString(),
            l.orderQuantity().stripTrailingZeros().toPlainString(),l.requiredDate().toString())).toArray(String[]::new));
    }
    private static boolean positive(Long value){return value!=null&&value>0;}
    private static boolean decimal(BigDecimal n){return n!=null&&n.signum()>0&&n.stripTrailingZeros().scale()<=4
        &&(long)n.stripTrailingZeros().precision()-n.stripTrailingZeros().scale()<=14;}
    private static boolean nonnegative(BigDecimal n){return n!=null&&(n.signum()==0||decimal(n));}
    private static String wall(LocalDate date){return date+" 00:00:00";}
    private static String requestHash(PuCertifiedPurchaseCommand c,DecisionActor actor,String source,Long run,Long action,List<FrozenLine> frozen,FrozenContract contract) {
        return hash(actor.tenantId(),actor.userId().toString(),source,c.requestKey(),c.materialId().toString(),c.vendorId().toString(),
            c.windowEnd().toString(),c.requirementDetailIds().stream().sorted().map(Object::toString).reduce("",(a,b)->a+","+b),
            Objects.toString(c.expectedSourceFingerprint(),""),Objects.toString(run,""),Objects.toString(action,""),frozenFingerprint(frozen),contractFingerprint(contract));
    }
    private static String resultFingerprint(List<Line> lines) {
        return hash(lines.stream().sorted(Comparator.comparing(Line::requirementDetailId)).map(l->hash(l.lineId().toString(),
            l.requirementDetailId().toString(),l.materialId().toString(),l.baseUomId().toString(),
            l.applyQuantity().stripTrailingZeros().toPlainString(),l.requiredDate().toString())).toArray(String[]::new));
    }
    private static String hash(String... values){try{MessageDigest d=MessageDigest.getInstance("SHA-256");
        for(String value:values){byte[] b=value.getBytes(StandardCharsets.UTF_8);d.update(java.nio.ByteBuffer.allocate(4).putInt(b.length).array());d.update(b);}
        return HexFormat.of().formatHex(d.digest());}catch(NoSuchAlgorithmException impossible){throw failed("采购请求摘要不可用");}}
    private static void require(boolean value,String message){if(!value)throw failed(message);}
    private static ServiceException failed(String message){return new ServiceException("可信采购写入拒绝："+message);}
}
