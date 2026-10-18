import type { ReportDefinition, ReportColumn, TypedValue } from '@/types/report';
import { formatDimension } from './model';

/** 报表展示筛选；目录和资源 ID 始终保留完整字符串。 */
export interface ReportFilters {
  /** 按名称或编码查找的文字。 */
  keyword?: string;
  /** 报表类型，空值代表全部。 */
  kind?: string;
  /** 发布状态，空值代表全部。 */
  status?: string;
  /** 当前目录 ID，空值代表全部目录。 */
  catalogId?: string;
}

/** 状态标签的中文文案和语义色。 */
export interface StatusPresentation {
  /** 向用户展示的中文状态。 */
  label: string;
  /** Element Plus 标签支持的语义类型。 */
  type: 'primary' | 'success' | 'warning' | 'danger' | 'info';
}

/** 资源、导出、批次及交付的通用状态词典。 */
const statuses: Record<string, StatusPresentation> = {
  DRAFT: { label: '草稿', type: 'info' },
  PUBLISHED: { label: '已发布', type: 'success' },
  OFFLINE: { label: '已下线', type: 'warning' },
  ARCHIVED: { label: '已归档', type: 'info' },
  ACTIVE: { label: '已启用', type: 'success' },
  DISABLED: { label: '已停用', type: 'info' },
  QUEUED: { label: '排队中', type: 'info' },
  PENDING: { label: '待处理', type: 'info' },
  RUNNING: { label: '执行中', type: 'primary' },
  SUCCEEDED: { label: '已完成', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  CANCEL_REQUESTED: { label: '取消中', type: 'warning' },
  CANCELLED: { label: '已取消', type: 'info' },
  EXPIRED: { label: '已过期', type: 'warning' },
  READY: { label: '待领取', type: 'success' },
  CLAIMED: { label: '已领取', type: 'success' },
  RETRY: { label: '等待重试', type: 'warning' },
  RETRY_WAIT: { label: '等待重试', type: 'warning' },
  BLOCKED: { label: '已阻止', type: 'danger' },
  DISPATCHED: { label: '已入队', type: 'primary' },
  LEASED: { label: '处理中', type: 'primary' },
  SUPPRESSED: { label: '通知已关闭', type: 'info' },
  SENT: { label: '已通知', type: 'success' },
  SKIPPED: { label: '已跳过', type: 'info' },
  NONE: { label: '未通知', type: 'info' }
};

/** 查找状态展示信息；未知状态保留原值以兼容后续服务扩展。 */
export function statusPresentation(status?: string | null): StatusPresentation {
  return statuses[status || ''] || { label: status || '—', type: 'info' };
}

/** 筛选接口已返回的可见资源，不转换 ID，也不更改原数组。 */
export function filterReports<T extends Pick<ReportDefinition, 'name' | 'code' | 'kind' | 'status' | 'catalogId'>>(
  rows: T[],
  filters: ReportFilters
): T[] {
  const keyword = (filters.keyword || '').trim().toLocaleLowerCase();
  return rows.filter(
    (row) =>
      (!keyword || (row.name + ' ' + row.code).toLocaleLowerCase().includes(keyword)) &&
      (!filters.kind || row.kind === filters.kind) &&
      (!filters.status || row.status === filters.status) &&
      (!filters.catalogId || row.catalogId === filters.catalogId)
  );
}

/** 统计当前可见资源数量；仅用于列表导航，不计算任何业务指标。 */
export function reportCounts(rows: Pick<ReportDefinition, 'status'>[]) {
  return {
    all: rows.length,
    PUBLISHED: rows.filter((row) => row.status === 'PUBLISHED').length,
    DRAFT: rows.filter((row) => row.status === 'DRAFT').length,
    OFFLINE: rows.filter((row) => row.status === 'OFFLINE').length,
    ARCHIVED: rows.filter((row) => row.status === 'ARCHIVED').length
  };
}

/** 按标签、绑定名或类型搜索设计器字段，保留字段的契约顺序。 */
export function filterFields<T extends { name: string; label: string; type: string }>(fields: T[], search: string): T[] {
  const keyword = search.trim().toLocaleLowerCase();
  return fields.filter((field) => !keyword || [field.label, field.name, field.type].some((value) => value.toLocaleLowerCase().includes(keyword)));
}

/**
 * 将服务端汇总分区的字段名替换为业务标签，原样保留精确值。
 * @param group 服务端已计算的汇总维度
 * @param columns 当前已授权报表列，可提供用户自定义标题
 * @returns 可直接展示的分区说明
 */
export function summaryGroupText(group: Record<string, TypedValue>, columns: Pick<ReportColumn, 'field' | 'label'>[] = []): string {
  const labels: Record<string, string> = { currency: '币种', materialCode: '物料编码', unitName: '单位' };
  return Object.entries(group)
    .map(
      ([name, value]) =>
        (columns.find((column) => column.field === name)?.label || (Object.hasOwn(labels, name) ? labels[name] : name)) +
        '=' +
        formatDimension(value, '—')
    )
    .join('，');
}
