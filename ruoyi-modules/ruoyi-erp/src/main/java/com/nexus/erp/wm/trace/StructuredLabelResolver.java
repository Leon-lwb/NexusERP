package com.nexus.erp.wm.trace;

import com.fasterxml.jackson.databind.*;
import com.nexus.common.trace.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.*;
import java.util.function.BiFunction;

/**
 * 图像识别字段作为校验断言，与有权限的主数据求交集后解析完整身份。
 */
@Component
@RequiredArgsConstructor
public class StructuredLabelResolver {
    private final ObjectMapper json;

    public boolean supports(String raw) {
        return raw != null && raw.stripLeading().startsWith("{");
    }

    public CodeResolution resolve(
            CodeResolveRequest context,
            Set<CodeObjectType> allowed,
            BiFunction<CodeResolveRequest, Set<CodeObjectType>, CodeResolution> lookup) {
        return resolve(context, allowed, null, lookup);
    }

    public CodeResolution resolve(
            CodeResolveRequest context,
            Set<CodeObjectType> allowed,
            CodeResolution carrier,
            BiFunction<CodeResolveRequest, Set<CodeObjectType>, CodeResolution> lookup) {
        if (context.rawCode().length() > 32768) {
            return CodeResolution.invalid("标签识别对象过大");
        }
        JsonNode label;
        try {
            label = json.readTree(context.rawCode());
        } catch (Exception e) {
            return CodeResolution.invalid("标签识别对象不是有效JSON");
        }
        if (label == null || !label.isObject()) {
            return CodeResolution.invalid("标签识别结果必须为完整对象");
        }
        String primary =
                first(label, "snCode", "canonicalSnCode", "batchCode", "materialCode", "rawCode");
        if (carrier == null && (primary == null || primary.length() > 512 || supports(primary))) {
            return CodeResolution.invalid("标签缺少可解析的SN、批次或物料编码");
        }
        CodeObjectType primaryType =
                text(label, "snCode") != null || text(label, "canonicalSnCode") != null
                        ? CodeObjectType.SN
                        : text(label, "batchCode") != null
                                ? CodeObjectType.BATCH
                                : text(label, "materialCode") != null
                                        ? CodeObjectType.MATERIAL
                                        : null;
        if (carrier == null && primaryType != null && !allowed.contains(primaryType)) {
            return CodeResolution.invalid("标签主对象不在当前授权范围");
        }
        CodeResolution result =
                carrier == null
                        ? lookup.apply(
                                request(context, primary),
                                primaryType == null ? allowed : Set.of(primaryType))
                        : carrier;
        if (carrier == null
                && primaryType != null
                && (result.status() == ResolveStatus.NOT_FOUND
                        || result.candidates().stream()
                                .anyMatch(candidate -> candidate.objectType() != primaryType))) {
            return CodeResolution.invalid("标签声明的主对象不存在或类型不一致");
        }
        if (result.status() != ResolveStatus.RESOLVED
                && result.status() != ResolveStatus.AMBIGUOUS) {
            return result;
        }
        List<CodeCandidate> candidates = new ArrayList<>(result.candidates());
        if (carrier != null && text(label, "snCode") != null) {
            if (!allowed.contains(CodeObjectType.SN)) {
                return CodeResolution.invalid("标签SN断言不在当前授权范围");
            }
            var evidence =
                    lookup.apply(
                            request(context, text(label, "snCode")), Set.of(CodeObjectType.SN));
            if (evidence.status() != ResolveStatus.RESOLVED
                    && evidence.status() != ResolveStatus.AMBIGUOUS) {
                return CodeResolution.invalid("登记条码与标签SN断言不一致");
            }
            candidates.removeIf(
                    candidate ->
                            candidate.objectType() != CodeObjectType.SN
                                    || evidence.candidates().stream()
                                            .noneMatch(
                                                    other ->
                                                            Objects.equals(
                                                                    candidate.identityId(),
                                                                    other.identityId())));
        }
        for (String field : List.of("materialCode", "batchCode")) {
            String raw = text(label, field);
            if (raw == null) {
                continue;
            }
            var matching = new ArrayList<CodeCandidate>();
            for (var candidate : candidates) {
                if (raw.equals(
                        field.equals("materialCode")
                                ? candidate.materialCode()
                                : candidate.batchCode())) {
                    matching.add(candidate);
                }
            }
            CodeObjectType type =
                    field.equals("materialCode") ? CodeObjectType.MATERIAL : CodeObjectType.BATCH;
            if (allowed.contains(type)) {
                CodeResolution evidence = lookup.apply(request(context, raw), Set.of(type));
                if (evidence.status() == ResolveStatus.RESOLVED
                        || evidence.status() == ResolveStatus.AMBIGUOUS) {
                    for (var candidate : candidates) {
                        if (!matching.contains(candidate)
                                && evidence.candidates().stream()
                                        .anyMatch(
                                                other ->
                                                        Objects.equals(
                                                                        candidate.materialId(),
                                                                        other.materialId())
                                                                && (type == CodeObjectType.MATERIAL
                                                                        || candidate.batchId()
                                                                                        != null
                                                                                && Objects.equals(
                                                                                        candidate
                                                                                                .batchId(),
                                                                                        other
                                                                                                .batchId())))) {
                            matching.add(candidate);
                        }
                    }
                }
            }
            candidates = matching;
            if (candidates.isEmpty()) {
                return CodeResolution.invalid("标签的物料、批次与SN身份不一致或无权确认，原单据保持原值");
            }
        }
        String originalCode = text(label, "rawCode");
        if (originalCode != null && (carrier != null || !originalCode.equals(primary))) {
            if (originalCode.length() > 512 || supports(originalCode)) {
                return CodeResolution.invalid("标签原码无效");
            }
            Set<CodeObjectType> types = new HashSet<>();
            candidates.forEach(candidate -> types.add(candidate.objectType()));
            types.retainAll(allowed);
            if (types.isEmpty()) {
                return CodeResolution.invalid("标签原码不在当前授权范围");
            }
            var evidence = lookup.apply(request(context, originalCode), types);
            if (evidence.status() != ResolveStatus.RESOLVED
                    && evidence.status() != ResolveStatus.AMBIGUOUS) {
                return CodeResolution.invalid("标签原码未确认同一主对象");
            }
            candidates.removeIf(
                    candidate ->
                            evidence.candidates().stream()
                                    .noneMatch(
                                            other ->
                                                    candidate.objectType() == other.objectType()
                                                            && candidate.objectId() != null
                                                            && candidate
                                                                    .objectId()
                                                                    .equals(other.objectId())
                                                            && Objects.equals(
                                                                    candidate.documentType(),
                                                                    other.documentType())));
        }
        String canonical = text(label, "canonicalSnCode");
        if (canonical != null) {
            candidates.removeIf(candidate -> !canonical.equals(candidate.canonicalSnCode()));
        }
        for (String field : List.of("identityId", "localSnId", "materialId", "batchId")) {
            if (label.hasNonNull(field)) {
                Long expected = identifier(label.get(field));
                if (expected == null) {
                    return CodeResolution.invalid("标签身份字段必须是无精度损失的正整数字符串");
                }
                candidates.removeIf(
                        candidate -> !Objects.equals(expected, value(candidate, field)));
            }
        }
        JsonNode matched = label.get("matchedMaterial");
        if (matched != null && matched.isObject()) {
            if (matched.hasNonNull("materialId")) {
                Long expected = identifier(matched.get("materialId"));
                if (expected == null) {
                    return CodeResolution.invalid("OCR匹配物料ID无效");
                }
                candidates.removeIf(candidate -> !Objects.equals(expected, candidate.materialId()));
            }
            String code = text(matched, "materialCode");
            if (code != null) {
                candidates.removeIf(candidate -> !code.equals(candidate.materialCode()));
            }
        }
        if (candidates.isEmpty()) {
            return CodeResolution.invalid("OCR编码与识别ID不一致，禁止局部替换");
        }
        if (candidates.size() > 1) {
            return CodeResolution.ambiguous(candidates);
        }
        return CodeResolution.resolved(candidates.get(0));
    }

