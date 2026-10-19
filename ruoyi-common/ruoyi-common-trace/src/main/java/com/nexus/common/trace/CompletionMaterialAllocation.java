package com.nexus.common.trace;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

/**
 * 记录一笔实际工单消耗，原分配额度标识保留在不可变的来源证据内。
 *
 * @param allocationId 原来源分配额度标识。
 * @param materialId 当前业务行实际物料标识。
 * @param reservationId 正式身份预留单标识。
 * @param reservationLineId 原业务预留行标识。
 * @param postingGroupId 本次完整过账集合的稳定组标识。
 * @param identityId ERP 序列号主档主键，作为跨模块稳定实物身份。
 * @param sourceStockId 原库存行引用，保留领用或移库时的真实来源。
 * @param erpBatchId 经来源凭据核实的库存批次身份。
 * @param quantity 本行实际组件用料数量，汇总必须与原实际消耗核对。
 * @param sourceVersion 原来源事实版本。
 * @param evidenceHash 来源凭据内容摘要。
 * @param sourceEventId 已提交的原来源事件标识。
 */
public record CompletionMaterialAllocation(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long allocationId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long materialId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long reservationId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long reservationLineId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long postingGroupId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long identityId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long sourceStockId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long erpBatchId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal quantity,
        @JsonFormat(shape = JsonFormat.Shape.STRING) long sourceVersion,
        String evidenceHash,
        String sourceEventId) {}
