package com.nexus.erp.ai.adapter;

import com.nexus.common.ai.decision.dto.DecisionActionExecutionResult;
import com.nexus.common.ai.decision.dto.DecisionActionProposal;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.domain.bo.PuCertifiedPurchaseCommand;
import com.nexus.erp.pu.domain.vo.PuCertifiedPurchaseResult;
import com.nexus.erp.pu.service.PuCertifiedPurchaseWriteService;
import com.nexus.erp.pu.service.PuCertifiedPurchaseWriteService.FrozenLine;
import com.nexus.erp.pu.service.PuCertifiedPurchaseWriteService.FrozenContract;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * AI v2 的单物料采购契约边界，不创建通用采购 BO，不升级旧 payload，也不签发来源证书。
 * 冻结数值只是待核对意图；writer 在自己的 ERP RR 内认证来源/指纹、逐行比较并恢复永久 command。
 * 完整指纹（含目标、版本及逐行冻结）与九项元数据必须传给 writer 永久绑定，不能只留在结果信封。
 * 物料/供应商版本是目录认证的内容摘要，不代表完整 ERP 主数据变更版本；UOM 版本来自换算合同。
 * writer 独立核对当前目录、来源指纹和冻结行；这些字段及显示名称本身不是来源证书。
 * 所有公开作业均不得被外层事务包裹；master AI 回写与 ERP 提交不构成跨库 ACID。
 */
@Component
public class ErpCertifiedPurchaseDecisionAdapter {
    private static final DecisionActionType TYPE = DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT;
    private static final String BUSINESS_TYPE = "PU_PURCHASE_REQUISITION";
    private static final Set<String> PAYLOAD_FIELDS = Set.of(
        "purchase_payload_version", "runId", "materialId", "vendorId", "windowEnd",
        "materialVersion", "vendorVersion", "supplierTermsId", "supplierTermsVersion",
        "baseUomId", "baseUomVersion", "orderUomId", "orderUomVersion", "conversionVersion",
        "requirement_detail_ids", "frozenLines", "expectedSourceFingerprint");
    private static final List<String> VERSION_FIELDS = List.of("materialVersion", "vendorVersion",
        "supplierTermsVersion", "baseUomVersion", "orderUomVersion", "conversionVersion");
    private static final List<String> CONTRACT_METADATA_FIELDS = List.of("materialVersion", "vendorVersion",
        "supplierTermsId", "supplierTermsVersion", "baseUomId", "baseUomVersion", "orderUomId", "orderUomVersion",
        "conversionVersion");
    private static final Set<String> FROZEN_FIELDS = Set.of("detailId", "baseUomId", "coveredDemandBase",
        "orderUomId", "requestedOrderQty", "requiredDate");
    private static final Set<String> RESULT_FIELDS = Set.of("purchase_payload_version", "runId", "actionId",
        "tenantId", "requestKey", "materialId", "vendorId", "sourceType", "sourceFingerprint",
        "payloadFingerprint", "status", "versionMarker", "beforeState", "afterState", "replayed", "lines");
    private static final Set<String> RESULT_LINE_FIELDS = Set.of("lineId", "requirementDetailId", "materialId",
        "baseUomId", "orderUomId", "coveredDemandBase", "requestedOrderQty", "requiredDate");
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("99999999999999.9999");
    private final PuCertifiedPurchaseWriteService writer;

    public ErpCertifiedPurchaseDecisionAdapter(PuCertifiedPurchaseWriteService writer) {
        this.writer = Objects.requireNonNull(writer);
    }

    public DecisionActionExecutionResult execute(DecisionActionProposal action, DecisionActor actor) {
        Intent intent = requireIntent(action, actor);
        PuCertifiedPurchaseResult committed = writer.createForDecision(intent.command(), actor,
            intent.runId(), action.actionId(), intent.lines(), intent.contract());
        requireReceipt(intent, committed);
        if (!"DRAFT".equals(committed.status()) || !Integer.valueOf(0).equals(committed.version())) {
            return new DecisionActionExecutionResult(false, false, BUSINESS_TYPE, committed.requisitionId(),
                committed.requisitionNo(), Map.of("status", committed.status(), "replayed", committed.replayed()),
                "VERIFY_STATE_MISMATCH", "原采购单据已变更，不能重新创建或声称新草稿执行成功");
        }
        return new DecisionActionExecutionResult(true, false, BUSINESS_TYPE, committed.requisitionId(),
            committed.requisitionNo(), resultDetails(action, actor, intent, committed, committed.replayed()), null, null);
    }

