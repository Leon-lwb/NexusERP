import type {
  ReportDsl,
  ReportParameter,
  DatasetDescriptor,
  DatasetField,
  ReportStyle,
  TypedValue,
  ReportSummary,
  ExportFormat
} from '@/types/report';
import { formatReportDateTime } from './time';
/** 返回原生报表默认文字样式。 */
export const normalStyle = (): ReportStyle => ({ fontSize: 10, bold: false, align: 'LEFT', color: '#1f2937', background: '#ffffff' });
/** 格式化精确值，十进制舍入使用整数文本而不转换为浮点数。 */
export function formatTyped(value: TypedValue | null | undefined, scale = 2): string {
  if (!value || value.value === null) return '';
  if (value.type === 'STRING' && value.displayValue?.trim()) return value.displayValue;
  if (value.type === 'DATETIME') return formatReportDateTime(value.value);
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
/** 维度仅转换名称和时间显示，保留数字原始精度及各场景的空值标记。 */
export function formatDimension(value: TypedValue, empty = '（空）'): string {
  if (value.value === null) return empty;
  return ['STRING', 'DATE', 'DATETIME'].includes(value.type) ? formatTyped(value) : value.value;
}
/** 按已授权契约创建原生草稿并复制声明参数。 */
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
/** 按相对宽度适配纸张可用区域。 */
export function fitColumns(dsl: ReportDsl): void {
  const width = (dsl.print?.orientation === 'LANDSCAPE' ? dsl.page.height : dsl.page.width) - dsl.page.marginLeft - dsl.page.marginRight;
  const total = dsl.columns.reduce((sum, c) => sum + c.width, 0);
  dsl.columns.forEach((c) => {
    c.width = (width * c.width) / total;
  });
}
/** 复制已授权定义并清除业务标识默认值。 */
function copy(dsl: ReportDsl): ReportDsl {
  return JSON.parse(JSON.stringify(dsl));
}
/** 不可变添加可信字段并维护整行固定区域覆盖。 */
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
/** 不可变删除明细列并保持至少一列及合并一致。 */
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
/** 重排明细列并检查有效下标。 */
export function moveColumn(dsl: ReportDsl, from: number, to: number): ReportDsl {
  if (from < 0 || to < 0 || from >= dsl.columns.length || to >= dsl.columns.length) throw new Error('列位置无效');
  const next = copy(dsl);
  const [column] = next.columns.splice(from, 1);
  next.columns.splice(to, 0, column);
  return next;
}
/** 仅保留非空字符串参数，默认值由服务器按契约解析。 */
export function parameterInput(input: Record<string, string>): Record<string, string> {
  return Object.fromEntries(Object.entries(input).filter(([, value]) => value !== '' && value !== undefined));
}
/** 处理当前报表的 `summaryFor` 操作。 */
export function summaryFor(field: DatasetField): ReportSummary {
  if (!field.aggregatable || !['DECIMAL', 'INTEGER'].includes(field.type)) throw new Error('请选择允许汇总的数值字段');
  return { field: field.name, function: 'SUM', groupBy: [...field.aggregationKeys], label: field.label + '合计', scale: 4 };
}
/** 处理当前报表的 `requiresSnapshot` 操作。 */
export function requiresSnapshot(dsl: ReportDsl): boolean {
  if (dsl.kind !== 'TABLE' || dsl.summaries.some((s) => s.function !== 'COUNT_ROWS' || s.groupBy.length > 0)) return true;
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
/** 按运行模式和行数决定允许的导出格式。 */
export function exportFormats(mode: string, rows: number): ExportFormat[] {
  return rows > 5000 ? ['XLSX_DATA'] : ['PDF', 'XLSX_DATA', 'XLSX_LAYOUT'];
}
/**
 * 保存当前页面或测试的keys状态。
 */
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
  'repeatHeader',
  'pivot',
  'print'
]);
/** 序列化自有定义并拒绝混入编辑器状态。 */
export function serializeDsl(dsl: ReportDsl): string {
  if (Object.keys(dsl).some((key) => !keys.has(key))) throw new Error('编辑状态不得进入报表定义');
  return JSON.stringify(dsl);
}
/** 仅用于编辑器的历史快照，不写入持久化报表定义。 */
export class DslHistory {
  /** 保存当前 `states` 值。 */
  private states: string[] = [];
  /** 保存当前 `cursor` 值。 */
  private cursor = -1;
  constructor(private readonly limit = 50) {}
  get canUndo(): boolean {
    return this.cursor > 0;
  }
  get canRedo(): boolean {
    return this.cursor + 1 < this.states.length;
  }
  /** 处理当前报表的 `reset` 操作。 */
  reset(dsl?: ReportDsl): void {
    this.states = dsl ? [serializeDsl(dsl)] : [];
    this.cursor = this.states.length - 1;
  }
  /** 记录有界阶段和结果指标。 */
  record(dsl: ReportDsl): void {
    const snapshot = serializeDsl(dsl);
    if (this.states[this.cursor] === snapshot) return;
    this.states = this.states.slice(0, this.cursor + 1);
    this.states.push(snapshot);
    if (this.states.length > this.limit) this.states.shift();
    this.cursor = this.states.length - 1;
  }
  /** 处理当前报表的 `undo` 操作。 */
  undo(): ReportDsl | undefined {
    if (!this.canUndo) return;
    return JSON.parse(this.states[--this.cursor]);
  }
  /** 处理当前报表的 `redo` 操作。 */
  redo(): ReportDsl | undefined {
    if (!this.canRedo) return;
    return JSON.parse(this.states[++this.cursor]);
  }
}
/** 声明请求Gate结构，保留服务端精确值和固定版本约束。 */
export class RequestGate {
  /** 保存当前 `serial` 值。 */
  private serial = 0;
  /** 处理当前报表的 `begin` 操作。 */
  begin(): number {
    return ++this.serial;
  }
  /** 处理当前报表的 `current` 操作。 */
  current(serial: number): boolean {
    return serial === this.serial;
  }
  /** 处理当前报表的 `invalidate` 操作。 */
  invalidate(): void {
    this.serial += 1;
  }
}
/** 处理当前报表的 `jobError` 操作。 */
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

