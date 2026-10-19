package com.nexus.common.trace;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * ERP正式实物身份；MES本地SN主键单独维护。
 *
 * @param identityId ERP 序列号主档主键，作为跨模块稳定实物身份。
 * @param snCode 已确认身份对应的单件编码。
 * @param materialId 当前业务行实际物料标识。
 * @param batchId 库存批次身份；与制造子批次标识分别保存。
 */
public record SerialIdentity(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long identityId,
        String snCode,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long materialId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long batchId,
        String batchCode) {}