    /** 只恢复已提交 command；缺记录绝不能为了验证而创建单据或分配新编号。 */
    public DecisionActionExecutionResult verify(DecisionActionProposal action,
                                               DecisionActionExecutionResult result, DecisionActor actor) {
        Intent intent = requireIntent(action, actor);
        boolean replayed = requireEnvelope(action, actor, intent, result);
        PuCertifiedPurchaseResult current = writer.recoverForDecision(intent.command(), actor,
            intent.runId(), action.actionId(), intent.lines(), intent.contract());
        if (current == null) return failure(result, "VERIFY_NOT_FOUND", "未找到该冻结动作的永久采购提交");
        try {
            requireReceipt(intent, current);
        } catch (ServiceException conflict) {
            return failure(result, "VERIFY_CONFLICT", "真实采购来源或申请行与冻结动作不一致");
        }
        if (!"DRAFT".equals(current.status())) {
            return failure(result, "VERIFY_STATE_MISMATCH", "采购申请已不再是草稿状态");
        }
        if (!current.replayed() || !Integer.valueOf(0).equals(current.version())
            || !current.requisitionId().equals(result.resultBizId())
            || !current.requisitionNo().equals(result.resultBizCode())
            || !resultDetails(action, actor, intent, current, replayed).equals(result.result())) {
            return failure(result, "VERIFY_CONFLICT", "采购永久结果、版本或前后状态与冻结结果不一致");
        }
        return result;
    }

    /**
     * 逐 allocation 的生命周期调用不等于整单补偿。D 未提供整单 CAS + 逐行释放 + 永久 command
     * 终态标记的专属 ERP 原子入口前，显式拒绝；不调用旧 deleteDraftIfUnchanged 或跨库伪回滚。
     */
    public DecisionActionExecutionResult compensate(DecisionActionProposal action,
                                                   DecisionActionExecutionResult result, DecisionActor actor) {
        Intent intent = requireIntent(action, actor);
        requireEnvelope(action, actor, intent, result);
        return failure(result, "COMPENSATION_NOT_SUPPORTED", "可信采购整单生命周期补偿尚未接通，禁止自动删除或释放占用");
    }

