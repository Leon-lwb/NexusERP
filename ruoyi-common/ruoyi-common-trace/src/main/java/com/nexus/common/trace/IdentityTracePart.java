package com.nexus.common.trace;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.*;

/**
 * 各追溯分节分别声明范围与分页状态，缺失的历史证据保留为明确缺口。
 *
 * @param module 事实所属业务模块。
 * @param identityId ERP 序列号主档主键，作为跨模块稳定实物身份。
 * @param localSnId 制造模块本地单件主键，保留原接口含义。
 * @param canonicalSnCode 库存统一登记的正式单件编码。
 * @param scope 证据范围，区分精确单件、批次与工单可能影响。
 * @param sections 按业务证据范围分开的追溯分节。
 * @param evidenceGaps 明确保留的历史证据缺口。
 * @param localSnCode 模块原编码，仅作为原文保留。
 */
public record IdentityTracePart(
        String module,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long identityId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long localSnId,
        String canonicalSnCode,
        String scope,
        Map<String, TracePage<Map<String, Object>>> sections,
        List<String> evidenceGaps,
        String localSnCode) {
    public IdentityTracePart(
            String module,
            Long identityId,
            Long localSnId,
            String canonicalSnCode,
            String scope,
            Map<String, TracePage<Map<String, Object>>> sections,
            List<String> evidenceGaps) {
        this(module, identityId, localSnId, canonicalSnCode, scope, sections, evidenceGaps, null);
    }

    public IdentityTracePart {
        sections = Map.copyOf(sections);
        evidenceGaps = List.copyOf(evidenceGaps);
    }
}
