package com.nexus.common.trace;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;

/**
 * 稳定的多端解析结果；歧义结果不包含默认选中的根对象。
 *
 * @param status 解析或业务处理的明确状态。
 * @param objectType 解析得到的业务对象类型。
 * @param objectId 解析对象在所属模块内的主键。
 * @param identityId ERP 序列号主档主键，作为跨模块稳定实物身份。
 * @param localSnId 制造模块本地单件主键，保留原接口含义。
 * @param canonicalSnCode 库存统一登记的正式单件编码。
 * @param snCode 已确认身份对应的单件编码。
 * @param materialId 当前业务行实际物料标识。
 * @param materialCode 与物料标识一致的主数据编码。
 * @param batchId 库存批次身份；与制造子批次标识分别保存。
 * @param policy 来源业务已冻结的追踪策略。
 * @param candidates 当前调用方有权限查看的候选，歧义时必须显式选择。
 * @param reason 与原任务或原事实关联的明确操作原因。
 * @param documentType 原业务单据类型。
 * @param documentCode 原业务单据编码。
 */
public record CodeResolution(
        ResolveStatus status,
        CodeObjectType objectType,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long objectId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long identityId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long localSnId,
        String canonicalSnCode,
        String snCode,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long materialId,
        String materialCode,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long batchId,
        String batchCode,
        TrackingPolicy policy,
        List<CodeCandidate> candidates,
        String reason,
        String documentType,
        String documentCode) {
    public CodeResolution {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public static CodeResolution resolved(CodeCandidate c) {
        return new CodeResolution(
                ResolveStatus.RESOLVED,
                c.objectType(),
                c.objectId(),
                c.identityId(),
                c.localSnId(),
                c.canonicalSnCode(),
                c.canonicalSnCode(),
                c.materialId(),
                c.materialCode(),
                c.batchId(),
                c.batchCode(),
                c.policy(),
                List.of(c),
                null,
                c.documentType(),
                c.documentCode());
    }

    public static CodeResolution ambiguous(List<CodeCandidate> candidates) {
        if (candidates == null || candidates.size() < 2) {
            throw new IllegalArgumentException("歧义结果需要多个候选");
        }
        return empty(ResolveStatus.AMBIGUOUS, candidates, "同一码匹配多个对象，请补充来源上下文");
    }

    public static CodeResolution missing(String reason) {
        return empty(ResolveStatus.NOT_FOUND, List.of(), reason);
    }

    public static CodeResolution invalid(String reason) {
        return empty(ResolveStatus.INVALID, List.of(), reason);
    }

    private static CodeResolution empty(
            ResolveStatus status, List<CodeCandidate> candidates, String reason) {
        return new CodeResolution(
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                candidates,
                reason,
                null,
                null);
    }
}
