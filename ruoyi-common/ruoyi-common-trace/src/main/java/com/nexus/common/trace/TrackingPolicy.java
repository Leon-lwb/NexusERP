package com.nexus.common.trace;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 来自物料主数据的版本化策略快照，不能用客户端参数替代。
 *
 * @param batchManaged 是否要求批次跟踪。
 * @param serialManaged 是否要求逐实物身份跟踪。
 * @param allowMixedLot 同一产品单件的同种组件是否允许使用多个批次。
 * @param version 当前事实或规则版本。
 * @param stage 追踪策略当前所处的审核或严格阶段。
 */
public record TrackingPolicy(
        boolean batchManaged,
        boolean serialManaged,
        boolean allowMixedLot,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long version,
        TrackingStage stage) {
    public TrackingPolicy {
        if (version == null || version < 1 || stage == null) {
            throw new IllegalArgumentException("追踪策略版本或阶段无效");
        }
    }

    public static TrackingPolicy of(
            Long batch, Long serial, Boolean mixed, Long version, String stage) {
        return new TrackingPolicy(
                flag(batch),
                flag(serial),
                mixed == null || mixed,
                version == null ? 1L : version,
                stage == null ? TrackingStage.AUDIT : TrackingStage.valueOf(stage));
    }

    private static boolean flag(Long value) {
        if (value == null || value == 0L) {
            return false;
        }
        if (value == 1L) {
            return true;
        }
        throw new IllegalArgumentException("物料追踪开关必须为0或1");
    }

    public TrackingMode mode() {
        return batchManaged
                ? (serialManaged ? TrackingMode.BATCH_SERIAL : TrackingMode.BATCH)
                : (serialManaged ? TrackingMode.SERIAL : TrackingMode.QUANTITY);
    }
}
