/** 备件四菜单共用的四位小数显示与输入边界；仅处理数量/金额，不处理业务 ID。 */
export const MAX_SPARE_DECIMAL = 9999999999.9999;

const quantityFormatter = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 4 });
const amountFormatter = new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 4 });

/** null、空串与非有限值都是未知，不能通过 Number(null/空串) 伪装成零。 */
export const decimalValue = (value: unknown): number | undefined => {
  if (typeof value !== 'number' && typeof value !== 'string') return undefined;
  if (typeof value === 'string' && !value.trim()) return undefined;
  const result = Number(value);
  return Number.isFinite(result) ? result : undefined;
};

export const formatQuantity = (value: unknown): string => {
  const quantity = decimalValue(value);
  return quantity === undefined ? '-' : quantityFormatter.format(quantity);
};

/** 参考成本不是供应商报价；两位起显示，保留有效的第三/第四位。 */
export const formatAmount = (value: unknown): string => {
  const amount = decimalValue(value);
  if (amount === undefined) return '-';
  const parts = /^(-?)(\d+)(?:\.(\d+))?$/.exec(String(value).trim());
  if (!parts) return amountFormatter.format(amount);
  // 成本 decimal(18,4) 可能超过 JS 的精确小数范围，按字符串/BigInt 格式化，不先转 Number。
  const fraction = parts[3] || '';
  let scaled = BigInt(parts[2]) * 10000n + BigInt(fraction.padEnd(4, '0').slice(0, 4));
  if (fraction.length > 4 && fraction[4] >= '5') scaled += 1n;
  const integer = quantityFormatter.format(scaled / 10000n);
  const decimals = (scaled % 10000n).toString().padStart(4, '0').replace(/0+$/, '').padEnd(2, '0');
  return `${parts[1] && scaled > 0n ? '-' : ''}${integer}.${decimals}`;
};

/** 成本对齐 decimal(18,4)，用字符位数校验，保留十四位整数和四位小数。 */
export const isNonNegativeCost = (value: unknown): boolean => {
  if (typeof value !== 'number' && typeof value !== 'string') return false;
  const parts = /^(\d+)(?:\.(\d{1,4}))?$/.exec(String(value).trim());
  return !!parts && parts[1].replace(/^0+(?=\d)/, '').length <= 14;
};
export const costInputValue = (value: unknown): string | undefined => (isNonNegativeCost(value) ? String(value).trim() : undefined);

/** 对齐 decimal(14,4)，前端校验仅改善交互，后端仍为权威。 */
export const isNonNegativeDecimal = (value: unknown): boolean => {
  const quantity = decimalValue(value);
  return quantity !== undefined && quantity >= 0 && quantity <= MAX_SPARE_DECIMAL && /^\d+(?:\.\d{1,4})?$/.test(String(quantity));
};
export const isPositiveQuantity = (value: unknown): boolean => isNonNegativeDecimal(value) && decimalValue(value)! > 0;

/** 仅 ERP_AVAILABLE 的已知数值可作 ERP 口径展示/预校验，绝不退回 MES 缓存。 */
export const erpStockValue = (row: { lowStockBasis?: string; erpAvailableStock?: unknown }): number | undefined =>
  row.lowStockBasis === 'ERP_AVAILABLE' ? decimalValue(row.erpAvailableStock) : undefined;

const BASIS_LABELS: Record<string, string> = {
  ERP_AVAILABLE: 'ERP 可用量（权威口径）',
  NOT_MAPPED: '未映射 ERP 物料，库存未知',
  ERP_UNAVAILABLE: 'ERP 不可用，库存未知'
};
export const stockBasisLabel = (basis?: string): string => (basis ? BASIS_LABELS[basis] || `未知库存口径（${basis}）` : '库存口径未知');

export const SPARE_TXN_OPTIONS = [
  { value: 'IN', label: '入库' },
  { value: 'OUT', label: '出库' },
  { value: 'REPAIR_USE', label: '维修领用' },
  { value: 'RETURN', label: '退库' }
];
export const SPARE_SYNC_STATUS_OPTIONS = [
  { value: 'PENDING', label: '待投递' },
  { value: 'SENT', label: '已投递，待确认' },
  { value: 'SYNCED', label: '已确认 ERP 落账' },
  { value: 'FAILED', label: '同步失败' }
];
const TXN_LABELS = Object.fromEntries(SPARE_TXN_OPTIONS.map((option) => [option.value, option.label]));
const SYNC_LABELS = Object.fromEntries(SPARE_SYNC_STATUS_OPTIONS.map((option) => [option.value, option.label]));
const REF_LABELS: Record<string, string> = {
  MANUAL: '手工操作',
  REPAIR: '维修单',
  REPAIR_CANCEL: '维修领用撤销',
  SPARE_RETURN: '退库单'
};
const CATEGORY_LABELS: Record<string, string> = {
  ELECTRICAL: '电气元件',
  MECHANICAL: '机械元件',
  HYDRAULIC: '液压气动',
  CONSUMABLE: '耗材',
  OTHER: '其他'
};
const label = (labels: Record<string, string>, value?: string): string => (value ? labels[value] || `未知（${value}）` : '-');
export const txnTypeLabel = (value?: string): string => label(TXN_LABELS, value);
export const refTypeLabel = (value?: string): string => label(REF_LABELS, value);
export const syncStatusLabel = (value?: string): string => label(SYNC_LABELS, value);
export const categoryLabel = (value?: string): string => label(CATEGORY_LABELS, value);
export const txnTagType = (type?: string): 'success' | 'warning' | 'danger' | 'info' => {
  if (type === 'IN' || type === 'RETURN') return 'success';
  if (type === 'OUT') return 'warning';
  if (type === 'REPAIR_USE') return 'danger';
  return 'info';
};
export const syncTagType = (status?: string): 'success' | 'danger' | 'primary' | 'warning' | 'info' => {
  if (status === 'SYNCED') return 'success';
  if (status === 'FAILED') return 'danger';
  if (status === 'SENT') return 'primary';
  if (status === 'PENDING') return 'warning';
  return 'info';
};
