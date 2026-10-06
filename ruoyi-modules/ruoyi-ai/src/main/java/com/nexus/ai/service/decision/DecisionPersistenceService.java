package com.nexus.ai.service.decision;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.*;
import com.nexus.common.ai.domain.entity.*;
import com.nexus.common.ai.mapper.*;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 主库持久化，每次变更均显式限定租户并检查状态与版本。
 */
@Service
@RequiredArgsConstructor
public class DecisionPersistenceService {
    public static final String SCENE_CODE = "DELIVERY_RISK";
    private static final Set<DecisionRunStatus> ACTIVE = EnumSet.of(DecisionRunStatus.CREATED, DecisionRunStatus.RUNNING,
        DecisionRunStatus.WAITING_APPROVAL, DecisionRunStatus.APPROVED, DecisionRunStatus.EXECUTING,
        DecisionRunStatus.ROLLING_BACK);
    private static final Pattern SECRETS = Pattern.compile("(?i)(?:bearer\\s+\\S+|sk-[a-z0-9_-]+|api[_-]?key\\s*[:=]\\s*\\S+|jdbc:(?:mysql|postgresql):\\S+)");

    private static final Set<String> PURCHASE_RESULT_FIELDS = Set.of("purchase_payload_version", "runId", "actionId",
        "tenantId", "requestKey", "materialId", "vendorId", "sourceType", "sourceFingerprint", "payloadFingerprint",
        "status", "versionMarker", "beforeState", "afterState", "replayed", "lines");
    private static final Set<String> PURCHASE_RESULT_LINE_FIELDS = Set.of("lineId", "requirementDetailId", "materialId",
        "baseUomId", "orderUomId", "coveredDemandBase", "requestedOrderQty", "requiredDate");
    private static final Set<String> EXECUTION_RESULT_FIELDS = Set.of("success", "retryable", "resultBizType", "resultBizId",
        "resultBizCode", "result", "errorCode", "errorMessage");

    private final AiDecisionRunMapper runMapper;
    private final AiDecisionEvidenceMapper evidenceMapper;
    private final AiDecisionActionMapper actionMapper;
    private final DecisionRunStateMachine stateMachine;
    private final DecisionEvidenceService evidenceService;
    /**
     * 注入应用配置的映射器，不绕过 BigNumberSerializer。
     */
    private final ObjectMapper objectMapper;

    public AiDecisionRun findActiveRun(DecisionBusinessRef ref, String tenantId) {
        String tenant = requireTenant(tenantId);
        requireThreadTenant(tenant);
        if (ref == null || ref.type() == null) throw new ServiceException("决策业务对象无效");
        var query = Wrappers.<AiDecisionRun>lambdaQuery()
            .eq(AiDecisionRun::getTenantId, tenant).eq(AiDecisionRun::getSceneCode, SCENE_CODE)
            .eq(AiDecisionRun::getBizType, ref.type().name())
            .in(AiDecisionRun::getStatus, ACTIVE.stream().map(Enum::name).toList());
        if (ref.id() == null) query.isNull(AiDecisionRun::getBizId);
        else query.eq(AiDecisionRun::getBizId, ref.id());
        return runMapper.selectOne(query);
    }

    public long countActiveRuns(String tenantId) {
        String tenant = requireTenant(tenantId);
        requireThreadTenant(tenant);
        Long count = runMapper.selectCount(Wrappers.<AiDecisionRun>lambdaQuery()
            .eq(AiDecisionRun::getTenantId, tenant).eq(AiDecisionRun::getSceneCode, SCENE_CODE)
            .in(AiDecisionRun::getStatus, ACTIVE.stream().map(Enum::name).toList()));
        if (count == null) throw new ServiceException("无法查询活跃决策数量");
        return count;
    }

    @Transactional(rollbackFor = Exception.class)
    public AiDecisionRun createRun(DeliveryRiskRunRequest request, DecisionActor actor) {
        String tenant = requireActor(actor);
        if (request == null || !request.isBusinessScopeValid() || request.getBizType() == null
            || request.getHorizonDays() == null || request.getHorizonDays() < 1 || request.getHorizonDays() > 90)
            throw new ServiceException("决策启动请求无效");
        AiDecisionRun run = new AiDecisionRun();
        run.setTenantId(tenant); run.setSceneCode(SCENE_CODE);
        run.setBizType(request.getBizType().name()); run.setBizId(request.getBizId());
        run.setTriggerType((request.getTriggerType() == null ? DecisionTriggerType.MANUAL
            : request.getTriggerType()).name());
        run.setStatus(DecisionRunStatus.CREATED.name()); run.setVersion(0);
        run.setDecisionContractVersion(2);
        run.setCreateBy(actor.userId()); run.setUpdateBy(actor.userId());
        run.setScoreConfigJson(json(Map.of("horizonDays", request.getHorizonDays(),
            "scenarioCodes", request.getScenarioCodes(), "userName", actor.userName(),
            "permissions", actor.permissions()))); // 冻结执行范围，不使用客户端或流程传入的操作者字段。
        if (runMapper.insert(run) != 1 || run.getId() == null || run.getId() <= 0)
            throw new ServiceException("决策运行创建失败");
        return run;
    }

    @Transactional(rollbackFor = Exception.class)
    public void bindExecutionActor(Long runId, DecisionActor actor) {
        String tenant = requireActor(actor);
        AiDecisionRun run = requireRun(runId, tenant);
        if (!"WAITING_APPROVAL".equals(run.getStatus()) || run.getApprovalContextJson() != null
            || actor.userName() == null || actor.userName().isBlank() || actor.permissions() == null)
            throw new ServiceException("审批身份不可绑定当前决策");
        String scope = json(Map.of("userId", actor.userId(), "userName", actor.userName(),
            "permissions", actor.permissions()));
        if (runMapper.update(null, baseUpdate(run, tenant)
            .eq(AiDecisionRun::getStatus, "WAITING_APPROVAL")
            .isNull(AiDecisionRun::getApprovalContextJson)
            .set(AiDecisionRun::getApprovalContextJson, scope)) != 1)
            throw new ServiceException("审批人权限快照并发写入冲突");
    }

    public DecisionActor trustedExecutionActor(Long runId, String tenantId) {
        AiDecisionRun run = requireRun(runId, tenantId);
        if (run.getApprovalContextJson() == null || !Set.of("APPROVED", "EXECUTING", "SUCCEEDED", "PARTIAL_SUCCESS")
            .contains(run.getStatus())) throw new ServiceException("运行尚无可信的审批执行人");
        try {
            var frozen = objectMapper.readTree(run.getApprovalContextJson());
            Long id = Long.valueOf(frozen.path("userId").asText("0"));
            String name = frozen.path("userName").asText("");
            var rights = frozen.path("permissions");
            if (id <= 0 || name.isBlank() || !rights.isArray() || rights.isEmpty())
                throw new ServiceException("审批执行身份快照无效");
            Set<String> permissions = new HashSet<>();
            rights.forEach(right -> { if (right.isTextual()) permissions.add(right.asText()); });
            if (permissions.size() != rights.size() || !permissions.contains("ai:decision:execute")
                && !permissions.contains("*:*:*"))
                throw new ServiceException("审批执行权限快照无效");
            return new DecisionActor(run.getTenantId(), id, name, Set.copyOf(permissions));
        } catch (JsonProcessingException | NumberFormatException bad) {
            throw new ServiceException("审批执行身份无法解析");
        }
    }