    private static Intent requireIntent(DecisionActionProposal action, DecisionActor actor) {
        require(!TransactionSynchronizationManager.isActualTransactionActive()
            && !TransactionSynchronizationManager.isSynchronizationActive()
            && TransactionSynchronizationManager.getResourceMap().isEmpty(), "专属 ERP 作业不能加入外层事务");
        require(actor != null && TenantHelper.isEnable() && actor.tenantId() != null
            && actor.tenantId().matches("[0-9]{6,20}") && actor.tenantId().equals(TenantHelper.getTenantId())
            && positive(actor.userId()) && actor.userName() != null && !actor.userName().isBlank()
            && actor.userName().length() <= 64 && actor.permissions() != null
            && (actor.permissions().contains("*:*:*") || (actor.permissions().contains("ai:decision:execute")
                && actor.permissions().contains(TYPE.permissionCode()))), "当前租户、执行身份或采购权限无效");
        require(action != null && positive(action.actionId()) && action.actionType() == TYPE
            && TYPE.executorCode().equals(action.executorCode()) && TYPE.permissionCode().equals(action.permissionCode())
            && action.approvalRequired() && action.target() != null && action.target().type() != null
            && (action.target().type() == DecisionBusinessType.SHORTAGE_HORIZON ? action.target().id() == null
                : positive(action.target().id())), "采购动作身份、目标或固定元数据无效");
        require(action.idempotencyKey() != null && action.idempotencyKey().matches("[A-Za-z0-9:_-]{16,128}"),
            "采购永久请求键无效");
        Map<String, Object> payload = action.payload();
        requireFields(payload, PAYLOAD_FIELDS, "采购 v2 载荷字段缺失或含旧版/额外字段");
        require(Integer.valueOf(2).equals(payload.get("purchase_payload_version")), "采购载荷必须为显式整数 v2，旧动作不可升级");
        Long runId = id(payload.get("runId"));
        Long materialId = id(payload.get("materialId"));
        Long vendorId = id(payload.get("vendorId"));
        Long baseUomId = id(payload.get("baseUomId"));
        Long orderUomId = id(payload.get("orderUomId"));
        id(payload.get("supplierTermsId"));
        for (String field : VERSION_FIELDS) version(payload.get(field));
        // 当前统一 writer 只支持已核根基本单位且无超额；不能先用展示名称或派生换算放行。
        require(baseUomId.equals(orderUomId) && payload.get("baseUomVersion").equals(payload.get("orderUomVersion"))
            && payload.get("baseUomVersion").equals(payload.get("conversionVersion")),
            "当前仅支持同根基本单位、同版本、无超额的采购");
        LocalDate windowEnd = date(payload.get("windowEnd"));
        Object expectedFingerprint = payload.get("expectedSourceFingerprint");
        require(expectedFingerprint instanceof String hash && hash.matches("[0-9a-f]{64}"), "冻结来源指纹缺失或格式无效");
        require(payload.get("requirement_detail_ids") instanceof List<?> ids && !ids.isEmpty() && ids.size() <= 1000,
            "逐 MRP 冻结来源集合缺失或超限");
        List<Long> detailIds = new ArrayList<>();
        long previous = 0;
        for (Object rawId : (List<?>) payload.get("requirement_detail_ids")) {
            Long detailId = id(rawId);
            require(detailId > previous, "逐 MRP 来源必须唯一并按身份排序");
            detailIds.add(detailId); previous = detailId;
        }
        require(payload.get("frozenLines") instanceof List<?> lines && lines.size() == detailIds.size(),
            "冻结来源行必须与 MRP 来源集合逐项一致");
        List<FrozenLine> frozen = new ArrayList<>();
        int index = 0;
        for (Object rawLine : (List<?>) payload.get("frozenLines")) {
            require(rawLine instanceof Map<?, ?>, "冻结来源行必须为对象");
            Map<?, ?> line = (Map<?, ?>) rawLine;
            requireFields(line, FROZEN_FIELDS, "冻结来源行字段不完整或含客户端数量/其他物料");
            Long detailId = id(line.get("detailId"));
            require(detailIds.get(index++).equals(detailId) && baseUomId.equals(id(line.get("baseUomId")))
                && orderUomId.equals(id(line.get("orderUomId"))), "冻结来源行身份或基本/订购单位不一致");
            BigDecimal covered = quantity(line.get("coveredDemandBase"));
            BigDecimal ordered = quantity(line.get("requestedOrderQty"));
            LocalDate required = date(line.get("requiredDate"));
            require(covered.compareTo(ordered) == 0 && !required.isAfter(windowEnd), "冻结覆盖量/订购量有超额或来源日期超出窗口");
            frozen.add(new FrozenLine(detailId, baseUomId, covered, orderUomId, ordered, required));
        }
        var command = new PuCertifiedPurchaseCommand(action.idempotencyKey(), materialId, vendorId,
            windowEnd, detailIds, (String) expectedFingerprint);
        Map<String, String> metadata = new HashMap<>();
        // 仅提取已通过上方严格解析的字段，不补造版本或以原始 payload 的可变容器作为永久合同。
        CONTRACT_METADATA_FIELDS.forEach(field -> metadata.put(field, (String) payload.get(field)));
        var contract = new FrozenContract(payloadFingerprint(action, actor, payload, frozen), Map.copyOf(metadata));
        return new Intent(command, runId, List.copyOf(frozen), contract);
    }

