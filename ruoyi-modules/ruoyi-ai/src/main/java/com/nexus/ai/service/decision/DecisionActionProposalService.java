package com.nexus.ai.service.decision;

import com.nexus.common.ai.decision.dto.DecisionActionProposal;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.dto.DecisionPurchaseContractV2;
import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.enums.DecisionRiskLevel;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 仅依据强类型方案事实提出白名单内的可逆草稿及通知。
 */
@Service
public class DecisionActionProposalService {
    private static final BigDecimal MAX_DECIMAL = new BigDecimal("99999999999999.9999");
    private static final Set<String> SUPPORTED_SCHEDULES = Set.of("APS_EDD", "APS_CR", "APS_CR_EXPEDITE");

    /** 旧接口只保留通知和排程，不以旧缺口签发采购。 */
    public List<DecisionActionProposal> propose(ProposalInput input) {
        if (input != null && input.rootCauses() != null && input.rootCauses().contains("MATERIAL_SHORTAGE")
            && input.target() != null && input.target().type() != DecisionBusinessType.SHORTAGE_HORIZON
            && input.materialLines() != null) {
            for (Map<String, Object> row : input.materialLines()) {
                if (row == null) throw new IllegalArgumentException("缺料明细不可为空");
                positiveId(row.get("materialId"), "materialId");
                positiveDecimal(row.get("gap")); parseDate(row.get("requiredDate"));
            }
        }
        return propose(input, List.of());
    }

    /** 采购动作只消费服务端冻结的完整强类型合同。 */
    public List<DecisionActionProposal> propose(ProposalInput input, List<DecisionPurchaseContractV2> contracts) {
        if (input == null || input.runId() == null || input.runId() <= 0 || input.tenantId() == null
            || !input.tenantId().matches("[A-Za-z0-9_-]{1,20}") || input.target() == null
            || input.target().type() == null || input.riskLevel() == null || input.rootCauses() == null) {
            throw new IllegalArgumentException("决策运行/租户/风险/目标来源必须完整");
        }
        DecisionBusinessRef target = input.target();
        boolean executableTarget = target.type() != DecisionBusinessType.SHORTAGE_HORIZON
            && target.id() != null && target.id() > 0;
        if (target.type() != DecisionBusinessType.SHORTAGE_HORIZON && !executableTarget) {
            throw new IllegalArgumentException("草稿目标必须是精确业务对象");
        }
        List<DecisionActionProposal> actions = new ArrayList<>();
        List<Map<String, Object>> lines = input.materialLines() == null ? List.of() : input.materialLines();
        if (input.rootCauses().contains("PURCHASE_DELAY") && input.inTransitCovers()
            && lines.stream().anyMatch(line -> line != null && line.get("purchaseLineId") instanceof String id
                && id.matches("[1-9][0-9]*"))) {
            List<String> purchaseLineIds = lines.stream().filter(line -> line != null)
                .map(line -> line.get("purchaseLineId"))
                .filter(String.class::isInstance).map(String.class::cast)
                .filter(s -> s.matches("[1-9][0-9]*")).distinct().sorted().toList();
            actions.add(build(input, DecisionActionType.SEND_EXPEDITE_NOTICE, "通知采购负责人核实到货承诺",
                Map.of("runId", String.valueOf(input.runId()), "purchaseLineIds", purchaseLineIds)));
        }
        if (contracts == null || contracts.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("冻结采购合同集合不可为空或包含空行");
        if (!contracts.isEmpty() && (target.type() != DecisionBusinessType.WORK_ORDER
            || !input.rootCauses().contains("MATERIAL_SHORTAGE")))
            throw new IllegalArgumentException("采购合同只能用于明确工单的缺料风险");
        Set<String> purchaseKeys = new HashSet<>();
        List<DecisionPurchaseContractV2> ordered = new ArrayList<>(contracts);
        // 先交唯一编解码器验证，再按数值标识稳定排序，不能靠旗号补足缺失事实。
        ordered.forEach(contract -> DecisionPurchaseContractCodec.toPayload(input.runId(), contract));
        ordered.sort(Comparator.comparing(DecisionPurchaseContractV2::materialId)
            .thenComparing(DecisionPurchaseContractV2::vendorId));
        for (DecisionPurchaseContractV2 contract : ordered) {
            String key = DecisionPurchaseContractCodec.idempotencyKey(input.tenantId(), input.runId(), target, contract);
            if (!purchaseKeys.add(key)) throw new IllegalArgumentException("物料供应商冻结合同重复");
            actions.add(new DecisionActionProposal(null, DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,
                "创建待审批采购申请草稿", "依据服务端逐来源冻结合同创建草稿", target,
                DecisionPurchaseContractCodec.toPayload(input.runId(), contract), input.riskLevel(), true,
                DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT.permissionCode(), key,
                DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT.executorCode(), 0, List.of()));
        }
        if (input.rootCauses().contains("SCHEDULE_DELAY") && executableTarget
            && input.recommendedScenarioCode() != null
            && SUPPORTED_SCHEDULES.contains(input.recommendedScenarioCode())
            && input.workOrderIds() != null && !input.workOrderIds().isEmpty()
            && input.startDate() != null && input.horizonDays() >= 1 && input.horizonDays() <= 90) {
            List<String> ids = new ArrayList<>();
            for (String id : input.workOrderIds()) ids.add(positiveId(id, "workOrderId"));
            if (new HashSet<>(ids).size() != ids.size()) throw new IllegalArgumentException("排产工单 ID 不可重复");
            ids.sort(Comparator.comparingLong(Long::parseLong));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("runId", String.valueOf(input.runId()));
            payload.put("scenarioCode", input.recommendedScenarioCode());
            payload.put("strategy", input.recommendedScenarioCode().equals("APS_EDD") ? "EDD" : "CR");
            payload.put("startDate", input.startDate().toString());
            payload.put("horizonDays", input.horizonDays());
            payload.put("workOrderIds", ids);
            if (input.workshopId() != null) payload.put("workshopId", positiveId(String.valueOf(input.workshopId()), "workshopId"));
            actions.add(build(input, DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT,
                "创建待审批排产方案草稿", payload));
        }
        if ((input.riskLevel() == DecisionRiskLevel.HIGH || input.riskLevel() == DecisionRiskLevel.CRITICAL)
            && input.managerUserId() != null && input.managerUserId() > 0) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("runId", String.valueOf(input.runId()));
            payload.put("targetUserId", String.valueOf(input.managerUserId()));
            actions.add(build(input, DecisionActionType.SEND_MANAGER_NOTIFICATION,
                "提醒负责人打开决策模块审批列表", payload));
        }
        List<DecisionActionProposal> sequenced = new ArrayList<>(actions.size());
        for (int i = 0; i < actions.size(); i++) {
            var action = actions.get(i);
            sequenced.add(new DecisionActionProposal(null, action.actionType(), action.title(), action.displayReason(),
                action.target(), action.payload(), action.riskLevel(), action.approvalRequired(),
                action.permissionCode(), action.idempotencyKey(), action.executorCode(), i + 1, List.of()));
        }
        return List.copyOf(sequenced);
    }

