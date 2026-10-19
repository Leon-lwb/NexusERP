package com.nexus.common.trace;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

/**
 * 一条冻结交付事实；SN行一件，非SN行按批次数量。
 *
 * @param lineNo 清单或命令内稳定行序。
 * @param identityId ERP 序列号主档主键，作为跨模块稳定实物身份。
 * @param localSnId 制造模块本地单件主键，保留原接口含义。
 * @param snCode 已确认身份对应的单件编码。
 * @param batchId 库存批次身份；与制造子批次标识分别保存。
 * @param localLotId 制造模块本地子批次标识。
 * @param materialId 当前业务行实际物料标识。
 * @param quantity 本行已完工或报废的实际产品数量。
 * @param result 本次业务执行的稳定结果。
 * @param sourceVersion 原来源事实版本。
 */
public record CompletionManifestLine(
        @JsonFormat(shape = JsonFormat.Shape.STRING) long lineNo,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long identityId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long localSnId,
        String snCode,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long batchId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long localLotId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long materialId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal quantity,
        String result,
        @JsonFormat(shape = JsonFormat.Shape.STRING) long sourceVersion) {}