    public DecisionActor trustedActor(Long runId, String tenantId) {
        AiDecisionRun run = requireRun(runId, tenantId);
        if (run.getCreateBy() == null || run.getCreateBy() <= 0 || run.getScoreConfigJson() == null)
            throw new ServiceException("运行缺少可信的创建人和权限范围");
        try {
            var frozen = objectMapper.readTree(run.getScoreConfigJson());
            String name = frozen.path("userName").asText("");
            var permissionsNode = frozen.path("permissions");
            if (name.isBlank() || !permissionsNode.isArray() || permissionsNode.isEmpty())
                throw new ServiceException("运行缺少可信的创建人权限");
            Set<String> permissions = new HashSet<>();
            permissionsNode.forEach(p -> {
                if (!p.isTextual() || p.asText().isBlank())
                    throw new ServiceException("运行权限数据无效");
                permissions.add(p.asText());
            });
            return new DecisionActor(run.getTenantId(), run.getCreateBy(), name, Set.copyOf(permissions));
        } catch (JsonProcessingException error) { throw new ServiceException("运行权限快照解析失败"); }
    }

    public AiDecisionRun requireRun(Long runId, String tenantId) {
        String tenant = requireTenant(tenantId);
        requireThreadTenant(tenant);
        if (runId == null || runId <= 0) throw new ServiceException("决策运行ID无效");
        AiDecisionRun row = runMapper.selectOne(Wrappers.<AiDecisionRun>lambdaQuery()
            .eq(AiDecisionRun::getId, runId).eq(AiDecisionRun::getTenantId, tenant)
            .eq(AiDecisionRun::getSceneCode, SCENE_CODE));
        if (row == null) throw new ServiceException("决策运行不存在或无权限访问");
        return row;
    }

    public void transition(Long runId, DecisionRunStatus expected, DecisionRunStatus target) {
        String tenant = currentTenant();
        stateMachine.requireTransition(expected, target);
        AiDecisionRun run = requireRun(runId, tenant);
        if (!expected.name().equals(run.getStatus())) throw new ServiceException("决策运行状态已变化");
        var update = baseUpdate(run, tenant).eq(AiDecisionRun::getStatus, expected.name())
            .set(AiDecisionRun::getStatus, target.name());
        LocalDateTime now = LocalDateTime.now();
        if (target == DecisionRunStatus.RUNNING) update.set(AiDecisionRun::getStartedAt, now);
        if (target.isTerminal()) update.set(AiDecisionRun::getFinishedAt, now);
        if (runMapper.update(null, update) != 1) throw new ServiceException("决策状态并发冲突");
    }

    /**
     * 分析流程可能在 startAnalysis() 返回与绑定实例标识之间完成。
     */
    public void attachAnalysisFlow(Long runId, Long flowInstanceId) {
        attachFlow(runId, flowInstanceId, true);
    }
    public void attachExecutionFlow(Long runId, Long flowInstanceId) {
        attachFlow(runId, flowInstanceId, false);
    }

    private void attachFlow(Long runId, Long flowInstanceId, boolean analysis) {
        String tenant = currentTenant();
        if (flowInstanceId == null || flowInstanceId <= 0) throw new ServiceException("Flow 实例ID无效");
        for (int attempt = 0; attempt < 3; attempt++) {
            AiDecisionRun run = requireRun(runId, tenant);
            Long current = analysis ? run.getAnalysisFlowInstanceId() : run.getExecutionFlowInstanceId();
            if (flowInstanceId.equals(current)) return;
            if (current != null) throw new ServiceException("决策运行已绑定其他 Flow 实例");
            if (analysis && DecisionRunStatus.CREATED.name().equals(run.getStatus()))
                throw new ServiceException("分析 Flow 不得在运行启动前绑定");
            if (!analysis && !Set.of("APPROVED", "EXECUTING", "SUCCEEDED", "PARTIAL_SUCCESS", "FAILED")
                .contains(run.getStatus())) throw new ServiceException("执行 Flow 绑定状态无效");
            var update = baseUpdate(run, tenant).isNull(analysis ? AiDecisionRun::getAnalysisFlowInstanceId
                : AiDecisionRun::getExecutionFlowInstanceId)
                .set(analysis ? AiDecisionRun::getAnalysisFlowInstanceId : AiDecisionRun::getExecutionFlowInstanceId,
                    flowInstanceId);
            if (runMapper.update(null, update) == 1) return;
        }
        throw new ServiceException("Flow 实例关联发生并发冲突");
    }

