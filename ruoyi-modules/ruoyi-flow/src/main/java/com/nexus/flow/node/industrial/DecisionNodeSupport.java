package com.nexus.flow.node.industrial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.service.decision.DecisionPersistenceService;
import com.nexus.ai.service.decision.DecisionPurchaseContractGuard;
import com.nexus.ai.service.decision.DecisionActionExecutionService.PurchaseExecutionPendingException;
import com.nexus.ai.service.decision.DecisionActionExecutionService.InitialExecutionCheckUnavailableException;
import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.ai.decision.enums.DecisionRunStatus;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.ai.decision.exception.DecisionEvidenceException;
import com.nexus.common.core.service.PermissionService;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.ai.service.sse.AiDecisionSseMessageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 每个工作线程根据租户内持久化运行重建可信身份。
 */
@Component
@Slf4j
public class DecisionNodeSupport {
    private final DecisionPersistenceService persistence;
    private final IndustrialDecisionDataPort dataPort;
    private final ObjectMapper mapper;
    private final AiDecisionSseMessageHelper events;
    private final PermissionService permissionService;

    @Autowired
    public DecisionNodeSupport(DecisionPersistenceService persistence, IndustrialDecisionDataPort dataPort,
                               ObjectMapper mapper, AiDecisionSseMessageHelper events,
                               PermissionService permissionService) {
        this.persistence = persistence; this.dataPort = dataPort; this.mapper = mapper;
        this.events = events; this.permissionService = permissionService;
    }
    DecisionNodeSupport(DecisionPersistenceService persistence, IndustrialDecisionDataPort dataPort,
                        ObjectMapper mapper, AiDecisionSseMessageHelper events) {
        this(persistence, dataPort, mapper, events, null);
    }
    DecisionNodeSupport(DecisionPersistenceService persistence, IndustrialDecisionDataPort dataPort,
                        ObjectMapper mapper) {
        this(persistence, dataPort, mapper, null, null);
    }

    public long requireRunId(FlowExecutionContext ctx) {
        if (ctx == null || ctx.getState() == null || ctx.getState().getInput() == null) throw new ServiceException("缺少运行输入");
        JsonNode id = ctx.getState().getInput().path("runId");
        if (!id.isTextual() || !id.asText().matches("[1-9][0-9]{0,18}")) throw new ServiceException("运行ID必须为十进制字符串");
        try { return Long.parseLong(id.asText()); }
        catch (NumberFormatException ex) { throw new ServiceException("运行ID无效"); }
    }

    public DecisionActor actor(FlowExecutionContext ctx) {
        if (ctx == null || ctx.getTenantId() == null || !ctx.getTenantId().matches("[A-Za-z0-9_-]{1,20}"))
            throw new ServiceException("工业节点缺少受信任的租户");
        String tenant = ctx.getTenantId();
        long id = requireRunId(ctx);
        return TenantHelper.dynamic(tenant, () -> {
            AiDecisionRun run = persistence.requireRun(id, tenant);
            if (!tenant.equals(run.getTenantId()) || run.getBizType() == null)
                throw new ServiceException("运行租户或业务对象不匹配");
            DecisionActor actor = "industrial.executeApprovedActions".equals(ctx.getNodeType())
                ? persistence.trustedExecutionActor(id, tenant) : persistence.trustedActor(id, tenant);
            if (permissionService != null) {
                java.util.Set<String> current;
                try { current = permissionService.getMenuPermission(actor.userId()); }
                catch (RuntimeException invalid) { throw new ServiceException("无法验证当前决策权限"); }
                if (current == null) throw new ServiceException("决策权限已失效");
                java.util.Set<String> effective = new java.util.HashSet<>();
                if (actor.permissions().contains("*:*:*")) effective.addAll(current);
                else if (current.contains("*:*:*")) effective.addAll(actor.permissions());
                else { effective.addAll(actor.permissions()); effective.retainAll(current); }
                String required = "industrial.executeApprovedActions".equals(ctx.getNodeType())
                    ? "ai:decision:execute" : "ai:decision:run";
                if (!effective.contains(required) && !effective.contains("*:*:*"))
                    throw new ServiceException("决策流程权限已撤销");
                actor = new DecisionActor(tenant, actor.userId(), actor.userName(), java.util.Set.copyOf(effective));
            }
            if (!tenant.equals(actor.tenantId()) || !dataPort.canAccess(
                new DecisionBusinessRef(DecisionBusinessType.valueOf(run.getBizType()), run.getBizId(), run.getBizCode()), actor))
                throw new ServiceException("工业节点业务访问已失效");
            return actor;
        });
    }

