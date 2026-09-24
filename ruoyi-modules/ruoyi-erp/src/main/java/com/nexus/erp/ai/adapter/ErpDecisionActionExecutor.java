package com.nexus.erp.ai.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.ai.decision.dto.DecisionActionExecutionResult;
import com.nexus.common.ai.decision.dto.DecisionActionProposal;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.erp.prd.domain.PrdSchedulePlan;
import com.nexus.erp.prd.domain.bo.PrdSchedulePlanBo;
import com.nexus.erp.prd.domain.vo.PrdSchedulePlanVo;
import com.nexus.erp.prd.mapper.PrdSchedulePlanMapper;
import com.nexus.erp.prd.service.IPrdScheduleService;
import com.nexus.erp.pu.domain.PuPurchaseRequisitionLine;
import com.nexus.erp.pu.domain.bo.PuPurchaseRequisitionBo;
import com.nexus.erp.pu.domain.vo.PuPurchaseRequisitionVo;
import com.nexus.erp.pu.service.IPuPurchaseRequisitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 执行、验证并条件补偿 ERP 决策草稿动作。 */
@Component
@RequiredArgsConstructor
@DS("erp")
public class ErpDecisionActionExecutor {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final BigDecimal MAX_DECIMAL_18_4 = new BigDecimal("99999999999999.9999");
    private static final int MAX_DECIMAL_TEXT_LENGTH = 64;
    private static final String PURCHASE_BIZ_TYPE = "PU_PURCHASE_REQUISITION";
    private static final String SCHEDULE_BIZ_TYPE = "PRD_SCHEDULE_PLAN";
    private static final String SOURCE_AI_DECISION = "AI_DECISION";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private static final Set<String> PURCHASE_FIELDS = Set.of("runId", "requiredDate", "materialLines");
    private static final Set<String> PURCHASE_LINE_FIELDS = Set.of(
        "materialId", "materialCode", "materialName", "quantity", "gap", "requiredDate");
    private static final Set<String> SCHEDULE_FIELDS = Set.of(
        "runId", "scenarioCode", "strategy", "startDate", "horizonDays", "workOrderIds", "workshopId");

    private final IPuPurchaseRequisitionService purchaseRequisitionService;
    private final IPrdScheduleService scheduleService;
    private final PrdSchedulePlanMapper planMapper;

    @Transactional(rollbackFor = Exception.class)
    public DecisionActionExecutionResult execute(DecisionActionProposal action, DecisionActor actor) {
        validateCommon(action, actor);
        return switch (action.actionType()) {
            case CREATE_PURCHASE_REQUISITION_DRAFT -> executePurchase(action, actor);
            case CREATE_SCHEDULE_PLAN_DRAFT -> executeSchedule(action, actor);
            default -> throw new ServiceException("不支持的ERP决策动作: " + action.actionType());
        };
    }

    public DecisionActionExecutionResult verify(DecisionActionProposal action,
                                                DecisionActionExecutionResult result,
                                                DecisionActor actor) {
        validateCommon(action, actor);
        ValidatedResult validated = validateResultEnvelope(action, result);
        if (action.actionType() == DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT) {
            PurchaseIntent intent = parsePurchaseIntent(action.payload());
            PuPurchaseRequisitionVo current = purchaseRequisitionService.selectById(result.resultBizId());
            if (current == null) {
                return verifyFailure(result, "VERIFY_NOT_FOUND", "采购申请草稿不存在");
            }
            if (!STATUS_DRAFT.equals(current.getStatus())) {
                return verifyFailure(result, "VERIFY_STATE_MISMATCH", "采购申请已不再是草稿状态");
            }
            if (!validated.marker().equals(versionMarker(current.getVersion()))
                || !purchaseMatches(action, actor, intent, current)
                || !Objects.equals(result.resultBizCode(), current.getRequisitionNo())
                || !STATUS_DRAFT.equals(result.result().get("status"))) {
                return verifyFailure(result, "VERIFY_CONFLICT", "采购申请来源、载荷或版本已变化");
            }
            return result;
        }

        ScheduleIntent intent = parseScheduleIntent(action.payload());
        PrdSchedulePlan current = planMapper.selectById(result.resultBizId());
        if (current == null) {
            return verifyFailure(result, "VERIFY_NOT_FOUND", "排程方案不存在");
        }
        if (!STATUS_COMPLETED.equals(current.getStatus())) {
            return verifyFailure(result, "VERIFY_STATE_MISMATCH", "排程方案已不再是未发布完成状态");
        }
        if (!validated.marker().equals(versionMarker(current.getVersion()))
            || !scheduleMatches(action, intent, current)
            || !Objects.equals(result.resultBizCode(), current.getPlanNo())
            || !STATUS_COMPLETED.equals(result.result().get("status"))
            || !intent.scenarioCode().equals(result.result().get("scenarioCode"))) {
            return verifyFailure(result, "VERIFY_CONFLICT", "排程方案来源、范围、载荷或版本已变化");
        }
        return result;
    }