    /**
     * 保留未确认的执行流程标识供人工对账，不改写其状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordUnlinkedExecutionFlow(Long runId, Long flowInstanceId) {
        String tenant = currentTenant();
        if (flowInstanceId == null || flowInstanceId <= 0) throw new ServiceException("Flow 实例ID无效");
        AiDecisionRun run = requireRun(runId, tenant);
        if (flowInstanceId.equals(run.getExecutionFlowInstanceId())) return;
        if (run.getExecutionFlowInstanceId() != null)
            throw new ServiceException("决策运行已绑定其他执行 Flow 实例");
        var update = baseUpdate(run, tenant).eq(AiDecisionRun::getStatus, run.getStatus())
            .isNull(AiDecisionRun::getExecutionFlowInstanceId)
            .set(AiDecisionRun::getErrorCode, "ORPHAN_EXECUTION_FLOW")
            .set(AiDecisionRun::getErrorMessage, "未确认停止的执行 Flow 实例ID: " + flowInstanceId);
        if (runMapper.update(null, update) != 1)
            throw new ServiceException("未关联执行 Flow 实例的审计写入冲突");
    }

    @Transactional(rollbackFor = Exception.class)
    public void fail(Long runId, String errorCode, String sanitizedMessage) {
        String tenant = currentTenant();
        AiDecisionRun run = requireRun(runId, tenant);
        var status = DecisionRunStatus.valueOf(run.getStatus());
        stateMachine.requireTransition(status, DecisionRunStatus.FAILED);
        var update = baseUpdate(run, tenant).eq(AiDecisionRun::getStatus, run.getStatus())
            .set(AiDecisionRun::getStatus, DecisionRunStatus.FAILED.name())
            .set(AiDecisionRun::getErrorCode, safeCode(errorCode))
            .set(AiDecisionRun::getErrorMessage, safeMessage(sanitizedMessage))
            .set(AiDecisionRun::getFinishedAt, LocalDateTime.now());
        if (runMapper.update(null, update) != 1) throw new ServiceException("决策运行失败状态写入冲突");
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveSnapshot(Long runId, DeliveryContextSnapshot snapshot) {
        String tenant = currentTenant();
        if (snapshot == null || snapshot.rootBusinessRef() == null || snapshot.snapshotAt() == null
            || snapshot.snapshotHash() == null || snapshot.snapshotHash().isBlank())
            throw new ServiceException("决策快照不完整");
        AiDecisionRun run = requireRun(runId, tenant);
        if (!DecisionRunStatus.RUNNING.name().equals(run.getStatus())
            || !run.getBizType().equals(snapshot.rootBusinessRef().type().name())
            || !Objects.equals(run.getBizId(), snapshot.rootBusinessRef().id()))
            throw new ServiceException("决策快照不属于当前业务运行");
        var update = baseUpdate(run, tenant).eq(AiDecisionRun::getStatus, DecisionRunStatus.RUNNING.name())
            .set(AiDecisionRun::getInputSnapshotJson, json(snapshot))
            .set(AiDecisionRun::getInputSnapshotHash, snapshot.snapshotHash())
            .set(AiDecisionRun::getSnapshotTime, snapshot.snapshotAt());
        if (runMapper.update(null, update) != 1) throw new ServiceException("保存决策快照发生并发冲突");
    }

    public int requireHorizonDays(AiDecisionRun run) {
        return DecisionFrozenHorizon.requireHorizonDays(run);
    }

    public DeliveryContextSnapshot requireSnapshot(Long runId, String tenantId) {
        AiDecisionRun run = requireRun(runId, tenantId);
        if (run.getInputSnapshotJson() == null || run.getInputSnapshotHash() == null)
            throw new ServiceException("决策运行尚无可信业务快照");
        try { return objectMapper.readValue(run.getInputSnapshotJson(), DeliveryContextSnapshot.class); }
        catch (JsonProcessingException ex) { throw new ServiceException("决策快照无法解码"); }
    }

    @Transactional(rollbackFor = Exception.class)
    public void replaceEvidence(Long runId, List<EvidenceItem> items, DecisionActor actor) {
        String tenant = requireActor(actor);
        AiDecisionRun run = requireRun(runId, tenant);
        if (run.getInputSnapshotHash() == null || !DecisionRunStatus.RUNNING.name().equals(run.getStatus()))
            throw new ServiceException("快照缺失或决策运行状态不允许写证据");
        List<EvidenceItem> normalized = evidenceService.normalize(items);
        for (EvidenceItem item : normalized) {
            if (item.runId() != null && !runId.equals(item.runId())) throw new ServiceException("证据运行ID不匹配");
        }
        List<AiDecisionEvidence> existing = evidenceMapper.selectList(Wrappers.<AiDecisionEvidence>lambdaQuery()
            .eq(AiDecisionEvidence::getTenantId, tenant).eq(AiDecisionEvidence::getRunId, runId));
        if (!existing.isEmpty()) {
            Map<String, EvidenceItem> desired = new HashMap<>();
            normalized.forEach(item -> desired.put(item.evidenceKey(), item));
            if (existing.size() != desired.size() || existing.stream().anyMatch(row ->
                !sameEvidence(row, desired.get(row.getEvidenceKey()))))
                throw new ServiceException("证据已冻结，冲突事实必须开启新决策运行");
            return;
        }
        for (EvidenceItem item : normalized) {
            AiDecisionEvidence row = new AiDecisionEvidence();
            row.setTenantId(tenant); row.setRunId(runId); row.setEvidenceKey(item.evidenceKey());
            row.setSourceDomain(item.sourceDomain()); row.setSourceType(item.sourceType());
            row.setSourceId(item.sourceId()); row.setSourceCode(item.sourceCode()); row.setSourceName(item.sourceName());
            row.setMetricCode(item.metricCode()); row.setMetricName(item.metricName()); row.setValueType(item.valueType());
            row.setMetricValue(item.value()); row.setUnit(item.unit()); row.setObservedAt(item.observedAt());
            row.setDataVersion(item.dataVersion()); row.setConfidence(item.confidence());
            row.setVerifiable(item.verifiable() ? "Y" : "N"); row.setNormative(item.normative() ? "Y" : "N");
            row.setDerivedFromJson(json(item.derivedFromEvidenceKeys())); row.setDisplayPath(item.displayPath());
            row.setCreateBy(actor.userId()); row.setUpdateBy(actor.userId());
            if (evidenceMapper.insert(row) != 1) throw new ServiceException("证据写入失败");
        }
    }

    public List<AiDecisionEvidence> listEvidence(Long runId) {
        String tenant = currentTenant();
        requireRun(runId, tenant);
        return evidenceMapper.selectList(Wrappers.<AiDecisionEvidence>lambdaQuery()
            .eq(AiDecisionEvidence::getTenantId, tenant).eq(AiDecisionEvidence::getRunId, runId)
            .orderByAsc(AiDecisionEvidence::getEvidenceKey));
    }

    @Transactional(rollbackFor = Exception.class)
    public List<AiDecisionAction> saveActions(Long runId, List<DecisionActionProposal> proposals, DecisionActor actor) {
        String tenant = requireActor(actor);
        AiDecisionRun run = requireRun(runId, tenant);
        if (proposals == null || !Set.of("RUNNING", "WAITING_APPROVAL").contains(run.getStatus()))
            throw new ServiceException("当前决策状态不可写入动作");
        Set<String> keys = new HashSet<>();
        List<AiDecisionAction> saved = new ArrayList<>();
        for (DecisionActionProposal proposal : proposals) {
            if (proposal == null || proposal.target() == null || proposal.actionType() == null
                || !Objects.equals(proposal.target().type().name(), run.getBizType())
                || !Objects.equals(proposal.target().id(), run.getBizId())
                || proposal.idempotencyKey() == null || proposal.idempotencyKey().isBlank()
                || proposal.idempotencyKey().length() > 128 || !keys.add(proposal.idempotencyKey())
                || !Objects.equals(proposal.permissionCode(), proposal.actionType().permissionCode())
                || !Objects.equals(proposal.executorCode(), proposal.actionType().executorCode()))
                throw new ServiceException("决策动作来源、元数据或幂等键无效");
            if (proposal.actionType() == DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT)
                requirePurchaseContract(run, proposal, tenant);
            AiDecisionAction row = new AiDecisionAction();
            if (proposal.actionType() == DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT)
                row.setPurchasePayloadVersion(2);
            row.setTenantId(tenant); row.setRunId(runId); row.setActionType(proposal.actionType().name());
            row.setTitle(proposal.title()); row.setDisplayReason(proposal.displayReason());
            row.setTargetBizType(proposal.target().type().name()); row.setTargetBizId(proposal.target().id());
            row.setTargetBizCode(proposal.target().code()); row.setPayloadJson(json(proposal.payload()));
            row.setRiskLevel(proposal.riskLevel().name()); row.setApprovalRequired(proposal.approvalRequired() ? "Y" : "N");
            row.setApprovalStatus(DecisionApprovalStatus.PROPOSED.name());
            row.setExecutionStatus(DecisionExecutionStatus.NOT_STARTED.name());
            row.setPermissionCode(proposal.permissionCode()); row.setExecutorCode(proposal.executorCode());
            row.setSequenceNo(proposal.sequence()); row.setDependencyIdsJson(json(proposal.dependencyIds()));
            row.setIdempotencyKey(proposal.idempotencyKey()); row.setVersion(0);
            row.setCreateBy(actor.userId()); row.setUpdateBy(actor.userId());
            // 幂等键在整个租户范围内唯一，而非仅在单个运行内唯一。
            List<AiDecisionAction> existing = actionMapper.selectList(Wrappers.<AiDecisionAction>lambdaQuery()
                .eq(AiDecisionAction::getTenantId, tenant)
                .eq(AiDecisionAction::getIdempotencyKey, row.getIdempotencyKey()));
            if (!existing.isEmpty()) {
                if (existing.size() != 1 || !sameAction(existing.get(0), row))
                    throw new ServiceException("动作幂等键已被不同内容使用");
                saved.add(existing.get(0));
                continue;
            }
            if (actionMapper.insert(row) != 1 || row.getId() == null || row.getId() <= 0)
                throw new ServiceException("决策动作写入失败");
            saved.add(row);
        }
        return List.copyOf(saved);
    }

    public AiDecisionAction requireAction(Long actionId, String tenantId) {
        String tenant = requireTenant(tenantId);
        requireThreadTenant(tenant);
        if (actionId == null || actionId <= 0) throw new ServiceException("决策动作ID无效");
        AiDecisionAction action = actionMapper.selectOne(Wrappers.<AiDecisionAction>lambdaQuery()
            .eq(AiDecisionAction::getId, actionId).eq(AiDecisionAction::getTenantId, tenant));
        if (action == null) throw new ServiceException("决策动作不存在或不属于租户");
        requireRun(action.getRunId(), tenant);
        return action;
    }

    @Transactional(rollbackFor = Exception.class)
    public void setActionApproval(Long actionId, String expected, String target, DecisionActor actor, String comment) {
        String tenant = requireActor(actor);
        AiDecisionAction row = requireAction(actionId, tenant);
        if (!Set.of("PROPOSED", "PENDING_APPROVAL").contains(expected)
            || !Set.of("APPROVED", "REJECTED").contains(target)
            || !Objects.equals(row.getApprovalStatus(), expected))
            throw new ServiceException("决策动作审批状态发生冲突");
        var update = actionUpdate(row, tenant).eq(AiDecisionAction::getApprovalStatus, expected)
            .eq(AiDecisionAction::getExecutionStatus, "NOT_STARTED")
            .set(AiDecisionAction::getApprovalStatus, target).set(AiDecisionAction::getApprovedBy, actor.userId())
            .set(AiDecisionAction::getApprovedAt, LocalDateTime.now())
            .set(AiDecisionAction::getApprovalComment, safeMessage(comment));
        if (actionMapper.update(null, update) != 1) throw new ServiceException("动作审批并发冲突");
    }

    @Transactional(rollbackFor = Exception.class)
    public void transitionAction(Long actionId, String approval, String expected, String target) {
        String tenant = currentTenant();
        AiDecisionAction row = requireAction(actionId, tenant);
        if (!Objects.equals(approval, row.getApprovalStatus()) || !Objects.equals(expected, row.getExecutionStatus())
            || !Set.of("NOT_STARTED", "EXECUTING", "FAILED").contains(expected)
            || !Set.of("EXECUTING", "SUCCEEDED", "FAILED", "SKIPPED", "COMPENSATING").contains(target)
            || "COMPENSATING".equals(target) && !"FAILED".equals(expected))
            throw new ServiceException("动作执行状态发生冲突");
        var update = actionUpdate(row, tenant).eq(AiDecisionAction::getApprovalStatus, approval)
            .eq(AiDecisionAction::getExecutionStatus, expected)
            .set(AiDecisionAction::getExecutionStatus, target);
        if ("EXECUTING".equals(target)) update.set(AiDecisionAction::getStartedAt, LocalDateTime.now());
        if (Set.of("SUCCEEDED", "FAILED", "SKIPPED").contains(target))
            update.set(AiDecisionAction::getFinishedAt, LocalDateTime.now());
        if (actionMapper.update(null, update) != 1) throw new ServiceException("动作执行并发冲突");
    }

    /**
     * 仅补回已独立核验的永久采购结果，不重新创建、不回填旧合同。
     */
    @Transactional(rollbackFor = Exception.class)
    void saveRecoveredPurchaseResult(Long actionId, int expectedVersion, DecisionActionExecutionResult result) {
        String tenant = currentTenant();
        if (expectedVersion < 0) throw new ServiceException("采购恢复动作版本无效");
        AiDecisionAction row = requireAction(actionId, tenant);
        AiDecisionRun run = requireRun(row.getRunId(), tenant);
        RecoveryPurchaseContract contract = requireRecoverablePurchase(row, run, result);
        requireExistingRecoveredResult(row, result, contract, "SUCCEEDED".equals(row.getExecutionStatus()));
        if ("SUCCEEDED".equals(row.getExecutionStatus())) return;
        if (expectedVersion < 0 || !Objects.equals(row.getVersion(), expectedVersion)
            || !Set.of("EXECUTING", "FAILED").contains(row.getExecutionStatus()))
            throw new ServiceException("采购恢复动作状态或版本发生冲突");
        DecisionActionExecutionResult sanitized = new DecisionActionExecutionResult(true, false,
            result.resultBizType(), result.resultBizId(), result.resultBizCode(), sanitizeResult(result.result()), null, null);
        var update = actionUpdate(row, tenant)
            .eq(AiDecisionAction::getApprovalStatus, "APPROVED")
            .eq(AiDecisionAction::getExecutionStatus, row.getExecutionStatus())
            .eq(AiDecisionAction::getActionType, DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT.name())
            .eq(AiDecisionAction::getPurchasePayloadVersion, 2)
            .apply("EXISTS (SELECT 1 FROM ai_decision_run recovery_run WHERE recovery_run.id = {0} "
                + "AND recovery_run.tenant_id = {1} AND recovery_run.scene_code = {2} "
                + "AND recovery_run.status = {3} AND recovery_run.decision_contract_version = {4} "
                + "AND recovery_run.version = {5})", run.getId(), tenant, SCENE_CODE, "EXECUTING", 2, run.getVersion())
            .set(AiDecisionAction::getExecutionStatus, "SUCCEEDED")
            .set(AiDecisionAction::getResultBizType, sanitized.resultBizType())
            .set(AiDecisionAction::getResultBizId, sanitized.resultBizId())
            .set(AiDecisionAction::getResultBizCode, sanitized.resultBizCode())
            .set(AiDecisionAction::getResultJson, json(sanitized))
            .set(AiDecisionAction::getLastErrorCode, null).set(AiDecisionAction::getLastErrorMessage, null)
            .set(AiDecisionAction::getFinishedAt, LocalDateTime.now());
        int affected = actionMapper.update(null, update);
        if (affected == 1) return;
        if (affected > 1) throw new ServiceException("采购恢复更新影响多行动作，必须回滚");
        // 锁定读取得并发执行者的最新结果，不用可重复读的旧快照判定另一笔采购。
        AiDecisionAction latest = actionMapper.selectOne(Wrappers.<AiDecisionAction>lambdaQuery()
            .eq(AiDecisionAction::getId, actionId).eq(AiDecisionAction::getTenantId, tenant)
            .eq(AiDecisionAction::getRunId, run.getId()).last("FOR UPDATE"));
        AiDecisionRun current = runMapper.selectOne(Wrappers.<AiDecisionRun>lambdaQuery()
            .eq(AiDecisionRun::getId, run.getId()).eq(AiDecisionRun::getTenantId, tenant)
            .eq(AiDecisionRun::getSceneCode, SCENE_CODE).last("FOR UPDATE"));
        if (current == null) throw new ServiceException("采购恢复运行不存在或状态已变化");
        if (latest == null) throw new ServiceException("采购恢复回写发生并发冲突");
        RecoveryPurchaseContract currentContract = requireRecoverablePurchase(latest, current, result);
        if (!"SUCCEEDED".equals(latest.getExecutionStatus()))
            throw new ServiceException("采购恢复回写发生并发冲突");
        requireExistingRecoveredResult(latest, result, currentContract, true);
    }

