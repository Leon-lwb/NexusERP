import type { AiFlowTriggerType } from '@/api/ai/flow';

/** 实例触发字典：筛选、历史列表和详情共用，保持服务端原始触发值。 */
export const FLOW_TRIGGER_OPTIONS = [
  { value: 'MANUAL', label: '手动', detailLabel: '手动触发' },
  { value: 'SCHEDULE', label: '定时', detailLabel: '定时触发' },
  { value: 'TEST', label: '测试', detailLabel: '测试运行' },
  { value: 'WEBHOOK', label: 'Webhook', detailLabel: 'Webhook触发' },
  { value: 'DECISION', label: '工业决策', detailLabel: '工业决策触发' }
] as const satisfies readonly { value: AiFlowTriggerType; label: string; detailLabel: string }[];

/** 未知类型展示原值，不把新类型误判为手动；空值显示占位符。 */
export function flowTriggerLabel(triggerType?: string | null, detailed = false): string {
  const option = FLOW_TRIGGER_OPTIONS.find((item) => item.value === triggerType);
  if (!option) return triggerType || '-';
  return detailed ? option.detailLabel : option.label;
}