    public DecisionActionExecutionResult compensate(DecisionActionProposal action,
                                                    DecisionActionExecutionResult result,
                                                    DecisionActor actor) {
        validateCommon(action, actor);
        ValidatedResult validated = validateResultEnvelope(action, result);
        boolean compensated;
        if (action.actionType() == DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT) {
            PurchaseIntent intent = parsePurchaseIntent(action.payload());
            requireResultStatus(result, STATUS_DRAFT, null);
            compensated = purchaseRequisitionService.deleteDraftIfUnchanged(
                result.resultBizId(), validated.marker(), intent.runId(), action.actionId(), action.idempotencyKey());
        } else {
            ScheduleIntent intent = parseScheduleIntent(action.payload());
            requireResultStatus(result, STATUS_COMPLETED, intent.scenarioCode());
            compensated = scheduleService.deleteCompletedIfUnchanged(
                result.resultBizId(), validated.marker(), intent.runId(), action.actionId(),
                action.idempotencyKey(), intent.scenarioCode());
        }
        if (!compensated) {
            return new DecisionActionExecutionResult(false, false, result.resultBizType(), result.resultBizId(),
                result.resultBizCode(), result.result(), "COMPENSATION_NOT_ALLOWED",
                "业务状态、版本或结构化来源已变化，禁止自动补偿");
        }
        Map<String, Object> compensatedResult = new LinkedHashMap<>();
        compensatedResult.put("status", "COMPENSATED");
        compensatedResult.put("versionMarker", validated.marker());
        compensatedResult.put("runId", String.valueOf(validated.runId()));
        compensatedResult.put("actionId", String.valueOf(validated.actionId()));
        return success(result.resultBizType(), result.resultBizId(), result.resultBizCode(), compensatedResult);
    }

    private DecisionActionExecutionResult executePurchase(DecisionActionProposal action, DecisionActor actor) {
        PurchaseIntent intent = parsePurchaseIntent(action.payload());
        PuPurchaseRequisitionBo bo = new PuPurchaseRequisitionBo();
        bo.setRequisitionName("AI交付风险建议-" + targetLabel(action.target()));
        bo.setApplicantId(actor.userId());
        bo.setApplicantName(actor.userName());
        bo.setSourceType(SOURCE_AI_DECISION);
        bo.setRequisitionDate(currentBusinessTime());
        bo.setRequiredDate(java.sql.Date.valueOf(intent.requiredDate()));
        bo.setRemark("runId=" + intent.runId() + ";idempotencyKey=" + action.idempotencyKey());
        bo.setDecisionRunId(intent.runId());
        bo.setDecisionActionId(action.actionId());
        bo.setDecisionIdempotencyKey(action.idempotencyKey());
        bo.setVersion(0);
        bo.setCreateBy(actor.userId());
        bo.setUpdateBy(actor.userId());
        bo.setPuPurchaseRequisitionLineList(toPurchaseLines(intent));

        Long requisitionId = purchaseRequisitionService.createDraft(bo);
        if (requisitionId == null || requisitionId <= 0) {
            throw new ServiceException("采购申请草稿未返回有效ID");
        }
        PuPurchaseRequisitionVo persisted = purchaseRequisitionService.selectById(requisitionId);
        if (persisted == null) {
            throw new ServiceException("采购申请草稿创建后无法重读");
        }
        if (!STATUS_DRAFT.equals(persisted.getStatus()) || !purchaseMatches(action, actor, intent, persisted)) {
            throw new ServiceException("采购申请草稿创建后状态、来源或载荷异常");
        }
        if (persisted.getRequisitionNo() == null || persisted.getRequisitionNo().isBlank()) {
            throw new ServiceException("采购申请草稿创建后编号为空");
        }

        Map<String, Object> result = baseResult(STATUS_DRAFT, persisted.getVersion(), intent.runId(), action.actionId());
        return success(PURCHASE_BIZ_TYPE, requisitionId, persisted.getRequisitionNo(), result);
    }

