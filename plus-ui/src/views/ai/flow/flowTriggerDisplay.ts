import type { AiFlowTriggerType } from '@/api/ai/flow';

/**
 * 流程实例触发类型的共享显示契约。
 * <p>筛选、历史列表和详情共用；value 必须保留服务端原始协议值。</p>
 */
export const FLOW_TRIGGER_OPTIONS = [
  { value: 'MANUAL', label: '手动', detailLabel: '手动触发' },
  { value: 'SCHEDULE', label: '定时', detailLabel: '定时触发' },
  { value: 'TEST', label: '测试', detailLabel: '测试运行' },
  { value: 'WEBHOOK', label: 'Webhook', detailLabel: 'Webhook触发' },
  { value: 'DECISION', label: '工业决策', detailLabel: '工业决策触发' }
] as const satisfies readonly { value: AiFlowTriggerType; label: string; detailLabel: string }[];

/**
 * 获取触发类型显示文案；未知值原样显示，不能误判为手动，空值显示占位符。
 *
 * @param triggerType 服务端返回的原始触发类型
 * @param detailed 是否用于详情页的完整文案
 * @returns 中文友好的显示标签，未知值不改变协议含义
 */
export function flowTriggerLabel(triggerType?: string | null, detailed = false): string {
  const option = FLOW_TRIGGER_OPTIONS.find((item) => item.value === triggerType);
  if (!option) return triggerType || '-';
  return detailed ? option.detailLabel : option.label;
}
