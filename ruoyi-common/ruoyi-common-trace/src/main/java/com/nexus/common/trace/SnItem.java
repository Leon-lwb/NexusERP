package com.nexus.common.trace;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 一件实物的单据选择，不以调用方传入的编码或数量作为库存事实。
 *
 * @param identityId ERP 序列号主档主键，作为跨模块稳定实物身份。
 * @param sourceStockId 原库存行引用，保留领用或移库时的真实来源。
 * @param targetLocationId 目标储位标识。
 * @param originalTxId 退回或更正关联的原出库流水。
 * @param batchId 库存批次身份；与制造子批次标识分别保存。
 */
public record SnItem(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long identityId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long sourceStockId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long targetLocationId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long originalTxId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long batchId) {
    public SnItem {
        if (identityId == null || identityId <= 0) {
            throw new IllegalArgumentException("单件身份不能为空");
        }
        for (Long id : new Long[] {sourceStockId, targetLocationId, originalTxId, batchId}) {
            if (id != null && id <= 0) {
                throw new IllegalArgumentException("单件来源引用无效");
            }
        }
    }
}