    private DecisionActionExecutionResult executeSchedule(DecisionActionProposal action, DecisionActor actor) {
        ScheduleIntent intent = parseScheduleIntent(action.payload());
        PrdSchedulePlanBo bo = new PrdSchedulePlanBo();
        bo.setPlanName("AI交付风险方案-" + intent.scenarioCode());
        bo.setRemark("runId=" + intent.runId() + ";scenario=" + intent.scenarioCode()
            + ";idempotencyKey=" + action.idempotencyKey());
        bo.setStartDate(java.sql.Date.valueOf(intent.startDate()));
        bo.setHorizonDays(intent.horizonDays());
        bo.setStrategy(intent.strategy());
        bo.setWorkshopId(intent.workshopId());
        bo.setDecisionRunId(intent.runId());
        bo.setDecisionActionId(action.actionId());
        bo.setDecisionIdempotencyKey(action.idempotencyKey());
        bo.setDecisionScenarioCode(intent.scenarioCode());
        bo.setDecisionWorkOrderIdsJson(intent.workOrderIdsJson());
        bo.setVersion(0);
        bo.setCreateBy(actor.userId());
        bo.setUpdateBy(actor.userId());

        PrdSchedulePlanVo created = scheduleService.runSchedule(bo, intent.workOrderIds());
        if (created == null || created.getPlanId() == null || created.getPlanId() <= 0) {
            throw new ServiceException("排程方案未返回有效ID");
        }
        PrdSchedulePlan persisted = planMapper.selectById(created.getPlanId());
        if (persisted == null) {
            throw new ServiceException("排程方案创建后无法重读");
        }
        if (!STATUS_COMPLETED.equals(persisted.getStatus()) || !scheduleMatches(action, intent, persisted)) {
            throw new ServiceException("排程方案创建后状态、来源或载荷异常");
        }
        if (persisted.getPlanNo() == null || persisted.getPlanNo().isBlank()) {
            throw new ServiceException("排程方案创建后编号为空");
        }

        Map<String, Object> result = baseResult(
            STATUS_COMPLETED, persisted.getVersion(), intent.runId(), action.actionId());
        result.put("scenarioCode", intent.scenarioCode());
        return success(SCHEDULE_BIZ_TYPE, persisted.getPlanId(), persisted.getPlanNo(), result);
    }

    private void validateCommon(DecisionActionProposal action, DecisionActor actor) {
        if (action == null) {
            throw new ServiceException("决策动作不能为空");
        }
        if (actor == null) {
            throw new ServiceException("决策执行人不能为空");
        }
        if (actor.tenantId() == null || actor.tenantId().isBlank()) {
            throw new ServiceException("租户不能为空");
        }
        if (actor.userId() == null || actor.userId() <= 0) {
            throw new ServiceException("用户ID无效");
        }
        if (actor.userName() == null || actor.userName().isBlank()) {
            throw new ServiceException("用户名不能为空");
        }
        if (actor.permissions() == null || actor.permissions().isEmpty()) {
            throw new ServiceException("权限集合不能为空");
        }
        if (action.actionId() == null || action.actionId() <= 0) {
            throw new ServiceException("动作ID必须为正数");
        }
        DecisionActionType type = action.actionType();
        if (type != DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT
            && type != DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT) {
            throw new ServiceException("不支持的ERP决策动作: " + type);
        }
        if (!type.permissionCode().equals(action.permissionCode())) {
            throw new ServiceException("动作权限元数据不一致");
        }
        if (!type.executorCode().equals(action.executorCode())) {
            throw new ServiceException("动作执行器元数据不一致");
        }
        if (action.approvalRequired() != type.approvalRequired()) {
            throw new ServiceException("动作审批元数据不一致");
        }
        if (!actor.permissions().contains(type.permissionCode()) && !actor.permissions().contains("*:*:*")) {
            throw new ServiceException("无权执行该ERP决策动作");
        }
        if (action.payload() == null) {
            throw new ServiceException("动作payload不能为空");
        }
        if (action.idempotencyKey() == null || action.idempotencyKey().isBlank()
            || action.idempotencyKey().length() > 128) {
            throw new ServiceException("幂等键不能为空且长度不能超过128");
        }
        DecisionBusinessRef target = action.target();
        if (target == null || target.type() == null || target.id() == null || target.id() <= 0) {
            throw new ServiceException("动作目标无效");
        }
    }

