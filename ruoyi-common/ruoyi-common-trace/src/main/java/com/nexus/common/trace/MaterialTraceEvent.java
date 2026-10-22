package com.nexus.common.trace;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Set;

/**
 * 同源业务事务内冻结的事件；版本在来源模块和业务聚合范围内递增。
 *
 * @param eventId 跨模块事件的稳定标识。
 * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
 * @param sourceModule 事件来源所属模块。
 * @param eventType 由协议固定的业务事件类型。
 * @param aggregateType 来源模块内固定的业务聚合类型。
 * @param aggregateId 来源业务聚合的稳定主键。
 * @param aggregateVersion 该来源聚合内递增的事实版本。
 * @param schemaVersion 事件协议版本。
 * @param payload 冻结的事件载荷，后续重放不得重新拼装。
 * @param contentHash 按稳定顺序计算的完整内容摘要。
 */
public record MaterialTraceEvent(
        String eventId,
        String tenantId,
        String sourceModule,
        String eventType,
        String aggregateType,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long aggregateId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) long aggregateVersion,
        int schemaVersion,
        String payload,
        String contentHash) {
    public MaterialTraceEvent {
        if (eventId == null
                || eventId.isBlank()
                || eventId.length() > 64
                || tenantId == null
                || tenantId.isBlank()
                || tenantId.length() > 20
                || !Set.of("ERP", "MES").contains(sourceModule)
                || eventType == null
                || eventType.isBlank()
                || eventType.length() > 64
                || aggregateType == null
                || aggregateType.isBlank()
                || aggregateType.length() > 64
                || aggregateId == null
                || aggregateId <= 0
                || aggregateVersion < 1
                || schemaVersion != 1
                || payload == null
                || !TraceHashes.sha256(payload).equals(contentHash)) {
            throw new IllegalArgumentException("追溯事件身份、版本或内容摘要无效");
        }
    }
}
