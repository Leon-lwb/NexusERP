/** 只展示有限且非负的真实数值；未知哨兵和非法类型不能伪造为零。 */
export function formatToolDuration(duration: unknown): string {
  return typeof duration === 'number' && Number.isFinite(duration) && duration >= 0 ? `${duration}ms` : '耗时未知';
}
