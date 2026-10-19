package com.nexus.common.trace;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 组件快照与产品完工清单在同一制造事务中冻结，保持交付内容一致。
 *
 * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
 * @param manifestId 不可变完工清单标识。
 * @param revision 冻结清单修订号，必须与游标及事件一致。
 * @param lineCount 完整清单行数，用于核验分页是否读取完整。
 * @param contentHash 按稳定顺序计算的完整内容摘要。
 */
public record CompletionAllocationManifest(
        String tenantId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long manifestId,
        int revision,
        @JsonFormat(shape = JsonFormat.Shape.STRING) long lineCount,
        String contentHash) {}