    /**
     * 全部已批准动作都成功时才收敛；混合运行的未决动作保持执行中。
     */
    @Transactional(rollbackFor = Exception.class)
    boolean finishRecoveredExecution(Long runId, int expectedVersion) {
        String tenant = currentTenant();
        AiDecisionRun run = requireRun(runId, tenant);
        if (expectedVersion < 0 || !Objects.equals(run.getVersion(), expectedVersion)
            || !"EXECUTING".equals(run.getStatus()) || !Integer.valueOf(2).equals(run.getDecisionContractVersion()))
            return false;
        List<AiDecisionAction> all = actionMapper.selectList(Wrappers.<AiDecisionAction>lambdaQuery()
            .eq(AiDecisionAction::getTenantId, tenant).eq(AiDecisionAction::getRunId, runId)
            .orderByAsc(AiDecisionAction::getId).last("FOR UPDATE"));
        if (all == null || all.stream().anyMatch(row -> row == null
            || !runId.equals(row.getRunId()) || !tenant.equals(row.getTenantId()))) return false;
        List<AiDecisionAction> approved = all.stream().filter(row -> "APPROVED".equals(row.getApprovalStatus())).toList();
        if (approved.isEmpty() || approved.stream().anyMatch(row -> !"SUCCEEDED".equals(row.getExecutionStatus())))
            return false;
        stateMachine.requireTransition(DecisionRunStatus.EXECUTING, DecisionRunStatus.SUCCEEDED);
        int affected = runMapper.update(null, baseUpdate(run, tenant).eq(AiDecisionRun::getStatus, "EXECUTING")
            .eq(AiDecisionRun::getDecisionContractVersion, 2)
            .set(AiDecisionRun::getStatus, "SUCCEEDED").set(AiDecisionRun::getFinishedAt, LocalDateTime.now()));
        if (affected != 0 && affected != 1) throw new ServiceException("采购恢复运行更新行数异常，必须回滚");
        return affected == 1;
    }

