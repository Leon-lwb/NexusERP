package com.nexus.common.trace;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 一个已通过访问范围检查的扫码候选。编码为正式编码，原扫码内容保留在调用方请求。
 *
 * @param objectType 解析得到的业务对象类型。
 * @param objectId 解析对象在所属模块内的主键。
 * @param identityId ERP 序列号主档主键，作为跨模块稳定实物身份。
 * @param localSnId 制造模块本地单件主键，保留原接口含义。
 * @param canonicalSnCode 库存统一登记的正式单件编码。
 * @param materialId 当前业务行实际物料标识。
 * @param materialCode 与物料标识一致的主数据编码。
 * @param batchId 库存批次身份；与制造子批次标识分别保存。
 * @param policy 来源业务已冻结的追踪策略。
 * @param documentType 原业务单据类型。
 * @param documentCode 原业务单据编码。
 */
public record CodeCandidate(
        CodeObjectType objectType,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long objectId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long identityId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long localSnId,
        String canonicalSnCode,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long materialId,
        String materialCode,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long batchId,
        String batchCode,
        TrackingPolicy policy,
        String documentType,
        String documentCode) {
    public CodeCandidate(
            CodeObjectType type,
            Long objectId,
            Long identityId,
            Long localSnId,
            String code,
            Long materialId,
            String materialCode,
            Long batchId,
            String batchCode,
            TrackingPolicy policy) {
        this(
                type,
                objectId,
                identityId,
                localSnId,
                code,
                materialId,
                materialCode,
                batchId,
                batchCode,
                policy,
                null,
                null);
    }

    public CodeCandidate(
            CodeObjectType type,
            Long identityId,
            Long localSnId,
            String code,
            Long materialId,
            String materialCode,
            Long batchId,
            String batchCode,
            TrackingPolicy policy) {
        this(
                type,
                type == CodeObjectType.SN
                        ? identityId
                        : type == CodeObjectType.BATCH ? batchId : materialId,
                identityId,
                localSnId,
                code,
                materialId,
                materialCode,
                batchId,
                batchCode,
                policy);
    }

    public String snCode() {
        return canonicalSnCode;
    }
}
