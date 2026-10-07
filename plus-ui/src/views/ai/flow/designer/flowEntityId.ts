/** 实体 ID 只接受未失真的 Long 范围整数；0 保留默认契约。 */
export function normalizeFlowEntityId(value: unknown): string | undefined {
  if (typeof value === 'number') {
    return Number.isSafeInteger(value) && value >= 0 ? String(value) : undefined;
  }
  if (typeof value !== 'string' || !/^(0|[1-9]\d*)$/.test(value)) return undefined;
  const maxLong = '9223372036854775807';
  return value.length < maxLong.length || (value.length === maxLong.length && value <= maxLong) ? value : undefined;
}