    private RecoveryPurchaseContract requireRecoverablePurchase(AiDecisionAction row, AiDecisionRun run,
                                                                DecisionActionExecutionResult result) {
        if (!"EXECUTING".equals(run.getStatus()) || !Integer.valueOf(2).equals(run.getDecisionContractVersion())
            || !"APPROVED".equals(row.getApprovalStatus())
            || !DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT.name().equals(row.getActionType())
            || !Integer.valueOf(2).equals(row.getPurchasePayloadVersion())
            || !Objects.equals(row.getRunId(), run.getId()) || !Objects.equals(row.getTenantId(), run.getTenantId())
            || !Objects.equals(row.getTargetBizType(), run.getBizType())
            || !Objects.equals(row.getTargetBizId(), run.getBizId()))
            throw new ServiceException("采购恢复合同、批准状态或业务归属无效");
        try {
            Map<String, Object> payload = objectMapper.readerFor(new TypeReference<Map<String, Object>>() { })
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readValue(row.getPayloadJson());
            var frozen = DecisionPurchaseContractCodec.parse(payload, run.getId());
            DecisionBusinessRef target = new DecisionBusinessRef(DecisionBusinessType.valueOf(row.getTargetBizType()),
                row.getTargetBizId(), row.getTargetBizCode());
            if (!DecisionPurchaseContractCodec.idempotencyKey(run.getTenantId(), run.getId(), target, frozen)
                .equals(row.getIdempotencyKey())) throw new ServiceException("采购恢复永久键与冻结合同不一致");
            DecisionActor actor = trustedExecutionActor(run.getId(), run.getTenantId());
            if (!run.getTenantId().equals(actor.tenantId()) || !actor.permissions().contains("pu:requisition:add")
                && !actor.permissions().contains("*:*:*")
                || row.getApprovedBy() != null && !row.getApprovedBy().equals(actor.userId()))
                throw new ServiceException("采购恢复缺少原绑定执行身份或采购权限");
            // 只重建摘要实际使用的持久化字段，不签发新的动作或业务事实。
            DecisionActionProposal proposal = new DecisionActionProposal(row.getId(), DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,
                row.getTitle(), row.getDisplayReason(), target, payload, null, "Y".equals(row.getApprovalRequired()),
                row.getPermissionCode(), row.getIdempotencyKey(), row.getExecutorCode(),
                row.getSequenceNo() == null ? 0 : row.getSequenceNo(), List.of());
            RecoveryPurchaseContract contract = new RecoveryPurchaseContract(run.getId(), row.getId(), run.getTenantId(),
                row.getIdempotencyKey(), frozen, DecisionPurchaseContractCodec.payloadFingerprint(proposal, actor));
            requireRecoveredEnvelope(result, contract);
            return contract;
        } catch (JsonProcessingException | IllegalArgumentException invalid) {
            throw new ServiceException("采购恢复缺少完整冻结合同或绑定身份");
        }
    }

    /** 每次更新及竞争重读都独立验证完整信封，不能靠上游核验或成功旗号放行。 */
    private void requireRecoveredEnvelope(DecisionActionExecutionResult result, RecoveryPurchaseContract contract) {
        if (result == null || !result.success() || result.retryable() || result.errorCode() != null || result.errorMessage() != null
            || !"PU_PURCHASE_REQUISITION".equals(result.resultBizType()) || result.resultBizId() == null || result.resultBizId() <= 0
            || result.resultBizCode() == null || !result.resultBizCode().matches("[A-Za-z0-9_-]{1,64}"))
            throw new ServiceException("采购恢复必须具有真实原稿业务引用和成功信封");
        Map<String, Object> details = result.result();
        if (details == null || !PURCHASE_RESULT_FIELDS.equals(details.keySet()) || !(details.get("replayed") instanceof Boolean replayed)
            || !Integer.valueOf(2).equals(details.get("purchase_payload_version"))
            || !contract.runId().toString().equals(details.get("runId"))
            || !contract.actionId().toString().equals(details.get("actionId"))
            || !contract.tenantId().equals(details.get("tenantId")) || !contract.requestKey().equals(details.get("requestKey"))
            || !contract.frozen().materialId().toString().equals(details.get("materialId"))
            || !contract.frozen().vendorId().toString().equals(details.get("vendorId"))
            || !"AI_DECISION".equals(details.get("sourceType"))
            || !contract.frozen().expectedSourceFingerprint().equals(details.get("sourceFingerprint"))
            || !contract.payloadFingerprint().equals(details.get("payloadFingerprint"))
            || !"DRAFT".equals(details.get("status")) || !"DRAFT".equals(details.get("afterState"))
            || !"v0".equals(details.get("versionMarker"))
            || !(replayed ? "COMMITTED" : "ABSENT").equals(details.get("beforeState")))
            throw new ServiceException("采购恢复信封的来源、合同、版本或观察状态不一致");
        if (!(details.get("lines") instanceof List<?> lines) || lines.size() != contract.frozen().frozenLines().size())
            throw new ServiceException("采购恢复逐来源行缺失或数量不一致");
        Set<Long> ids = new HashSet<>();
        for (int index = 0; index < lines.size(); index++) {
            if (!(lines.get(index) instanceof Map<?, ?> row) || !PURCHASE_RESULT_LINE_FIELDS.equals(row.keySet()))
                throw new ServiceException("采购恢复结果行缺少字段或含未知字段");
            Long lineId = requireResultLineId(row.get("lineId"));
            var line = contract.frozen().frozenLines().get(index);
            Map<String, Object> expected = Map.of("lineId", lineId.toString(), "requirementDetailId", line.detailId().toString(),
                "materialId", contract.frozen().materialId().toString(), "baseUomId", line.baseUomId().toString(),
                "orderUomId", line.orderUomId().toString(), "coveredDemandBase", line.coveredDemandBase().stripTrailingZeros().toPlainString(),
                "requestedOrderQty", line.requestedOrderQty().stripTrailingZeros().toPlainString(), "requiredDate", line.requiredDate().toString());
            if (!ids.add(lineId) || !expected.equals(row))
                throw new ServiceException("采购恢复行标识、单位、数量或日期与冻结合同不一致");
        }
    }

