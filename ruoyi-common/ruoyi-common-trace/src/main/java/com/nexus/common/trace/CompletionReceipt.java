package com.nexus.common.trace;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 完整库存回执；全部报废时保留无良品结果，不生成不存在的入库单或过账组。
 *
 * @param receiptId 原生产入库凭据标识；无良品时允许不存在。
 * @param postingGroupId 本次完整过账集合的稳定组标识。
 * @param manifestId 不可变完工清单标识。
 * @param revision 冻结清单修订号，必须与游标及事件一致。
 * @param manifestHash 原完工清单的完整摘要。
 * @param outcome 实际业务确认的合格或报废结果。
 */
public record CompletionReceipt(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long receiptId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long postingGroupId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long manifestId,
        int revision,
        String manifestHash,
        String outcome) {
    public CompletionReceipt(
            Long receiptId,
            Long postingGroupId,
            Long manifestId,
            int revision,
            String manifestHash) {
        this(
                receiptId,
                postingGroupId,
                manifestId,
                revision,
                manifestHash,
                receiptId == null && postingGroupId == null ? "NO_GOOD_OUTPUT" : "POSTED");
    }

    public CompletionReceipt {
        if (manifestId == null
                || manifestId <= 0
                || revision < 1
                || manifestHash == null
                || !manifestHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("完工回执清单身份不完整");
        }
        if ("POSTED".equals(outcome)) {
            if (receiptId == null
                    || receiptId <= 0
                    || postingGroupId == null
                    || postingGroupId <= 0) {
                throw new IllegalArgumentException("完工入库证明不完整");
            }
        } else if (!"NO_GOOD_OUTPUT".equals(outcome)
                || receiptId != null
                || postingGroupId != null) {
            throw new IllegalArgumentException("无良品回执不得伪造收货或库存效果");
        }
    }
}