    public <T> T read(JsonNode node, Class<T> type) {
        try { return mapper.treeToValue(node, type); }
        catch (Exception error) { throw new ServiceException("工业节点输入不合法"); }
    }

    public com.nexus.ai.service.decision.DecisionPolicyGuardService.DecisionNarrative readNarrative(JsonNode node) {
        if (node == null || !node.isObject()) throw new ServiceException("缺少结构化决策摘要");
        var safe = ((com.fasterxml.jackson.databind.node.ObjectNode) node).deepCopy();
        safe.remove("summaryMode"); // 服务端元数据不是叙述字段。
        return read(safe, com.nexus.ai.service.decision.DecisionPolicyGuardService.DecisionNarrative.class);
    }

    public JsonNode write(Object body) { return mapper.valueToTree(body); }

    private void markUntrustedActorFailure(FlowExecutionContext ctx, long runId) {
        String tenant = ctx == null ? null : ctx.getTenantId();
        if (tenant == null || !tenant.matches("[A-Za-z0-9_-]{1,20}")) return;
        try {
            TenantHelper.dynamic(tenant, () -> {
                AiDecisionRun run = persistence.requireRun(runId, tenant);
                if (!tenant.equals(run.getTenantId())) return; // 不得修改其他租户的运行。
                if (DecisionRunStatus.RUNNING.name().equals(run.getStatus()))
                    persistence.fail(runId, "DECISION_NODE_FAILED", "工业决策节点执行失败");
                else if (DecisionRunStatus.APPROVED.name().equals(run.getStatus())
                    && !(isExecutionNode(ctx) && Integer.valueOf(2).equals(run.getDecisionContractVersion())))
                    persistence.transition(runId, DecisionRunStatus.APPROVED, DecisionRunStatus.STALE);
            });
        } catch (RuntimeException concurrent) {
            log.warn("Cannot close decision run after worker identity denial: runId={}", runId, concurrent);
        }
    }

    private boolean isExecutionNode(FlowExecutionContext ctx) {
        return ctx != null && "industrial.executeApprovedActions".equals(ctx.getNodeType());
    }

    /** 只认明确的执行边界信号与持久化新版状态，不按错误消息猜测，也不豁免普通分析或非采购错误。 */
    private boolean preserveExecutionCheckpoint(FlowExecutionContext ctx, long runId, DecisionActor actor, RuntimeException error) {
        if (!isExecutionNode(ctx) || !(error instanceof PurchaseExecutionPendingException
            || error instanceof InitialExecutionCheckUnavailableException)) return false;
        try {
            AiDecisionRun run = persistence.requireRun(runId, actor.tenantId());
            if (!Integer.valueOf(2).equals(run.getDecisionContractVersion())) return false;
            if (error instanceof InitialExecutionCheckUnavailableException && DecisionRunStatus.APPROVED.name().equals(run.getStatus()))
                return true;
            if (!(error instanceof PurchaseExecutionPendingException || error instanceof InitialExecutionCheckUnavailableException)
                || !DecisionRunStatus.EXECUTING.name().equals(run.getStatus()))
                return false;
            var actions = persistence.listActions(runId);
            if (actions == null) return true; // 已有明确待确认信号，诊断读取未知不能被解释为业务失败。
            boolean found = false;
            for (var action : actions) {
                if (action == null) return true;
                if (!"APPROVED".equals(action.getApprovalStatus())
                    || !DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT.name().equals(action.getActionType())) continue;
                DecisionPurchaseContractGuard.requireExecutable(run, action);
                found = true;
            }
            return found;
        } catch (RuntimeException unavailable) {
            // 这里只处理真实执行服务的有限信号；包装层诊断失败只停节点，不能另写终态。
            return true;
        }
    }

