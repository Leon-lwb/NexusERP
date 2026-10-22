package com.nexus.common.trace;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;

/**
 * 已冻结的最终工单交付清单头，内容改变必须产生新修订。
 *
 * @param manifestId 不可变完工清单标识。
 * @param revision 冻结清单修订号，必须与游标及事件一致。
 * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
 * @param mesWoId MES 生产工单主键。
 * @param erpWoId ERP 生产工单主键。
 * @param materialId 当前业务行实际物料标识。
 * @param lineCount 完整清单行数，用于核验分页是否读取完整。
 * @param goodQty 已确认合格的实际产品数量。
 * @param scrapQty 已确认报废的实际产品数量。
 * @param contentHash 按稳定顺序计算的完整内容摘要。
 * @param status 解析或业务处理的明确状态。
 */
public record CompletionManifest(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long manifestId,
        int revision,
        String tenantId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long mesWoId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long erpWoId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long materialId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) long lineCount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal goodQty,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal scrapQty,
        String contentHash,
        String status) {}