    private ValidatedResult validateResultEnvelope(DecisionActionProposal action,
                                                    DecisionActionExecutionResult result) {
        if (result == null || !result.success()) {
            throw new ServiceException("仅成功的动作结果可验证或补偿");
        }
        if (result.resultBizId() == null || result.resultBizId() <= 0) {
            throw new ServiceException("动作结果业务ID无效");
        }
        String expectedType = action.actionType() == DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT
            ? PURCHASE_BIZ_TYPE : SCHEDULE_BIZ_TYPE;
        if (!expectedType.equals(result.resultBizType())) {
            throw new ServiceException("动作结果业务类型不匹配");
        }
        if (result.result() == null) {
            throw new ServiceException("动作结果详情不能为空");
        }
        Object markerValue = result.result().get("versionMarker");
        if (!(markerValue instanceof String marker) || parseVersion(marker) == null) {
            throw new ServiceException("动作结果版本标记无效");
        }
        long payloadRunId = requirePositiveLong(action.payload().get("runId"), "runId");
        String resultRunId = requireDecimalResultValue(result.result().get("runId"), "result.runId");
        String resultActionId = requireDecimalResultValue(result.result().get("actionId"), "result.actionId");
        if (!String.valueOf(payloadRunId).equals(resultRunId)
            || !String.valueOf(action.actionId()).equals(resultActionId)) {
            throw new ServiceException("动作结果结构化来源与动作不一致");
        }
        return new ValidatedResult(marker, payloadRunId, action.actionId());
    }

    private void requireResultStatus(DecisionActionExecutionResult result,
                                     String expectedStatus, String expectedScenario) {
        if (!expectedStatus.equals(result.result().get("status"))) {
            throw new ServiceException("动作结果状态被篡改");
        }
        if (expectedScenario != null && !expectedScenario.equals(result.result().get("scenarioCode"))) {
            throw new ServiceException("动作结果场景被篡改");
        }
    }

    private PurchaseIntent parsePurchaseIntent(Map<String, Object> payload) {
        validateAllowedFields(payload, PURCHASE_FIELDS, "采购动作");
        long runId = requirePositiveLong(payload.get("runId"), "runId");
        LocalDate requiredDate = requireDate(payload.get("requiredDate"), "requiredDate");
        Object value = payload.get("materialLines");
        if (!(value instanceof List<?> rawLines) || rawLines.isEmpty()) {
            throw new ServiceException("materialLines 必须是非空列表");
        }
        List<PurchaseLineIntent> lines = new ArrayList<>(rawLines.size());
        for (Object rawLine : rawLines) {
            if (!(rawLine instanceof Map<?, ?> rawMap)) {
                throw new ServiceException("materialLines 行必须是对象");
            }
            Map<String, Object> line = stringKeyMap(rawMap, "materialLines 行");
            validateAllowedFields(line, PURCHASE_LINE_FIELDS, "materialLines 行");
            long materialId = requirePositiveLong(line.get("materialId"), "materialId");
            BigDecimal quantity = optionalPositiveDecimal(line.get("quantity"), "quantity");
            BigDecimal gap = optionalPositiveDecimal(line.get("gap"), "gap");
            if (quantity == null && gap == null) {
                throw new ServiceException("quantity 和 gap 至少提供一个");
            }
            if (quantity != null && gap != null && quantity.compareTo(gap) != 0) {
                throw new ServiceException("quantity 与 gap 数值不一致");
            }
            BigDecimal determined = quantity != null ? quantity : gap;
            LocalDate lineDate = line.get("requiredDate") == null
                ? requiredDate : requireDate(line.get("requiredDate"), "materialLines.requiredDate");
            lines.add(new PurchaseLineIntent(materialId,
                optionalString(line.get("materialCode"), "materialCode"),
                optionalString(line.get("materialName"), "materialName"),
                determined, gap != null ? gap : determined, lineDate));
        }
        return new PurchaseIntent(runId, requiredDate, List.copyOf(lines));
    }