    private static boolean requireEnvelope(DecisionActionProposal action, DecisionActor actor, Intent intent,
                                          DecisionActionExecutionResult result) {
        require(result != null && result.success() && !result.retryable() && result.errorCode() == null
            && result.errorMessage() == null && BUSINESS_TYPE.equals(result.resultBizType())
            && positive(result.resultBizId()) && validCode(result.resultBizCode()), "仅有效成功采购结果可验证或补偿");
        Map<String, Object> details = result.result();
        requireFields(details, RESULT_FIELDS, "采购结果信封字段无效");
        require(details.get("replayed") instanceof Boolean, "采购重放结果标记无效");
        boolean replayed = (Boolean) details.get("replayed");
        require(Integer.valueOf(2).equals(details.get("purchase_payload_version"))
            && intent.runId().toString().equals(details.get("runId"))
            && action.actionId().toString().equals(details.get("actionId"))
            && actor.tenantId().equals(details.get("tenantId"))
            && intent.command().requestKey().equals(details.get("requestKey"))
            && intent.command().materialId().toString().equals(details.get("materialId"))
            && intent.command().vendorId().toString().equals(details.get("vendorId"))
            && "AI_DECISION".equals(details.get("sourceType"))
            && intent.command().expectedSourceFingerprint().equals(details.get("sourceFingerprint"))
            && intent.contract().payloadFingerprint().equals(details.get("payloadFingerprint"))
            && "DRAFT".equals(details.get("status")) && "DRAFT".equals(details.get("afterState"))
            && "v0".equals(details.get("versionMarker"))
            && (replayed ? "COMMITTED" : "ABSENT").equals(details.get("beforeState")),
            "采购结果来源、合同、版本或前后状态与冻结动作不一致");
        require(details.get("lines") instanceof List<?> rows && rows.size() == intent.lines().size(), "采购结果逐来源行缺失");
        Set<Long> seen = new HashSet<>();
        int index = 0;
        for (Object raw : (List<?>) details.get("lines")) {
            require(raw instanceof Map<?, ?>, "采购结果行无效");
            Map<?, ?> row = (Map<?, ?>) raw;
            requireFields(row, RESULT_LINE_FIELDS, "采购结果行字段无效");
            Long lineId = id(row.get("lineId"));
            require(seen.add(lineId) && resultLine(lineId, intent.command().materialId(), intent.lines().get(index++)).equals(row),
                "采购结果行身份、基本单位或数值与冻结动作不一致");
        }
        return replayed;
    }

    private static void requireReceipt(Intent intent, PuCertifiedPurchaseResult receipt) {
        require(receipt != null && positive(receipt.requisitionId()) && validCode(receipt.requisitionNo())
            && receipt.status() != null && receipt.version() != null && receipt.version() >= 0
            && intent.command().expectedSourceFingerprint().equals(receipt.sourceFingerprint())
            && receipt.lines() != null && receipt.lines().size() == intent.lines().size(), "writer 未返回可核对的真实采购提交");
        Map<Long, FrozenLine> expected = new HashMap<>();
        intent.lines().forEach(line -> expected.put(line.detailId(), line));
        Set<Long> lineIds = new HashSet<>();
        Set<Long> detailIds = new HashSet<>();
        for (var line : receipt.lines()) {
            require(line != null && positive(line.lineId()) && lineIds.add(line.lineId())
                && positive(line.requirementDetailId()) && detailIds.add(line.requirementDetailId())
                && intent.command().materialId().equals(line.materialId()), "实际采购申请行身份或物料不一致");
            FrozenLine frozen = expected.get(line.requirementDetailId());
            require(frozen != null && frozen.baseUomId().equals(line.baseUomId()) && validQuantity(line.applyQuantity())
                && frozen.coveredDemandBase().compareTo(line.applyQuantity()) == 0
                && frozen.orderQuantity().compareTo(line.applyQuantity()) == 0
                && frozen.requiredDate().equals(line.requiredDate()), "实际采购申请行单位、数量或来源日期与冻结动作不一致");
        }
    }

    private static Map<String, Object> resultDetails(DecisionActionProposal action, DecisionActor actor, Intent intent,
                                                   PuCertifiedPurchaseResult receipt, boolean replayed) {
        var lines = receipt.lines().stream().sorted(Comparator.comparing(PuCertifiedPurchaseResult.Line::requirementDetailId))
            .map(line -> Map.<String, Object>of("lineId", line.lineId().toString(),
                "requirementDetailId", line.requirementDetailId().toString(), "materialId", line.materialId().toString(),
                "baseUomId", line.baseUomId().toString(), "orderUomId", line.baseUomId().toString(),
                "coveredDemandBase", decimalText(line.applyQuantity()), "requestedOrderQty", decimalText(line.applyQuantity()),
                "requiredDate", line.requiredDate().toString())).toList();
        return Map.ofEntries(Map.entry("purchase_payload_version", 2), Map.entry("runId", intent.runId().toString()),
            Map.entry("actionId", action.actionId().toString()), Map.entry("tenantId", actor.tenantId()),
            Map.entry("requestKey", intent.command().requestKey()), Map.entry("materialId", intent.command().materialId().toString()),
            Map.entry("vendorId", intent.command().vendorId().toString()), Map.entry("sourceType", "AI_DECISION"),
            Map.entry("sourceFingerprint", receipt.sourceFingerprint()), Map.entry("payloadFingerprint", intent.contract().payloadFingerprint()),
            Map.entry("status", receipt.status()), Map.entry("versionMarker", "v" + receipt.version()),
            Map.entry("beforeState", replayed ? "COMMITTED" : "ABSENT"), Map.entry("afterState", receipt.status()),
            Map.entry("replayed", replayed), Map.entry("lines", lines));
    }

