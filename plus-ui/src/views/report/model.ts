import type { ReportDsl, DatasetDescriptor, DatasetField, ReportStyle, TypedValue, ReportSummary, ExportFormat } from '@/types/report';
export const normalStyle = (): ReportStyle => ({ fontSize: 10, bold: false, align: 'LEFT', color: '#1f2937', background: '#ffffff' });
export function formatTyped(value: TypedValue | null | undefined, scale = 2): string {
  if (!value || value.value === null) return '';
  if (value.type === 'BOOLEAN') return value.value === 'true' ? '是' : '否';
  if (value.type !== 'DECIMAL') return value.value;
  if (!/^[-+]?\d+(?:\.\d+)?$/.test(value.value) || scale < 0 || scale > 12) return value.value;
  const negative = value.value.startsWith('-');
  const [whole, fraction = ''] = value.value.replace(/^[-+]/, '').split('.');
  const digits = fraction.padEnd(scale + 1, '0');
  let integer = BigInt(whole + digits.slice(0, scale));
  if (digits[scale] >= '5') integer += 1n;
  const result = integer.toString().padStart(scale + 1, '0');
  return (negative && integer !== 0n ? '-' : '') + (scale ? result.slice(0, -scale) + '.' + result.slice(-scale) : result);
}
export function createDsl(descriptor: DatasetDescriptor, datasetVersionId: string, code: string): ReportDsl {
  const names = descriptor.defaultColumns.length ? descriptor.defaultColumns : descriptor.fields.slice(0, 5).map((f) => f.name);
  const columns = names.map((name) => {
    const field = descriptor.fields.find((f) => f.name === name)!;
    return { field: name, label: field.label, width: 120, scale: field.type === 'DECIMAL' ? 4 : 0, expression: null, style: normalStyle() };
  });
  const dsl: ReportDsl = {
    schemaVersion: 1,
    code,
    title: descriptor.name,
    kind: descriptor.document ? 'DOCUMENT' : 'TABLE',
    datasetCode: descriptor.code,
    datasetVersionId,
    columns,
    parameters: descriptor.parameters.map((p) => ({ ...p })),
    page: { width: 595.28, height: 841.89, marginTop: 36, marginRight: 36, marginBottom: 36, marginLeft: 36, rowHeight: 24 },
    headerRows: [],
    footerRows: [],
    summaries: [{ field: null, function: 'COUNT_ROWS', groupBy: [], label: '记录条数', scale: 0 }],
    repeatHeader: true
  };
  if (dsl.kind === 'DOCUMENT') {
    dsl.headerRows = [
      { height: 32, cells: [{ column: 0, colSpan: columns.length, rowSpan: 1, text: '记录数：{{row_count}}', field: null, style: normalStyle() }] }
    ];
    fitColumns(dsl);
  }
  return dsl;
}
export function fitColumns(dsl: ReportDsl): void {
  const width = dsl.page.width - dsl.page.marginLeft - dsl.page.marginRight;
  const total = dsl.columns.reduce((sum, c) => sum + c.width, 0);
  dsl.columns.forEach((c) => {
    c.width = (width * c.width) / total;
  });
}
function copy(dsl: ReportDsl): ReportDsl {
  return JSON.parse(JSON.stringify(dsl));
}
export function addField(dsl: ReportDsl, field: DatasetField): ReportDsl {
  if (dsl.columns.length >= 40 || dsl.columns.some((c) => c.field === field.name)) throw new Error('该字段已添加或已达到 40 列上限');
  const next = copy(dsl);
  const before = next.columns.length;
  next.columns.push({
    field: field.name,
    label: field.label,
    width: 100,
    scale: field.type === 'DECIMAL' ? 4 : 0,
    expression: null,
    style: normalStyle()
  });
  [...next.headerRows, ...next.footerRows].forEach((row) =>
    row.cells.forEach((c) => {
      if (c.column === 0 && c.colSpan === before) c.colSpan += 1;
    })
  );
  if (next.kind === 'DOCUMENT') fitColumns(next);
  return next;
}
export function removeColumn(dsl: ReportDsl, index: number): ReportDsl {
  if (dsl.columns.length <= 1 || index < 0 || index >= dsl.columns.length) throw new Error('至少保留一列明细');
  const next = copy(dsl);
  next.columns.splice(index, 1);
  [...next.headerRows, ...next.footerRows].forEach((row) => {
    row.cells = row.cells.filter((c) => !(c.column === index && c.colSpan === 1));
    row.cells.forEach((c) => {
      if (c.column > index) c.column -= 1;
      else if (c.column + c.colSpan > index) c.colSpan -= 1;
    });
  });
  if (next.kind === 'DOCUMENT') fitColumns(next);
  return next;
}
export function moveColumn(dsl: ReportDsl, from: number, to: number): ReportDsl {
  if (from < 0 || to < 0 || from >= dsl.columns.length || to >= dsl.columns.length) throw new Error('列位置无效');
  const next = copy(dsl);
  const [column] = next.columns.splice(from, 1);
  next.columns.splice(to, 0, column);
  return next;
}
export function parameterInput(input: Record<string, string>): Record<string, string> {
  return Object.fromEntries(Object.entries(input).filter(([, value]) => value !== '' && value !== undefined));
}
export function summaryFor(field: DatasetField): ReportSummary {
  if (!field.aggregatable || !['DECIMAL', 'INTEGER'].includes(field.type)) throw new Error('请选择允许汇总的数值字段');
  return { field: field.name, function: 'SUM', groupBy: [...field.aggregationKeys], label: field.label + '合计', scale: 4 };
}
export function requiresSnapshot(dsl: ReportDsl): boolean {
  if (dsl.kind === 'DOCUMENT' || dsl.summaries.some((s) => s.function !== 'COUNT_ROWS' || s.groupBy.length > 0)) return true;
  return dsl.columns.some((column) => {
    const expression = column.expression || '';
    for (const match of expression.matchAll(/\b(SUM|COUNT|MIN|MAX)\s*\(/gi)) {
      if (['SUM', 'COUNT'].includes(match[1].toUpperCase())) return true;
      let depth = 1;
      let scalar = false;
      for (let i = match.index! + match[0].length; i < expression.length && depth > 0; i++) {
        if (expression[i] === '(') depth++;
        else if (expression[i] === ')') depth--;
        else if (expression[i] === ',' && depth === 1) scalar = true;
      }
      if (depth === 0 && !scalar) return true;
    }
    return false;
  });
}
export function exportFormats(mode: string, rows: number): ExportFormat[] {
  return rows > 5000 ? ['XLSX_DATA'] : ['PDF', 'XLSX_DATA', 'XLSX_LAYOUT'];
}
const keys = new Set([
  'schemaVersion',
  'code',
  'title',
  'kind',
  'datasetCode',
  'datasetVersionId',
  'columns',
  'parameters',
  'page',
  'headerRows',
  'footerRows',
  'summaries',
  'repeatHeader'
]);
export function serializeDsl(dsl: ReportDsl): string {
  if (Object.keys(dsl).some((key) => !keys.has(key))) throw new Error('编辑状态不得进入报表定义');
  return JSON.stringify(dsl);
}
/** Editor-only snapshots; these states are never part of the persisted DSL. */
export class DslHistory {
  private states: string[] = [];
  private cursor = -1;
  constructor(private readonly limit = 50) {}
  get canUndo(): boolean {
    return this.cursor > 0;
  }
  get canRedo(): boolean {
    return this.cursor + 1 < this.states.length;
  }
  reset(dsl?: ReportDsl): void {
    this.states = dsl ? [serializeDsl(dsl)] : [];
    this.cursor = this.states.length - 1;
  }
  record(dsl: ReportDsl): void {
    const snapshot = serializeDsl(dsl);
    if (this.states[this.cursor] === snapshot) return;
    this.states = this.states.slice(0, this.cursor + 1);
    this.states.push(snapshot);
    if (this.states.length > this.limit) this.states.shift();
    this.cursor = this.states.length - 1;
  }
  undo(): ReportDsl | undefined {
    if (!this.canUndo) return;
    return JSON.parse(this.states[--this.cursor]);
  }
  redo(): ReportDsl | undefined {
    if (!this.canRedo) return;
    return JSON.parse(this.states[++this.cursor]);
  }
}
export class RequestGate {
  private serial = 0;
  begin(): number {
    return ++this.serial;
  }
  current(serial: number): boolean {
    return serial === this.serial;
  }
  invalidate(): void {
    this.serial += 1;
  }
}
export function jobError(code: string | null): string {
  const messages: Record<string, string> = {
    SCOPE_CHANGED: '权限范围已变化，请重新运行',
    SCOPE_DENIED: '当前无权访问报表或业务数据',
    FONT_UNAVAILABLE: '服务端中文字体未配置',
    CAPACITY_EXCEEDED: '数据超出容量，请缩小筛选范围',
    TIMEOUT: '执行超时，请缩小筛选范围',
    SNAPSHOT_EXPIRED: '快照已过期，请重新运行',
    REPORT_OFFLINE: '报表已下线',
    STORAGE_NODE_MISMATCH: '请在原存储节点访问',
    RETRY_EXHAUSTED: '任务重试次数已耗尽'
  };
  return code ? messages[code] || '导出失败，请重新运行或联系管理员' : '';
}