    private ScheduleIntent parseScheduleIntent(Map<String, Object> payload) {
        validateAllowedFields(payload, SCHEDULE_FIELDS, "排产动作");
        long runId = requirePositiveLong(payload.get("runId"), "runId");
        String scenarioCode = requireString(payload.get("scenarioCode"), "scenarioCode");
        String strategy = strategyForScenario(scenarioCode);
        if (payload.containsKey("strategy") && payload.get("strategy") != null) {
            String supplied = requireString(payload.get("strategy"), "strategy");
            if (!("EDD".equals(supplied) || "CR".equals(supplied)) || !strategy.equals(supplied)) {
                throw new ServiceException("strategy 与 scenarioCode 不一致");
            }
        }
        LocalDate startDate = requireDate(payload.get("startDate"), "startDate");
        int horizonDays = requireHorizon(payload.get("horizonDays"));
        List<Long> workOrderIds = requireUniqueIds(payload.get("workOrderIds"), "workOrderIds");
        Long workshopId = payload.get("workshopId") == null
            ? null : requirePositiveLong(payload.get("workshopId"), "workshopId");
        String workOrderIdsJson = JsonUtils.toJsonString(
            workOrderIds.stream().map(String::valueOf).toList());
        return new ScheduleIntent(runId, scenarioCode, strategy, startDate,
            horizonDays, workOrderIds, workshopId, workOrderIdsJson);
    }

    private List<PuPurchaseRequisitionLine> toPurchaseLines(PurchaseIntent intent) {
        List<PuPurchaseRequisitionLine> lines = new ArrayList<>(intent.lines().size());
        for (PurchaseLineIntent input : intent.lines()) {
            PuPurchaseRequisitionLine line = new PuPurchaseRequisitionLine();
            line.setMaterialId(input.materialId());
            line.setMaterialCode(input.materialCode());
            line.setMaterialName(input.materialName());
            line.setApplyQuantity(input.applyQuantity());
            line.setShortageQuantity(input.shortageQuantity());
            line.setRequiredDate(java.sql.Date.valueOf(input.requiredDate()));
            line.setSourceType(SOURCE_AI_DECISION);
            line.setSourceId(intent.runId());
            lines.add(line);
        }
        return lines;
    }

    private boolean purchaseMatches(DecisionActionProposal action, DecisionActor actor,
                                    PurchaseIntent intent, PuPurchaseRequisitionVo current) {
        return Objects.equals(current.getDecisionRunId(), intent.runId())
            && Objects.equals(current.getDecisionActionId(), action.actionId())
            && Objects.equals(current.getDecisionIdempotencyKey(), action.idempotencyKey())
            && Objects.equals(current.getRequisitionName(), "AI交付风险建议-" + targetLabel(action.target()))
            && Objects.equals(current.getApplicantId(), actor.userId())
            && Objects.equals(current.getApplicantName(), actor.userName())
            && SOURCE_AI_DECISION.equals(current.getSourceType())
            && intent.requiredDate().equals(toLocalDate(current.getRequiredDate()))
            && purchaseLineFingerprints(intent).equals(purchaseLineFingerprints(current.getPuPurchaseRequisitionLineList()));
    }

