/**
 * 生成用于导出、订阅等操作的幂等请求键。
 * 局域网HTTP可能没有randomUUID，仍使用getRandomValues生成标准UUIDv4。
 * @returns 不包含业务数据的安全随机UUID
 * @throws 安全随机能力缺失时明确拒绝，避免退化为可预测的标识
 */
export function createRequestKey(): string {
  const source = globalThis.crypto;
  if (typeof source?.randomUUID === 'function') return source.randomUUID();
  if (typeof source?.getRandomValues !== 'function') throw new Error('当前浏览器不支持安全随机数，无法创建请求标识');
  const bytes = source.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('');
  return hex.slice(0, 8) + '-' + hex.slice(8, 12) + '-' + hex.slice(12, 16) + '-' + hex.slice(16, 20) + '-' + hex.slice(20);
}