/**
 * 合并固定数据集和模板的默认提示，显式模板默认值优先，保留原定义不变。
 * @param parameters 当前报表声明的参数
 * @param datasetParameters 当前固定数据集契约参数
 * @returns 带有效默认标记的独立参数列表，不计算具体日期或业务值
 */
export function effectiveReportParameters(
  parameters: ReportParameter[],
  datasetParameters: Pick<ReportParameter, 'name' | 'defaultValue'>[] = []
): ReportParameter[] {
  return parameters.map((parameter) => ({
    ...parameter,
    defaultValue: parameter.defaultValue?.trim()
      ? parameter.defaultValue
      : (datasetParameters.find((item) => item.name === parameter.name)?.defaultValue ?? null)
  }));
}
/**
 * 在调用接口前提示缺少的必填条件，可选项留空和可信默认值保持原有行为。
 * @param parameters 当前固定报表或数据集声明的参数
 * @param input 用户填写的原始字符串，标识符不转为Number
 * @param datasetParameters 固定数据集的默认值，可用于继承默认标记
 * @returns 缺失条件的中文提示，全部满足时为空字符串
 */
export function requiredParameterMessage(
  parameters: ReportParameter[],
  input: Record<string, string>,
  datasetParameters: Pick<ReportParameter, 'name' | 'defaultValue'>[] = []
): string {
  const missing = effectiveReportParameters(parameters, datasetParameters).filter(
    (parameter) => parameter.required && !input[parameter.name]?.trim() && !parameter.defaultValue?.trim()
  );
  return missing.length ? '请先填写必填条件：' + missing.map((parameter) => parameter.label || parameter.name).join('、') : '';
}
