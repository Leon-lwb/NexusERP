package com.nexus.common.trace;

/** 发号请求；命令绑定物料、数量、规则、来源及可选批次，不能重放为其他实物。 */
public record SerialReservationCommand(String commandId, Long materialId, int quantity, Long ruleId,
                                       String sourceType, Long sourceId, Long batchId) {
    /** 仅限制单次注册请求，不限制工单总量；更大工作量由编排使用独立命令分批。 */
    public static final int MAX_QUANTITY = 10_000;
    /** 保留MES六参数调用；没有批次的既有预留沿用原指纹。 */
    public SerialReservationCommand(String commandId, Long materialId, int quantity, Long ruleId,
                                    String sourceType, Long sourceId) {
        this(commandId, materialId, quantity, ruleId, sourceType, sourceId, null);
    }

    public SerialReservationCommand {
        if (quantity > MAX_QUANTITY) throw new IllegalArgumentException("单次发号数量不得超过10000，工单可按独立命令分批");
        if (commandId == null || commandId.isBlank() || commandId.length() > 128
            || materialId == null || materialId <= 0 || quantity <= 0
            || sourceType == null || sourceType.isBlank() || sourceType.length() > 32
            || (sourceId != null && sourceId <= 0) || (ruleId != null && ruleId <= 0)
            || (batchId != null && batchId <= 0)) {
            throw new IllegalArgumentException("发号请求、来源或批次无效");
        }
    }

    /** 批次进入命令指纹；无批次时兼容已登记的MES命令与确认消息。 */
    public String fingerprint() {
        return batchId == null
            ? TraceHashes.fingerprint(materialId, quantity, ruleId, sourceType, sourceId)
            : TraceHashes.fingerprint(materialId, quantity, ruleId, sourceType, sourceId, batchId);
    }
}