    private static DecisionActionProposal build(ProposalInput input, DecisionActionType type,
                                                String title, Map<String, Object> payload) {
        // 持久化前没有 actionId；Task 11 保存动作后再绑定标识。
        String key = input.tenantId() + ":" + input.runId() + ":" + type.name() + ":"
            + input.target().type().name() + ":"
            + (input.target().id() == null ? "HORIZON" : input.target().id());
        if (key.length() > 128) throw new IllegalArgumentException("幂等键长度超过业务数据库上限");
        boolean requireApproval = type.approvalRequired()
            || input.riskLevel() == DecisionRiskLevel.HIGH || input.riskLevel() == DecisionRiskLevel.CRITICAL;
        return new DecisionActionProposal(null, type, title, title, input.target(), payload,
            input.riskLevel(), requireApproval, type.permissionCode(), key, type.executorCode(), 0, List.of());
    }

    private static String positiveId(Object value, String field) {
        if (!(value instanceof String text) || !text.matches("[1-9][0-9]{0,18}"))
            throw new IllegalArgumentException(field + " 必须是十进制字符串 ID");
        try { Long.parseLong(text); } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " 超出 Snowflake ID 范围", e);
        }
        return text;
    }

    private static String positiveDecimal(Object value) {
        if (!(value instanceof String text) || text.length() > 64)
            throw new IllegalArgumentException("缺口必须是十进制字符串");
        try {
            BigDecimal amount = new BigDecimal(text);
            if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 4 || amount.compareTo(MAX_DECIMAL) > 0)
                throw new IllegalArgumentException("缺口数值超出 decimal(18,4)");
            return amount.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) { throw new IllegalArgumentException("缺口不是有效十进制数", e); }
    }

    private static LocalDate parseDate(Object raw) {
        if (!(raw instanceof String text) || !text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
            throw new IllegalArgumentException("需求日必须使用 yyyy-MM-dd");
        try { return LocalDate.parse(text); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException("需求日期不存在", e); }
    }

    public record ProposalInput(Long runId, String tenantId, DecisionBusinessRef target, DecisionRiskLevel riskLevel,
                                Set<String> rootCauses, String recommendedScenarioCode, boolean inTransitCovers,
                                List<Map<String, Object>> materialLines, Long managerUserId,
                                List<String> workOrderIds, LocalDate startDate, int horizonDays, Long workshopId) {
        /**
         * 缺少精确工单与开始日期的最小输入不能授权创建排程草稿。
         */
        public ProposalInput(Long runId, String tenantId, DecisionBusinessRef target, DecisionRiskLevel riskLevel,
                             Set<String> rootCauses, String recommendedScenarioCode, boolean inTransitCovers,
                             List<Map<String, Object>> materialLines, Long managerUserId) {
            this(runId, tenantId, target, riskLevel, rootCauses, recommendedScenarioCode,
                inTransitCovers, materialLines, managerUserId, List.of(), null, 0, null);
        }
    }
}
