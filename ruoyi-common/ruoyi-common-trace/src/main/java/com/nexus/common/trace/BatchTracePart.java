package com.nexus.common.trace;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.*;

/**
 * 批次表达数量范围；单件关系必须来自明确的实物身份与实际分配证据。
 *
 * @param module 事实所属业务模块。
 * @param batchId 库存批次身份；与制造子批次标识分别保存。
 * @param scope 证据范围，区分精确单件、批次与工单可能影响。
 * @param sections 按业务证据范围分开的追溯分节。
 * @param evidenceGaps 明确保留的历史证据缺口。
 */
public record BatchTracePart(
        String module,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long batchId,
        String scope,
        Map<String, TracePage<Map<String, Object>>> sections,
        List<String> evidenceGaps) {
    public BatchTracePart {
        sections = Map.copyOf(sections);
        evidenceGaps = List.copyOf(evidenceGaps);
    }
}
