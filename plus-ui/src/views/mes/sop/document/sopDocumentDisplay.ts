import type { SopVersionVO } from '@/api/mes/sop/version';

/** 仅显示兼容历史类型，不改写已签名主档或未知字典值。 */
export function sopTypeForDisplay(value: string): string {
  return value === 'PROCESS' ? 'PRODUCTION' : value;
}

export function canPreviewPendingDocument(status: string): boolean {
  return status === 'DRAFT' || status === 'IN_REVIEW';
}

/** by-sop 接口已按发布时间、创建时间倒序排列；只取首个 PENDING，不回退到正式或旧文件。 */
export function latestPendingVersion(versions: readonly SopVersionVO[]): SopVersionVO | undefined {
  return versions.find((version) => version.status === 'PENDING');
}
