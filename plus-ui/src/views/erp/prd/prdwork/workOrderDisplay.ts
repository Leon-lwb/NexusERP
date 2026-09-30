/** MRP 工单的 sourceNo 是物料需求单号，不是销售订单编号。 */
export function workOrderSourceNoLabel(sourceType?: string | null): string {
  if (sourceType === 'MATERIAL_REQUIREMENT') return '物料需求单号';
  if (sourceType === 'SALES') return '销售订单编号';
  return '来源单号';
}

/** 保留后台字典配置，只在旧字典缺少 MRP 来源时补充显示项。 */
export function withMaterialRequirementSource(options: DictDataOption[]): DictDataOption[] {
  if (options.some((item) => item.value === 'MATERIAL_REQUIREMENT')) return options;
  return [...options, { value: 'MATERIAL_REQUIREMENT', label: '物料需求', elTagType: 'primary' }];
}

/** decimal(18,4) 数量只接受可安全显示的非负十进制值，未知/不支持的量保留空态。 */
export function normalizeWorkOrderQuantity(value: unknown): number | undefined {
  if (typeof value !== 'number' && typeof value !== 'string') return undefined;
  const decimal = String(value).trim();
  const parts = /^(\d+)(?:\.(\d{1,4}))?$/.exec(decimal);
  if (!parts) return undefined;

  /** 先用十进制整数检查边界，避免浮点乘法误判放大 10000 倍后的安全整数范围。 */
  const scaled = BigInt(parts[1]) * 10000n + BigInt((parts[2] ?? '').padEnd(4, '0'));
  if (scaled > BigInt(Number.MAX_SAFE_INTEGER)) return undefined;

  const quantity = Number(decimal);
  /** 即使 scaled 在安全范围内，number 的四位显示也必须能还原原量，不能静默舍入。 */
  const displayedScaled = BigInt(quantity.toFixed(4).replace('.', ''));
  return displayedScaled === scaled ? quantity : undefined;
}

type WorkOrderQuantityKey = 'plannedQty' | 'completedQty' | 'scrapQty';

/** 只归一化三个工单数量；ID、来源、UOM、版本及其他字段保持原值，不修改原响应。 */
export function normalizeWorkOrderQuantityFields<T extends Partial<Record<WorkOrderQuantityKey, unknown>>>(
  rawData: T
): Omit<T, WorkOrderQuantityKey> & Record<WorkOrderQuantityKey, number | undefined> {
  return {
    ...rawData,
    plannedQty: normalizeWorkOrderQuantity(rawData.plannedQty),
    completedQty: normalizeWorkOrderQuantity(rawData.completedQty),
    scrapQty: normalizeWorkOrderQuantity(rawData.scrapQty)
  };
}
