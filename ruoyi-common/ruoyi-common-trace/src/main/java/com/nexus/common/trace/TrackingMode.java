package com.nexus.common.trace;

/**
 * 物料的四种独立追踪策略。
 */
public enum TrackingMode {
    /**
     * 按数量管理，不要求实际批次或逐件身份。
     */
    QUANTITY,
    /**
     * 按实际批次及其用量管理。
     */
    BATCH,
    /**
     * 按稳定实物身份逐件管理。
     */
    SERIAL,
    /**
     * 同时保留稳定实物身份与实际批次。
     */
    BATCH_SERIAL
}
