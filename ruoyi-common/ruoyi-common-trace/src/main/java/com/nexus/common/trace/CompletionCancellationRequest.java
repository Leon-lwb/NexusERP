package com.nexus.common.trace;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/**
 * 取消冻结清单的来源命令；原始事件必须另经 所属业务库发件箱 实证核验。
 *
 * @param manifestId 不可变完工清单标识。
 * @param revision 冻结清单修订号，必须与游标及事件一致。
 * @param manifestHash 原完工清单的完整摘要。
 * @param originalEvent 本次握手引用的完整原完工事件。
 * @param commandId 首次业务命令号；重试保持原值并核验请求指纹。
 * @param reason 与原任务或原事实关联的明确操作原因。
 * @param actorId 本次业务操作的认证人员标识。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompletionCancellationRequest(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long manifestId,
        int revision,
        String manifestHash,
        MaterialTraceEvent originalEvent,
        String commandId,
        String reason,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long actorId) {
    public static final String EVENT_TYPE = "COMPLETION_MANIFEST_CANCEL_REQUESTED";
    public static final String AGGREGATE_TYPE = "COMPLETION_MANIFEST_CANCELLATION";

    /**
     * 兼容历史四字段事件；新MES命令在原始来源载荷中保留完整操作审计。
     */
    public CompletionCancellationRequest(
            Long manifestId, int revision, String manifestHash, MaterialTraceEvent originalEvent) {
        this(manifestId, revision, manifestHash, originalEvent, null, null, null);
    }

    public CompletionCancellationRequest {
        if (manifestId == null
                || manifestId <= 0
                || revision < 1
                || manifestHash == null
                || !manifestHash.matches("[0-9a-f]{64}")
                || originalEvent == null
                || !"MES".equals(originalEvent.sourceModule())
                || !"COMPLETION_MANIFEST_FROZEN".equals(originalEvent.eventType())
                || !"COMPLETION_MANIFEST".equals(originalEvent.aggregateType())
                || !Objects.equals(manifestId, originalEvent.aggregateId())) {
            throw new IllegalArgumentException("取消必须关联准确的原始 MES 完工清单事件");
        }
        boolean audit = commandId != null || reason != null || actorId != null;
        if (audit
                && (commandId == null
                        || commandId.isBlank()
                        || commandId.length() > 128
                        || reason == null
                        || reason.isBlank()
                        || reason.length() > 500
                        || actorId == null
                        || actorId <= 0)) {
            throw new IllegalArgumentException("取消操作审计必须完整包含稳定命令、原因与真实操作人");
        }
    }
}