    private boolean scheduleMatches(DecisionActionProposal action, ScheduleIntent intent,
                                    PrdSchedulePlan current) {
        return Objects.equals(current.getDecisionRunId(), intent.runId())
            && Objects.equals(current.getDecisionActionId(), action.actionId())
            && Objects.equals(current.getDecisionIdempotencyKey(), action.idempotencyKey())
            && Objects.equals(current.getDecisionScenarioCode(), intent.scenarioCode())
            && Objects.equals(current.getDecisionWorkOrderIdsJson(), intent.workOrderIdsJson())
            && Objects.equals(current.getPlanName(), "AI交付风险方案-" + intent.scenarioCode())
            && Objects.equals(current.getStrategy(), intent.strategy())
            && intent.startDate().equals(toLocalDate(current.getStartDate()))
            && Objects.equals(current.getHorizonDays(), intent.horizonDays())
            && Objects.equals(current.getWorkshopId(), intent.workshopId());
    }

    private List<LineFingerprint> purchaseLineFingerprints(PurchaseIntent intent) {
        return intent.lines().stream().map(line -> new LineFingerprint(
                line.materialId(),
                line.materialCode(),
                line.materialName(),
                normalizedDecimal(line.applyQuantity()),
                normalizedDecimal(line.shortageQuantity()),
                line.requiredDate(),
                SOURCE_AI_DECISION,
                intent.runId()))
            .sorted().toList();
    }

    private List<LineFingerprint> purchaseLineFingerprints(List<PuPurchaseRequisitionLine> lines) {
        if (lines == null) {
            return List.of();
        }
        return lines.stream().map(line -> new LineFingerprint(
                line.getMaterialId(),
                line.getMaterialCode(),
                line.getMaterialName(),
                normalizedDecimal(line.getApplyQuantity()),
                normalizedDecimal(line.getShortageQuantity()),
                toLocalDate(line.getRequiredDate()),
                line.getSourceType(),
                line.getSourceId()))
            .sorted().toList();
    }

    private Map<String, Object> stringKeyMap(Map<?, ?> raw, String field) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new ServiceException(field + " 包含非字符串字段名");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private void validateAllowedFields(Map<String, Object> payload, Set<String> allowed, String field) {
        for (String key : payload.keySet()) {
            if (!allowed.contains(key)) {
                throw new ServiceException(field + " 包含未知字段: " + key);
            }
        }
    }

