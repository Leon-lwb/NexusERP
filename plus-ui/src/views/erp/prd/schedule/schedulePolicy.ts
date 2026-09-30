import type { GanttBar, GanttRow, SchedulePlanVO } from '@/api/erp/prd/schedule/types';

type PlanVersion = Pick<SchedulePlanVO, 'capacityCalcVersion'>;

export function isLegacySchedulePlan(plan: PlanVersion | null | undefined): boolean {
  return plan?.capacityCalcVersion == null || plan.capacityCalcVersion === 0;
}

export function scheduleCapacityLabel(plan: PlanVersion): string {
  if (isLegacySchedulePlan(plan)) return '历史口径，非新版可信产能';
  return plan.capacityCalcVersion === 2 ? '排程版本 2' : '版本未识别，非新版可信产能';
}

export function isPublishableSchedulePlan(plan: Pick<SchedulePlanVO, 'status' | 'capacityCalcVersion'> | null | undefined): boolean {
  return plan?.status === 'COMPLETED' && plan.capacityCalcVersion === 2;
}

export function isDecisionReadySchedulePlan(plan: Pick<SchedulePlanVO, 'status' | 'capacityCalcVersion'> | null | undefined): boolean {
  return plan?.status === 'PUBLISHED' && plan.capacityCalcVersion === 2;
}

const htmlEntities: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

function escapeTooltipHtml(value: string): string {
  return value.replace(/[&<>"']/g, (character) => htmlEntities[character]);
}

export function formatScheduleGanttTooltip(
  row: Pick<GanttRow, 'woNo' | 'materialName'> | undefined,
  bar: Pick<GanttBar, 'processName' | 'startTime' | 'endTime'> | undefined
): string {
  const display = (value?: string) => escapeTooltipHtml(value || '-');
  return `<b>${display(row?.woNo)}</b><br/>物料: ${display(row?.materialName)}<br/>工序: ${display(bar?.processName)}<br/>起: ${display(bar?.startTime)}<br/>止: ${display(bar?.endTime)}`;
}