    private static Long requireResultLineId(Object raw) {
        if (!(raw instanceof String id) || !id.matches("[1-9][0-9]{0,18}"))
            throw new ServiceException("采购恢复行标识必须为规范正十进制字符串");
        try { return Long.valueOf(id); }
        catch (NumberFormatException invalid) { throw new ServiceException("采购恢复行标识超出有效范围"); }
    }

    /** 有任何历史引用或结果就必须完整证明同一笔采购，不清空、不覆盖残缺记录。 */
    private void requireExistingRecoveredResult(AiDecisionAction row, DecisionActionExecutionResult result,
                                               RecoveryPurchaseContract contract, boolean required) {
        boolean existing = row.getResultBizType() != null || row.getResultBizId() != null
            || row.getResultBizCode() != null || row.getResultJson() != null;
        if (!existing && !required) return;
        if (!Objects.equals(row.getResultBizType(), result.resultBizType())
            || !Objects.equals(row.getResultBizId(), result.resultBizId()) || !Objects.equals(row.getResultBizCode(), result.resultBizCode())
            || row.getResultJson() == null)
            throw new ServiceException("采购已有业务引用不完整或不是同一永久单据");
        try {
            JsonNode tree = objectMapper.reader().with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readTree(row.getResultJson());
            Set<String> fields = new HashSet<>();
            if (tree == null || !tree.isObject()) throw new ServiceException("采购已有结果缺少完整可复核回执");
            tree.fieldNames().forEachRemaining(fields::add);
            if (!EXECUTION_RESULT_FIELDS.equals(fields) || !tree.path("success").isBoolean() || !tree.path("retryable").isBoolean()
                || !tree.path("resultBizType").isTextual() || !tree.path("resultBizCode").isTextual()
                || !(tree.path("resultBizId").isTextual() || tree.path("resultBizId").isIntegralNumber()))
                throw new ServiceException("采购已有结果字段未知、不完整或类型无效");
            DecisionActionExecutionResult stored = objectMapper.treeToValue(tree, DecisionActionExecutionResult.class);
            requireRecoveredEnvelope(stored, contract);
            if (!Objects.equals(stored.resultBizType(), result.resultBizType())
                || !Objects.equals(stored.resultBizId(), result.resultBizId()) || !Objects.equals(stored.resultBizCode(), result.resultBizCode())
                || !sameJson(json(stablePurchaseResult(stored.result())), json(stablePurchaseResult(result.result()))))
                throw new ServiceException("采购已有信封不是同一永久单据的稳定结果");
            if (tree.path("resultBizId").isTextual() && !stored.resultBizId().toString().equals(tree.path("resultBizId").asText()))
                throw new ServiceException("采购已有业务标识不是规范十进制");
        } catch (JsonProcessingException | IllegalArgumentException invalid) {
            throw new ServiceException("采购已有结果缺少完整可复核回执");
        }
    }

    private record RecoveryPurchaseContract(Long runId, Long actionId, String tenantId, String requestKey,
                                            DecisionPurchaseContractV2 frozen, String payloadFingerprint) { }

