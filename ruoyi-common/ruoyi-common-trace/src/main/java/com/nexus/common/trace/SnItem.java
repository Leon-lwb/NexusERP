package com.nexus.common.trace;
import com.fasterxml.jackson.annotation.JsonFormat;
/** 一件实物的单据选择，不以调用方传入的编码或数量作为库存事实。 */
public record SnItem(@JsonFormat(shape=JsonFormat.Shape.STRING) Long identityId, @JsonFormat(shape=JsonFormat.Shape.STRING) Long sourceStockId, @JsonFormat(shape=JsonFormat.Shape.STRING) Long targetLocationId, @JsonFormat(shape=JsonFormat.Shape.STRING) Long originalTxId, @JsonFormat(shape=JsonFormat.Shape.STRING) Long batchId) {
    public SnItem {
        if (identityId == null || identityId <= 0) throw new IllegalArgumentException("单件身份不能为空");
        for (Long id : new Long[]{sourceStockId,targetLocationId,originalTxId,batchId}) {
            if (id != null && id <= 0) throw new IllegalArgumentException("单件来源引用无效");
        }
    }
}
