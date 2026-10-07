import type { ProductionIssueLineForm } from '@/api/erp/wm/productionissue/types';

import { readDecimalNumber } from './quantity';

/** 参考金额优先保留已知明细金额，否则按本次正量或累计已领量乘参考单价。 */
export function referenceLineAmount(line: ProductionIssueLineForm): number | null {
  const amount = readDecimalNumber(line.amount);
  if (amount != null) return amount;
  const price = readDecimalNumber(line.unitPrice);
  const current = readDecimalNumber(line.currentQty);
  const quantity = current != null && current > 0 ? current : readDecimalNumber(line.issuedQty);
  if (price == null || quantity == null) return null;
  const calculated = quantity * price;
  return Number.isFinite(calculated) ? Number(calculated.toFixed(4)) : null;
}

/** 任一明细未计价时总额未知，禁止把部分已知金额冒充完整参考总额。 */
export function referenceTotalAmount(lines: ProductionIssueLineForm[]): number | null {
  if (!lines.length) return null;
  let total = 0;
  for (const line of lines) {
    const amount = referenceLineAmount(line);
    if (amount == null) return null;
    total += amount;
  }
  return Number.isFinite(total) ? Number(total.toFixed(4)) : null;
}

/** 已知零金额照常显示零，未知参考金额明确显示未计价。 */
export function formatReferenceAmount(value: unknown, referenceAmountKnown?: boolean): string {
  const amount = readDecimalNumber(value);
  return referenceAmountKnown !== true || amount == null ? '未计价' : amount.toFixed(4);
}

/** 明确返回完整参考总額是否已知，空明细或任一缺失均为假。 */
export function referenceAmountState(lines: ProductionIssueLineForm[]) {
  const amount = referenceTotalAmount(lines);
  return { amount, referenceAmountKnown: amount != null };
}
