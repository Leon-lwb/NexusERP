import type { ProductionIssueLineForm, AvailableStockVO } from '@/api/erp/wm/productionissue/types';

/** 仅接受有限十进制数值或十进制字符串，空白及其他类型不代表可靠零。 */
export function readDecimalNumber(value: unknown): number | null {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null;
  if (typeof value !== 'string' || !/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$/.test(value)) return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

/** 比较及上限使用归一数值，未知、负量及超 ATP 均不可执行。 */
export function quantityWithinAtp(current: unknown, available: unknown): boolean {
  const quantity = readDecimalNumber(current);
  const atp = readDecimalNumber(available);
  return quantity != null && quantity > 0 && atp != null && atp >= 0 && quantity <= atp;
}

/** 表单边界转换十进制输入，数量缺失保持未知，不推断参考零金额。 */
export function normalizeIssueLine(line: ProductionIssueLineForm) {
  const result = { ...line } as ProductionIssueLineForm & { availableQty?: number | null };
  for (const field of ['quantity', 'bomQty', 'lossRate', 'requiredQty', 'issuedQty', 'currentQty', 'shortQty', 'unitPrice', 'amount'] as const) {
    result[field] = readDecimalNumber(line[field]);
  }
  result.availableQty = readDecimalNumber((line as { availableQty?: unknown }).availableQty);
  if (result.shortQty == null && result.requiredQty != null && result.issuedQty != null) {
    result.shortQty = Number(result.requiredQty) - Number(result.issuedQty);
  }
  return result;
}

/** 候选只承接已知数量并明确物料仓库身份；不使用账面量冒充可用量。 */
export function normalizeStockCandidate(stock: AvailableStockVO) {
  return {
    ...stock,
    stockQuantity: readDecimalNumber(stock.stockQuantity),
    reservedQuantity: readDecimalNumber(stock.reservedQuantity),
    availableQuantity: readDecimalNumber(stock.availableQuantity)
  };
}

/** 本次成功查询的库存身份快照，防止选择前物料或仓库发生漂移。 */
export function stockCandidateIdentity(stock: AvailableStockVO): string {
  return [stock.stockId, stock.materialId, stock.warehouseId, stock.batchId ?? '', stock.locationId ?? ''].map(String).join('/');
}
