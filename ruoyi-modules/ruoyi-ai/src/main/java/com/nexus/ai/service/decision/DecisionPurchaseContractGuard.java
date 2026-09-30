package com.nexus.ai.service.decision;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.domain.entity.AiDecisionAction;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.core.exception.ServiceException;

import java.util.Objects;

/**
 * P0 采购动作门禁。只检查已持久化的 run/action，不接受审批请求或 Flow 参数作为合同来源。
 * 执行、重试入口应在任何状态或重试次数写入前复用此方法；P1 来源与单位合同就绪前无放行分支。
 */
public final class DecisionPurchaseContractGuard {
    /** 仅解析冻结 JSON；不会用本地 ObjectMapper 序列化雪花 ID 或替换全局序列化器。 */
    private static final ObjectMapper READER = new ObjectMapper();

    private DecisionPurchaseContractGuard() {
    }

    public static void requireExecutable(AiDecisionRun run, AiDecisionAction action) {
        if (action == null) throw new ServiceException("决策动作不存在，须重新分析");
        boolean purchase = DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT.name().equals(action.getActionType())
            || DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT.executorCode().equals(action.getExecutorCode());
        if (!purchase) return;

        if (run == null || run.getId() == null || run.getId() <= 0 || action.getId() == null
            || action.getId() <= 0 || !Objects.equals(run.getId(), action.getRunId())
            || run.getTenantId() == null || run.getTenantId().isBlank()
            || !Objects.equals(run.getTenantId(), action.getTenantId()))
            throw new ServiceException("采购动作运行或租户归属不一致，须重新分析");
        if (!DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT.name().equals(action.getActionType())
            || !DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT.executorCode().equals(action.getExecutorCode()))
            throw new ServiceException("采购动作类型或执行器归属不一致，须重新分析");

        JsonNode payload;
        try {
            payload = action.getPayloadJson() == null ? null : READER.readTree(action.getPayloadJson());
        } catch (JsonProcessingException invalid) {
            throw new ServiceException("采购动作合同版本或载荷无效，须重新分析");
        }
        /** Jackson 的 isInt 不接受字符串 "2"、小数 2.0 或布尔值。只读取冻结动作，绝不读取客户端字段。 */
        if (payload == null || !payload.isObject() || !payload.path("purchase_payload_version").isInt()
            || payload.path("purchase_payload_version").intValue() != 2)
            throw new ServiceException("采购动作合同版本缺失或无效，须重新分析");
        if (!payload.path("runId").isTextual()
            || !String.valueOf(run.getId()).equals(payload.path("runId").textValue()))
            throw new ServiceException("采购动作载荷运行归属不一致，须重新分析");

        /** 尚无由可信分析路径签发并可独立复核的逐来源明细/UOM 合同。即使 payload 声称 v2 也不能批准或执行。 */
        throw new ServiceException("采购动作来源与计量单位合同尚未启用，当前不可批准或执行，须重新分析");
    }
}