    private static Map<String, Object> resultLine(Long lineId, Long materialId, FrozenLine line) {
        return Map.of("lineId", lineId.toString(), "requirementDetailId", line.detailId().toString(),
            "materialId", materialId.toString(), "baseUomId", line.baseUomId().toString(),
            "orderUomId", line.orderUomId().toString(), "coveredDemandBase", decimalText(line.coveredDemandBase()),
            "requestedOrderQty", decimalText(line.orderQuantity()), "requiredDate", line.requiredDate().toString());
    }

    private static String payloadFingerprint(DecisionActionProposal action, DecisionActor actor,
                                             Map<String, Object> payload, List<FrozenLine> lines) {
        List<String> values = new ArrayList<>(List.of("certified-purchase:v2", actor.tenantId(), actor.userId().toString(),
            action.actionId().toString(), action.idempotencyKey(), action.target().type().name(),
            Objects.toString(action.target().id(), "")));
        PAYLOAD_FIELDS.stream().filter(field -> !field.equals("frozenLines") && !field.equals("requirement_detail_ids"))
            .sorted().forEach(field -> { values.add(field); values.add(payload.get(field).toString()); });
        for (FrozenLine line : lines) {
            values.add(line.detailId().toString()); values.add(line.baseUomId().toString());
            values.add(decimalText(line.coveredDemandBase())); values.add(line.orderUomId().toString());
            values.add(decimalText(line.orderQuantity())); values.add(line.requiredDate().toString());
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new ServiceException("可信采购结果合同摘要不可用");
        }
    }

    private static Long id(Object value) {
        require(value instanceof String text && text.matches("[1-9][0-9]{0,18}"), "标识必须为正十进制 ID 字符串，不接受单位名称");
        try { return Long.valueOf((String) value); }
        catch (NumberFormatException invalid) { throw new ServiceException("可信采购标识超出有效范围"); }
    }

    private static void version(Object value) {
        require(value instanceof String text && !text.isBlank() && text.equals(text.trim()) && text.length() <= 256
            && text.chars().noneMatch(Character::isISOControl), "物料、供应商、条款或 UOM 冻结版本缺失");
    }

    private static LocalDate date(Object value) {
        require(value instanceof String text && text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"), "来源日期必须为明确业务日期");
        try {
            LocalDate date = LocalDate.parse((String) value);
            require(date.getYear() >= 1000 && date.getYear() <= 9999, "来源业务日期超出范围");
            return date;
        } catch (DateTimeException invalid) { throw new ServiceException("可信采购来源业务日期无效"); }
    }

    private static BigDecimal quantity(Object value) {
        require(value instanceof String text && text.matches("(0|[1-9][0-9]{0,13})(\\.[0-9]{1,4})?"),
            "冻结数量必须为 DECIMAL(18,4) 的精确十进制字符串");
        BigDecimal quantity = new BigDecimal((String) value);
        require(validQuantity(quantity), "冻结数量必须为正且不溢出");
        return quantity;
    }

    private static boolean validQuantity(BigDecimal value) {
        return value != null && value.signum() > 0 && value.compareTo(MAX_QUANTITY) <= 0
            && value.stripTrailingZeros().scale() <= 4;
    }

    private static String decimalText(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private static boolean positive(Long value) { return value != null && value > 0; }
    private static boolean validCode(String value) { return value != null && value.matches("[A-Za-z0-9_-]{1,64}"); }
    private static void requireFields(Map<?, ?> values, Set<String> expected, String message) {
        require(values != null && values.keySet().equals(expected), message);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new ServiceException("可信采购决策拒绝：" + message);
    }
    private static DecisionActionExecutionResult failure(DecisionActionExecutionResult result, String code, String message) {
        return new DecisionActionExecutionResult(false, false, result.resultBizType(), result.resultBizId(),
            result.resultBizCode(), result.result(), code, message);
    }
    private record Intent(PuCertifiedPurchaseCommand command, Long runId, List<FrozenLine> lines, FrozenContract contract) { }
}