    private Map<String, Object> stablePurchaseResult(Map<String, Object> source) {
        if (source == null || source.isEmpty()) throw new ServiceException("永久采购结果缺少稳定合同字段");
        Map<String, Object> stable = new LinkedHashMap<>(source);
        stable.remove("replayed"); stable.remove("beforeState");
        return stable;
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveActionResult(Long actionId, DecisionActionExecutionResult result, int attempts) {
        String tenant = currentTenant();
        AiDecisionAction row = requireAction(actionId, tenant);
        if (!"EXECUTING".equals(row.getExecutionStatus()) || !"APPROVED".equals(row.getApprovalStatus())
            || result == null || attempts < 1 || attempts > 3)
            throw new ServiceException("动作结果与执行状态不匹配");
        DecisionActionExecutionResult sanitized = new DecisionActionExecutionResult(result.success(), result.retryable(),
            result.resultBizType(), result.resultBizId(), result.resultBizCode(),
            sanitizeResult(result.result()), result.success() ? null : safeCode(result.errorCode()),
            result.success() ? null : safeMessage(result.errorMessage()));
        var update = actionUpdate(row, tenant).eq(AiDecisionAction::getExecutionStatus, "EXECUTING")
            .eq(AiDecisionAction::getApprovalStatus, "APPROVED")
            .set(AiDecisionAction::getRetryCount, attempts - 1)
            .set(AiDecisionAction::getExecutionStatus, result.success() ? "SUCCEEDED" : "FAILED")
            .set(AiDecisionAction::getResultBizType, result.resultBizType())
            .set(AiDecisionAction::getResultBizId, result.resultBizId())
            .set(AiDecisionAction::getResultBizCode, result.resultBizCode())
            .set(AiDecisionAction::getResultJson, json(sanitized))
            .set(AiDecisionAction::getLastErrorCode, result.success() ? null : safeCode(result.errorCode()))
            .set(AiDecisionAction::getLastErrorMessage, result.success() ? null : safeMessage(result.errorMessage()))
            .set(AiDecisionAction::getFinishedAt, LocalDateTime.now());
        if (actionMapper.update(null, update) != 1) throw new ServiceException("动作结果并发写入冲突");
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveCompensationResult(Long actionId, DecisionActionExecutionResult result) {
        String tenant = currentTenant();
        AiDecisionAction row = requireAction(actionId, tenant);
        if (!"COMPENSATING".equals(row.getExecutionStatus()) || result == null)
            throw new ServiceException("补偿结果与当前动作状态不匹配");
        DecisionActionExecutionResult safe = new DecisionActionExecutionResult(result.success(), false,
            result.resultBizType(), result.resultBizId(), result.resultBizCode(), sanitizeResult(result.result()),
            result.success() ? null : safeCode(result.errorCode()),
            result.success() ? null : safeMessage(result.errorMessage()));
        var update = actionUpdate(row, tenant).eq(AiDecisionAction::getExecutionStatus, "COMPENSATING")
            .eq(AiDecisionAction::getApprovalStatus, "APPROVED")
            .set(AiDecisionAction::getExecutionStatus, result.success() ? "COMPENSATED" : "COMPENSATE_FAILED")
            .set(AiDecisionAction::getResultJson, json(safe))
            .set(AiDecisionAction::getLastErrorCode, safe.errorCode())
            .set(AiDecisionAction::getLastErrorMessage, safe.errorMessage())
            .set(AiDecisionAction::getFinishedAt, LocalDateTime.now());
        if (actionMapper.update(null, update) != 1) throw new ServiceException("补偿结果并发写入冲突");
    }

    private Map<String, Object> sanitizeResult(Map<String, Object> result) {
        if (result == null) return Map.of();
        Map<String, Object> safe = new LinkedHashMap<>();
        result.forEach((key, value) -> safe.put(key, sanitizeValue(value)));
        return safe;
    }
    private Object sanitizeValue(Object value) {
        if (value instanceof String text) return safeMessage(text);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> safe = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                if (!(key instanceof String text)) throw new ServiceException("动作结果键类型无效");
                safe.put(text, sanitizeValue(item));
            });
            return safe;
        }
        if (value instanceof List<?> values) return values.stream().map(this::sanitizeValue).toList();
        if (value == null || value instanceof Boolean || value instanceof Number) return value;
        throw new ServiceException("动作结果含不支持的字段");
    }

    private com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AiDecisionAction> actionUpdate(
        AiDecisionAction row, String tenant) {
        if (row.getVersion() == null || row.getVersion() < 0) throw new ServiceException("动作版本无效");
        return Wrappers.<AiDecisionAction>lambdaUpdate().eq(AiDecisionAction::getId, row.getId())
            .eq(AiDecisionAction::getTenantId, tenant).eq(AiDecisionAction::getRunId, row.getRunId())
            .eq(AiDecisionAction::getVersion, row.getVersion())
            .set(AiDecisionAction::getVersion, row.getVersion() + 1);
    }

    private boolean sameEvidence(AiDecisionEvidence row, EvidenceItem item) {
        return item != null
            && Objects.equals(row.getSourceDomain(), item.sourceDomain())
            && Objects.equals(row.getSourceType(), item.sourceType())
            && Objects.equals(row.getSourceId(), item.sourceId())
            && Objects.equals(row.getSourceCode(), item.sourceCode())
            && Objects.equals(row.getSourceName(), item.sourceName())
            && Objects.equals(row.getMetricCode(), item.metricCode())
            && Objects.equals(row.getMetricName(), item.metricName())
            && Objects.equals(row.getValueType(), item.valueType())
            && Objects.equals(row.getMetricValue(), item.value())
            && Objects.equals(row.getUnit(), item.unit())
            && Objects.equals(secondPrecision(row.getObservedAt()), secondPrecision(item.observedAt()))
            && Objects.equals(row.getDataVersion(), item.dataVersion())
            && sameDecimal(row.getConfidence(), item.confidence())
            && Objects.equals(row.getVerifiable(), item.verifiable() ? "Y" : "N")
            && Objects.equals(row.getNormative(), item.normative() ? "Y" : "N")
            && sameJson(row.getDerivedFromJson(), json(item.derivedFromEvidenceKeys()))
            && Objects.equals(row.getDisplayPath(), item.displayPath());
    }

    private static LocalDateTime secondPrecision(LocalDateTime time) {
        return time == null ? null : time.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    }
    private static boolean sameDecimal(BigDecimal left, BigDecimal right) {
        return left == null || right == null ? left == right : left.compareTo(right) == 0;
    }

    private void requirePurchaseContract(AiDecisionRun run, DecisionActionProposal proposal, String tenant) {
        if (!Integer.valueOf(2).equals(run.getDecisionContractVersion()) || proposal.target() == null
            || proposal.target().type() != DecisionBusinessType.WORK_ORDER || !proposal.approvalRequired())
            throw new ServiceException("采购合同版本、工单目标或审批约束无效，须重新分析");
        var contract = DecisionPurchaseContractCodec.parse(proposal.payload(), run.getId());
        if (!DecisionPurchaseContractCodec.idempotencyKey(tenant, run.getId(), proposal.target(), contract)
            .equals(proposal.idempotencyKey())) throw new ServiceException("采购永久键与冻结合同不一致");
    }

    private boolean sameAction(AiDecisionAction old, AiDecisionAction desired) {
        return Objects.equals(old.getRunId(), desired.getRunId())
            && Objects.equals(old.getActionType(), desired.getActionType())
            && Objects.equals(old.getPurchasePayloadVersion(), desired.getPurchasePayloadVersion())
            && Objects.equals(old.getTitle(), desired.getTitle())
            && Objects.equals(old.getDisplayReason(), desired.getDisplayReason())
            && Objects.equals(old.getTargetBizType(), desired.getTargetBizType())
            && Objects.equals(old.getTargetBizId(), desired.getTargetBizId())
            && Objects.equals(old.getTargetBizCode(), desired.getTargetBizCode())
            && sameJson(old.getPayloadJson(), desired.getPayloadJson())
            && Objects.equals(old.getRiskLevel(), desired.getRiskLevel())
            && Objects.equals(old.getApprovalRequired(), desired.getApprovalRequired())
            && Objects.equals(old.getPermissionCode(), desired.getPermissionCode())
            && Objects.equals(old.getExecutorCode(), desired.getExecutorCode())
            && Objects.equals(old.getSequenceNo(), desired.getSequenceNo())
            && sameJson(old.getDependencyIdsJson(), desired.getDependencyIdsJson());
    }

    private boolean matchesProposal(AiDecisionAction row, DecisionActionProposal proposal, Long runId) {
        return proposal != null && proposal.target() != null && proposal.actionType() != null
            && proposal.riskLevel() != null && Objects.equals(row.getRunId(), runId)
            && Objects.equals(row.getActionType(), proposal.actionType().name())
            && (proposal.actionType() != DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT
                || Integer.valueOf(2).equals(row.getPurchasePayloadVersion()))
            && Objects.equals(row.getTargetBizType(), proposal.target().type().name())
            && Objects.equals(row.getTargetBizId(), proposal.target().id())
            && Objects.equals(row.getTargetBizCode(), proposal.target().code())
            && Objects.equals(row.getTitle(), proposal.title())
            && Objects.equals(row.getDisplayReason(), proposal.displayReason())
            && sameJson(row.getPayloadJson(), json(proposal.payload()))
            && Objects.equals(row.getRiskLevel(), proposal.riskLevel().name())
            && Objects.equals(row.getApprovalRequired(), proposal.approvalRequired() ? "Y" : "N")
            && Objects.equals(row.getPermissionCode(), proposal.permissionCode())
            && Objects.equals(row.getExecutorCode(), proposal.executorCode())
            && Objects.equals(row.getSequenceNo(), proposal.sequence())
            && sameJson(row.getDependencyIdsJson(), json(proposal.dependencyIds()));
    }

    private boolean sameJson(String left, String right) {
        try { return Objects.equals(objectMapper.readTree(left), objectMapper.readTree(right)); }
        catch (JsonProcessingException | IllegalArgumentException e) { return false; }
    }

    public List<AiDecisionAction> listActions(Long runId) {
        String tenant = currentTenant(); requireRun(runId, tenant);
        return actionMapper.selectList(Wrappers.<AiDecisionAction>lambdaQuery()
            .eq(AiDecisionAction::getTenantId, tenant).eq(AiDecisionAction::getRunId, runId)
            .orderByAsc(AiDecisionAction::getSequenceNo));
    }

    @Transactional(rollbackFor = Exception.class)
    // 仅编排器可定稿，必须先独立重读 ERP 事实并验证证据。
    void completeAnalysis(Long runId, DecisionResult result, String summaryMode) {
        String tenant = currentTenant();
        AiDecisionRun run = requireRun(runId, tenant);
        if (result == null || !Objects.equals(runId, result.runId()) || result.businessRef() == null
            || !result.businessRef().type().name().equals(run.getBizType())
            || !Objects.equals(result.businessRef().id(), run.getBizId())
            || !DecisionRunStatus.RUNNING.name().equals(run.getStatus()))
            throw new ServiceException("决策分析不属于当前运行或状态已变化");
        evidenceService.assertNumericEvidenceCoverage(result);
        List<EvidenceItem> normalized = evidenceService.normalize(result.evidence());
        Map<String, EvidenceItem> claimed = new HashMap<>();
        normalized.forEach(item -> claimed.put(item.evidenceKey(), item));
        List<AiDecisionEvidence> persisted = listEvidence(runId);
        if (persisted.size() != claimed.size() || persisted.stream().anyMatch(row ->
            !Objects.equals(row.getRunId(), runId) || !sameEvidence(row, claimed.get(row.getEvidenceKey()))))
            throw new ServiceException("已落库的证据与决策结果不一致");
        List<DecisionActionProposal> proposed = result.actions() == null ? List.of() : result.actions();
        Map<String, DecisionActionProposal> byKey = new HashMap<>();
        for (DecisionActionProposal action : proposed) {
            if (action == null || action.idempotencyKey() == null
                || byKey.putIfAbsent(action.idempotencyKey(), action) != null)
                throw new ServiceException("决策动作键无效或重复");
            if (action.actionType() == DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT)
                requirePurchaseContract(run, action, tenant);
        }
        List<AiDecisionAction> persistedActions = listActions(runId);
        if (persistedActions.size() != byKey.size() || persistedActions.stream().anyMatch(row ->
            !matchesProposal(row, byKey.get(row.getIdempotencyKey()), runId)))
            throw new ServiceException("已落库的动作与决策结果不一致");
        DecisionRunStatus target = result.riskLevel() == DecisionRiskLevel.LOW
            && (result.actions() == null || result.actions().isEmpty())
            ? DecisionRunStatus.SUCCEEDED : DecisionRunStatus.WAITING_APPROVAL;
        stateMachine.requireTransition(DecisionRunStatus.RUNNING, target);
        if (run.getInputSnapshotHash() == null || run.getInputSnapshotJson() == null)
            throw new ServiceException("缺少可复核的业务快照");
        var update = baseUpdate(run, tenant).eq(AiDecisionRun::getStatus, DecisionRunStatus.RUNNING.name())
            .set(AiDecisionRun::getStatus, target.name()).set(AiDecisionRun::getRiskScore, BigDecimal.valueOf(result.riskScore()))
            .set(AiDecisionRun::getRiskLevel, result.riskLevel().name())
            .set(AiDecisionRun::getConfidenceLevel, result.confidenceLevel())
            .set(AiDecisionRun::getRecommendedScenarioCode, result.recommendedScenarioCode())
            .set(AiDecisionRun::getScenarioResultJson, json(result.scenarios()))
            .set(AiDecisionRun::getNarrativeJson, json(result.narrative()))
            .set(AiDecisionRun::getResultJson, json(result))
            .set(AiDecisionRun::getSummaryMode, summaryMode)
            .set(AiDecisionRun::getAnalysisFinishedAt, LocalDateTime.now());
        if (target.isTerminal()) update.set(AiDecisionRun::getFinishedAt, LocalDateTime.now());
        if (runMapper.update(null, update) != 1) throw new ServiceException("决策分析结果并发写入冲突");
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveOutcome(Long runId, DecisionOutcome outcome) {
        String tenant = currentTenant();
        AiDecisionRun run = requireRun(runId, tenant);
        if (outcome == null || !Set.of("SUCCEEDED", "PARTIAL_SUCCESS", "FAILED", "ROLLED_BACK").contains(run.getStatus()))
            throw new ServiceException("只有终态决策可写入复盘结果");
        if (runMapper.update(null, baseUpdate(run, tenant).eq(AiDecisionRun::getStatus, run.getStatus())
            .set(AiDecisionRun::getOutcomeJson, json(outcome))) != 1)
            throw new ServiceException("复盘结果并发写入冲突");
    }

    public void linkReplacement(Long oldRunId, Long newRunId) {
        String tenant = currentTenant();
        AiDecisionRun old = requireRun(oldRunId, tenant);
        requireRun(newRunId, tenant);
        var update = baseUpdate(old, tenant).isNull(AiDecisionRun::getSupersededByRunId)
            .set(AiDecisionRun::getSupersededByRunId, newRunId);
        if (runMapper.update(null, update) != 1) throw new ServiceException("旧运行的替代关系写入冲突");
    }

    static String safeMessage(String raw) {
        if (raw == null) return "处理失败，请查看管理员审计日志";
        String redacted = SECRETS.matcher(raw).replaceAll("[REDACTED]");
        return redacted.length() <= 1000 ? redacted : redacted.substring(0, 1000);
    }
    private static String safeCode(String raw) {
        return raw != null && raw.matches("[A-Z][A-Z0-9_]{0,63}") ? raw : "DECISION_FAILED";
    }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new ServiceException("决策数据无法序列化"); }
    }
    private com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AiDecisionRun> baseUpdate(
        AiDecisionRun run, String tenant) {
        if (run.getVersion() == null || run.getVersion() < 0) throw new ServiceException("决策运行版本无效");
        return Wrappers.<AiDecisionRun>lambdaUpdate()
            .eq(AiDecisionRun::getId, run.getId())
            .eq(AiDecisionRun::getTenantId, tenant)
            .eq(AiDecisionRun::getVersion, run.getVersion())
            .set(AiDecisionRun::getVersion, run.getVersion() + 1);
    }
    private static String requireTenant(String value) {
        if (value == null || value.isBlank()) throw new ServiceException("工业决策租户不能为空");
        if (!TenantHelper.isEnable()) throw new ServiceException("工业决策必须开启租户隔离");
        return value;
    }
    private static void requireThreadTenant(String tenant) {
        if (!tenant.equals(TenantHelper.getDynamic())) throw new ServiceException("决策线程租户上下文不匹配");
    }
    private static String currentTenant() {
        String tenant = requireTenant(TenantHelper.getDynamic()); requireThreadTenant(tenant); return tenant;
    }
    private static String requireActor(DecisionActor actor) {
        if (actor == null || actor.userId() == null || actor.userId() <= 0) throw new ServiceException("决策执行人无效");
        String tenant = requireTenant(actor.tenantId()); requireThreadTenant(tenant); return tenant;
    }
}
