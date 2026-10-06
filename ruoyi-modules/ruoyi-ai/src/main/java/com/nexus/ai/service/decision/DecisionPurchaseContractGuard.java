package com.nexus.ai.service.decision;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec;
import com.nexus.common.ai.domain.entity.AiDecisionAction;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.core.exception.ServiceException;

import java.util.Map;
import java.util.Objects;

/**
 * 采购结构门禁：只读取已持久化的运行、动作和完整合同，不补版本、不访问 ERP。
 * 结构通过不代表来源仍可采购；审批和创建必须另经 ERP 逐来源认证。
 */
public final class DecisionPurchaseContractGuard {
    /** 只解析冻结 JSON，拒绝重复字段；不序列化业务对象或替换全局雪花 ID 序列化器。 */
    private static final ObjectMapper READER = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private DecisionPurchaseContractGuard() { }

    public static void requireExecutable(AiDecisionRun run, AiDecisionAction action) {
        if (action == null) throw new ServiceException("决策动作不存在，须重新分析");
        DecisionActionType type = DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT;
        boolean purchase = type.name().equals(action.getActionType()) || type.executorCode().equals(action.getExecutorCode());
        if (!purchase) return;
        if (run == null || run.getId() == null || run.getId() <= 0 || action.getId() == null
            || action.getId() <= 0 || !Objects.equals(run.getId(), action.getRunId())
            || run.getTenantId() == null || run.getTenantId().isBlank()
            || !Objects.equals(run.getTenantId(), action.getTenantId()))
            throw new ServiceException("采购动作运行或租户归属不一致，须重新分析");
        if (!type.name().equals(action.getActionType()) || !type.executorCode().equals(action.getExecutorCode())
            || !type.permissionCode().equals(action.getPermissionCode()) || !"Y".equals(action.getApprovalRequired()))
            throw new ServiceException("采购动作类型、执行器、权限或审批配置不一致，须重新分析");
        JsonNode payload;
        try { payload = action.getPayloadJson() == null ? null : READER.readTree(action.getPayloadJson()); }
        catch (Exception invalid) { throw new ServiceException("采购动作冻结载荷无效，须重新分析"); }
        if (payload != null && payload.isObject() && payload.path("runId").isTextual()
            && !String.valueOf(run.getId()).equals(payload.path("runId").textValue()))
            throw new ServiceException("采购动作载荷运行归属不一致，须重新分析");
        if (!Integer.valueOf(2).equals(run.getDecisionContractVersion())
            || !Integer.valueOf(2).equals(action.getPurchasePayloadVersion()))
            throw new ServiceException("采购动作合同版本缺失或无效，须重新分析");
        if (!DecisionBusinessType.WORK_ORDER.name().equals(run.getBizType()) || run.getBizId() == null
            || run.getBizId() <= 0 || !Objects.equals(run.getBizType(), action.getTargetBizType())
            || !Objects.equals(run.getBizId(), action.getTargetBizId())
            || !Objects.equals(run.getBizCode(), action.getTargetBizCode()))
            throw new ServiceException("采购动作目标归属不一致，须重新分析");
        try {
            if (payload == null || !payload.isObject() || !payload.path("purchase_payload_version").isInt()
                || payload.path("purchase_payload_version").intValue() != 2)
                throw new ServiceException("采购动作合同版本缺失或无效，须重新分析");
            Map<String, Object> fields = READER.convertValue(payload, new TypeReference<Map<String, Object>>() { });
            var contract = DecisionPurchaseContractCodec.parse(fields, run.getId());
            var target = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, run.getBizId(), run.getBizCode());
            if (!Objects.equals(DecisionPurchaseContractCodec.idempotencyKey(run.getTenantId(), run.getId(), target, contract),
                action.getIdempotencyKey()))
                throw new ServiceException("采购动作永久请求键与冻结合同不一致，须重新分析");
        } catch (Exception invalid) {
            // 编解码异常可能带载荷原文；门禁及只读查询不向调用方泄露合同内容。
            throw new ServiceException("采购动作来源、计量单位或幂等合同无效，须重新分析");
        }
    }
}