    public JsonNode output(FlowExecutionContext ctx, Supplier<?> body) {
        return output(ctx, ignored -> body.get());
    }

    public JsonNode output(FlowExecutionContext ctx,
                           java.util.function.Function<DecisionActor, ?> body) {
        long runId = requireRunId(ctx);
        DecisionActor actor;
        try { actor = actor(ctx); }
        catch (RuntimeException invalid) {
            markUntrustedActorFailure(ctx, runId);
            throw new ServiceException("工业节点身份或权限校验失败");
        }
        String code = ctx.getNodeId() == null ? ctx.getNodeType() : ctx.getNodeId();
        String name = ctx.getNodeName() == null ? code : ctx.getNodeName();
        if (events != null) events.sendNodeStart(actor.userId(), runId, 0, code, name);
        long start = System.nanoTime();
        return TenantHelper.dynamic(actor.tenantId(), () -> {
            try {
                JsonNode output = write(body.apply(actor));
                if (events != null) events.sendNodeEnd(actor.userId(), runId, 0, code, name, "SUCCEEDED",
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), "工业节点计算完成");
                return output;
            } catch (RuntimeException error) {
                diagnoseFailure(error);
                // 仅公开有限且不可继承的领域类型，不从消息或原因推断错误码。
                var reason = error instanceof DecisionEvidenceException rejected ? rejected.reason() : null;
                String failureCode = reason == null ? "DECISION_NODE_FAILED" : reason.code();
                String failureMessage = reason == null ? "工业决策节点执行失败" : reason.publicMessage();
                if (!preserveExecutionCheckpoint(ctx, runId, actor, error)) {
                    try { persistence.fail(runId, failureCode, failureMessage); }
                    catch (RuntimeException ignored) { /* 其他节点可能已将运行置为终态。 */ }
                }
                if (events != null) {
                    try { events.sendNodeEnd(actor.userId(), runId, 0, code, name, "FAILED",
                        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start), failureMessage); }
                    catch (RuntimeException ignored) { /* 传输失败不得替代已持久化的拒绝结果。 */ }
                    try { events.sendError(actor.userId(), runId, failureCode, failureMessage); }
                    catch (RuntimeException ignored) { /* 同一有界拒绝结果仍为权威结论。 */ }
                }
                throw new ServiceException(failureMessage);
            }
        });
    }

    /**
     * 原普通节点失败净化前仅记录有限类型与位置，最多扫描64帧并输出3个业务帧。
     * 不读取message、cause、suppressed或toString，不输出业务身份、文件名、模块或类加载器。
     * 日志仅接收安全字符串，不传Throwable；提取或日志设施失败不得改变原公开拒绝与执行保护。
     */
    private static void diagnoseFailure(RuntimeException failure) {
        try {
            String type = failure.getClass().getName();
            if (!diagnosticBinaryName(type)) type = "UNAVAILABLE";
            List<String> positions = new ArrayList<>(3);
            StackTraceElement[] trace = failure.getStackTrace();
            if (trace != null) {
                for (int i = 0; i < Math.min(trace.length, 64) && positions.size() < 3; i++) {
                    StackTraceElement frame = trace[i];
                    if (frame == null) continue;
                    String owner = frame.getClassName();
                    String method = frame.getMethodName();
                    if (owner != null && owner.startsWith("com.nexus.")
                        && diagnosticBinaryName(owner) && diagnosticMethodName(method)) {
                        positions.add(owner + "#" + method + ":" + frame.getLineNumber());
                    }
                }
            }
            log.warn("工业节点安全失败位置 type={} frames={}", type, positions.toString());
        } catch (Throwable ignored) {
            // 诊断只尽力完成；不得替换原失败，也不得以完整异常内容兜底。
        }
    }

    private static boolean diagnosticBinaryName(String name) {
        return name != null && name.length() <= 240
            && name.matches("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    }

    private static boolean diagnosticMethodName(String name) {
        return name != null && name.length() <= 80
            && (name.matches("[A-Za-z_$][A-Za-z0-9_$]*") || "<init>".equals(name) || "<clinit>".equals(name));
    }
}