    private static Long value(CodeCandidate candidate, String field) {
        return switch (field) {
            case "identityId" -> candidate.identityId();
            case "localSnId" -> candidate.localSnId();
            case "materialId" -> candidate.materialId();
            case "batchId" -> candidate.batchId();
            default -> null;
        };
    }

    private static Long identifier(JsonNode value) {
        try {
            if (!value.isTextual()
                    && (!value.isIntegralNumber()
                            || value.bigIntegerValue()
                                            .abs()
                                            .compareTo(BigInteger.valueOf(9007199254740991L))
                                    > 0)) {
                return null;
            }
            String text = value.asText();
            if (!text.matches("[1-9][0-9]{0,18}")) {
                return null;
            }
            return Long.valueOf(text);
        } catch (Exception e) {
            return null;
        }
    }

    private static String first(JsonNode label, String... fields) {
        for (String field : fields) {
            String value = text(label, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode label, String field) {
        JsonNode value = label.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank()
                ? value.asText()
                : null;
    }

    private static CodeResolveRequest request(CodeResolveRequest context, String raw) {
        return new CodeResolveRequest(
                raw,
                context.scene(),
                context.materialId(),
                context.supplierId(),
                context.customerId(),
                context.sourceDocType(),
                context.sourceDocId(),
                context.lineId());
    }
}
