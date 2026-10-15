import type { DatasetView, DatasetVersionView, ReportDsl, ReportStyle, ReportColumn } from '@/types/report';
import { fitColumns } from '../model';
/** 提供报表 `clone` 操作入口。 */
const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value));
/** 处理当前报表的 `indices` 操作。 */
function indices(dsl: ReportDsl, selection: number[]): number[] {
  const result = [...new Set(selection)].sort((a, b) => a - b);
  if (!result.length || result.some((i) => !Number.isInteger(i) || i < 0 || i >= dsl.columns.length)) throw Error('请选择有效的明细列');
  return result;
}
/** 处理当前报表的 `styleColumns` 操作。 */
export function styleColumns(dsl: ReportDsl, selection: number[], style: Partial<ReportStyle>): ReportDsl {
  const result = clone(dsl);
  for (const i of indices(dsl, selection)) result.columns[i].style = { ...result.columns[i].style, ...style };
  return result;
}
/** 处理当前报表的 `copyColumns` 操作。 */
export function copyColumns(dsl: ReportDsl, selection: number[]): string {
  return JSON.stringify({ formatVersion: 1, datasetCode: dsl.datasetCode, columns: indices(dsl, selection).map((i) => dsl.columns[i]) });
}
/** 处理当前报表的 `pasteColumns` 操作。 */
export function pasteColumns(dsl: ReportDsl, text: string): ReportDsl {
  if (text.length > 200000) throw Error('剪贴板内容超出限制');
  const value = JSON.parse(text);
  if (
    value.formatVersion !== 1 ||
    value.datasetCode !== dsl.datasetCode ||
    !Array.isArray(value.columns) ||
    !value.columns.length ||
    dsl.columns.length + value.columns.length > 40
  )
    throw Error('只可粘贴同一数据集的列，且总列数最多40');
  const result = clone(dsl);
  const names = new Set(result.columns.map((c) => c.field));
  for (const source of value.columns) {
    if (
      !source ||
      typeof source.field !== 'string' ||
      !/^[A-Za-z_][A-Za-z0-9_]{0,63}$/.test(source.field) ||
      typeof source.label !== 'string' ||
      !Number.isFinite(source.width) ||
      !source.style
    )
      throw Error('列定义格式无效');
    const column = clone(source) as ReportColumn;
    let name = column.field;
    for (let n = 1; names.has(name); n++) name = column.field.slice(0, 50) + '_copy' + (n === 1 ? '' : n);
    if (name !== column.field) {
      column.expression = column.expression || column.field;
      column.field = name;
    }
    names.add(name);
    result.columns.push(column);
  }
  const before = dsl.columns.length;
  for (const row of [...result.headerRows, ...result.footerRows]) {
    for (const cell of row.cells) if (cell.column === 0 && cell.colSpan === before) cell.colSpan = result.columns.length;
  }
  if (result.kind === 'DOCUMENT') {
    fitColumns(result);
    if (result.columns.some((column) => !Number.isFinite(column.width) || column.width < 20 || column.width > 600))
      throw Error('纸张无法容纳这些列，请增大纸张或减少列数');
  }
  return result;
}
/** 处理当前报表的 `upgradeDatasetVersion` 操作。 */
export function upgradeDatasetVersion(dsl: ReportDsl, target: DatasetVersionView): ReportDsl {
  if (target.code !== dsl.datasetCode || !target.id) throw Error('目标数据契约不匹配');
  const result = clone(dsl);
  result.datasetVersionId = target.id;
  result.parameters = (target.descriptor.parameters || []).map((parameter) => {
    const previous = dsl.parameters.find((candidate) => candidate.name === parameter.name && candidate.type === parameter.type);
    return previous
      ? {
          ...clone(parameter),
          label: previous.label,
          required: parameter.required || previous.required,
          defaultValue: previous.defaultValue === undefined ? parameter.defaultValue : previous.defaultValue
        }
      : clone(parameter);
  });
  return result;
}
/** 处理当前报表的 `boundDataset` 操作。 */
export function boundDataset(datasets: DatasetView[], dsl: ReportDsl | undefined, version: DatasetVersionView | undefined): DatasetView | undefined {
  if (!dsl) return undefined;
  const head = datasets.find((d) => d.dataset.code === dsl.datasetCode);
  if (!head) return undefined;
  if (version?.id === dsl.datasetVersionId && version.code === dsl.datasetCode) return { ...head, descriptor: version.descriptor };
  return head.dataset.publishedVersion === dsl.datasetVersionId ? head : undefined;
}

/** 声明EditorIssue结构，保留服务端精确值和固定版本约束。 */
export interface EditorIssue {
  /** 保存当前下标。 */
  index: number;
  /** 保存当前 `message` 值。 */
  message: string;
}
/** 处理当前报表的 `editorIssues` 操作。 */
export function editorIssues(dsl: ReportDsl, descriptor: import('@/types/report').DatasetDescriptor | undefined): EditorIssue[] {
  if (!descriptor) return [];
  const known = new Set(descriptor.fields.map((f) => f.name));
  const names = new Set<string>();
  const issues: EditorIssue[] = [];
  dsl.columns.forEach((column, index) => {
    if (names.has(column.field)) issues.push({ index, message: '重复列绑定：' + column.field });
    names.add(column.field);
    if (!column.expression && !known.has(column.field)) issues.push({ index, message: '契约中不存在或无权访问字段：' + column.field });
    if (!Number.isFinite(column.width) || column.width < 20) issues.push({ index, message: '列宽至少20pt' });
    if (!Number.isInteger(column.scale) || column.scale < 0 || column.scale > 12) issues.push({ index, message: '小数位范围0–12' });
  });
  return issues;
}