    private long requirePositiveLong(Object value, String field) {
        if (!(value instanceof String text) || text.isEmpty() || !text.chars().allMatch(Character::isDigit)) {
            throw new ServiceException(field + " 必须是正十进制字符串");
        }
        try {
            long parsed = Long.parseLong(text);
            if (parsed <= 0) {
                throw new ServiceException(field + " 必须大于0");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new ServiceException(field + " 超出Long范围");
        }
    }

    private String requireDecimalResultValue(Object value, String field) {
        if (!(value instanceof String text) || text.isEmpty() || !text.chars().allMatch(Character::isDigit)) {
            throw new ServiceException(field + " 必须是十进制字符串");
        }
        return text;
    }

    private BigDecimal optionalPositiveDecimal(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text) || text.length() > MAX_DECIMAL_TEXT_LENGTH
            || !text.matches("[0-9]+(?:\\.[0-9]+)?")) {
            throw new ServiceException(field + " 必须是有界正十进制字符串");
        }
        final BigDecimal decimal;
        try {
            decimal = new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new ServiceException(field + " 不是有效十进制数");
        }
        BigDecimal normalized = decimal.stripTrailingZeros();
        if (decimal.compareTo(BigDecimal.ZERO) <= 0
            || normalized.scale() > 4
            || decimal.compareTo(MAX_DECIMAL_18_4) > 0) {
            throw new ServiceException(field + " 超出decimal(18,4)正数范围");
        }
        return decimal;
    }

    private LocalDate requireDate(Object value, String field) {
        if (!(value instanceof String text) || !text.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new ServiceException(field + " 必须使用yyyy-MM-dd格式");
        }
        try {
            return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new ServiceException(field + " 不是有效日期");
        }
    }

    private String requireString(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new ServiceException(field + " 必须是非空字符串");
        }
        return text;
    }

    private String optionalString(Object value, String field) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new ServiceException(field + " 必须是字符串");
        }
        return text;
    }

    private int requireHorizon(Object value) {
        if (!(value instanceof Integer horizonDays) || horizonDays < 1 || horizonDays > 90) {
            throw new ServiceException("horizonDays 必须是1到90的整数");
        }
        return horizonDays;
    }

    private List<Long> requireUniqueIds(Object value, String field) {
        if (!(value instanceof List<?> rawIds) || rawIds.isEmpty()) {
            throw new ServiceException(field + " 必须是非空列表");
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Object rawId : rawIds) {
            long id = requirePositiveLong(rawId, field);
            if (!ids.add(id)) {
                throw new ServiceException(field + " 不允许重复ID");
            }
        }
        return ids.stream().sorted().toList();
    }

    private String strategyForScenario(String scenarioCode) {
        return switch (scenarioCode) {
            case "APS_EDD" -> "EDD";
            case "APS_CR", "APS_CR_EXPEDITE" -> "CR";
            case "BASELINE_CURRENT", "CURRENT" ->
                throw new ServiceException("基线/当前方案只读，不能创建排产方案");
            default -> throw new ServiceException("不支持的排产场景: " + scenarioCode);
        };
    }

    private String targetLabel(DecisionBusinessRef target) {
        if (target.code() != null && !target.code().isBlank()) {
            return target.code();
        }
        return target.type().name() + "/" + target.id();
    }

    Date currentBusinessTime() {
        return Date.from(ZonedDateTime.now(BUSINESS_ZONE).toInstant());
    }

    private LocalDate toLocalDate(Date value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return Instant.ofEpochMilli(value.getTime()).atZone(BUSINESS_ZONE).toLocalDate();
    }

    private BigDecimal normalizedDecimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }

    private String versionMarker(Integer version) {
        if (version == null || version < 0) {
            throw new ServiceException("业务对象缺少有效乐观版本");
        }
        return "V:" + version;
    }

    private Integer parseVersion(String marker) {
        if (marker == null || !marker.startsWith("V:")) {
            return null;
        }
        String text = marker.substring(2);
        if (text.isEmpty() || !text.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            int version = Integer.parseInt(text);
            return version < 0 ? null : version;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Map<String, Object> baseResult(String status, Integer version, long runId, long actionId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", status);
        result.put("versionMarker", versionMarker(version));
        result.put("runId", String.valueOf(runId));
        result.put("actionId", String.valueOf(actionId));
        return result;
    }

    private DecisionActionExecutionResult success(String bizType, Long bizId, String bizCode,
                                                   Map<String, Object> result) {
        return new DecisionActionExecutionResult(true, false, bizType, bizId, bizCode,
            Collections.unmodifiableMap(new LinkedHashMap<>(result)), null, null);
    }

    private DecisionActionExecutionResult verifyFailure(DecisionActionExecutionResult original,
                                                        String errorCode, String errorMessage) {
        return new DecisionActionExecutionResult(false, false, original.resultBizType(), original.resultBizId(),
            original.resultBizCode(), original.result(), errorCode, errorMessage);
    }

    private record LineFingerprint(Long materialId, String materialCode, String materialName,
                                   BigDecimal applyQuantity, BigDecimal shortageQuantity,
                                   LocalDate requiredDate, String sourceType, Long sourceId)
        implements Comparable<LineFingerprint> {

        private static final Comparator<LineFingerprint> ORDER = Comparator
            .comparing(LineFingerprint::materialId, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(LineFingerprint::materialCode, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(LineFingerprint::materialName, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(LineFingerprint::applyQuantity, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(LineFingerprint::shortageQuantity, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(LineFingerprint::requiredDate, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(LineFingerprint::sourceType, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(LineFingerprint::sourceId, Comparator.nullsFirst(Comparator.naturalOrder()));

        @Override
        public int compareTo(LineFingerprint other) {
            return ORDER.compare(this, other);
        }
    }

    private record PurchaseIntent(long runId, LocalDate requiredDate, List<PurchaseLineIntent> lines) {
    }

    private record PurchaseLineIntent(long materialId, String materialCode, String materialName,
                                      BigDecimal applyQuantity, BigDecimal shortageQuantity,
                                      LocalDate requiredDate) {
    }

    private record ScheduleIntent(long runId, String scenarioCode, String strategy,
                                  LocalDate startDate, int horizonDays, List<Long> workOrderIds,
                                  Long workshopId, String workOrderIdsJson) {
    }

    private record ValidatedResult(String marker, long runId, long actionId) {
    }
}
