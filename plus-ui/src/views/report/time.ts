/** 报表业务时间固定使用上海时区，避免浏览器所在时区改变预览和打印口径。 */
const businessTime = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23'
});

/** 将确定的时间点格式化到秒，不改变原始时间值。 */
function formatInstant(value: Date): string | null {
  if (!Number.isFinite(value.getTime())) return null;
  const parts = Object.fromEntries(businessTime.formatToParts(value).map((part) => [part.type, part.value]));
  return `${parts.year.padStart(4, '0')}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`;
}

/** 展示报表元数据与DATETIME；无偏移量的历史时间沿用业务本地时间，日期不跨天。 */
export function formatReportDateTime(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—';
  if (value instanceof Date) return formatInstant(value) ?? '—';
  if (typeof value === 'number' || (typeof value === 'string' && /^-?(?:\d{10}|\d{13})$/.test(value))) {
    const timestamp = Number(value);
    const milliseconds = Math.abs(timestamp) >= 1e9 && Math.abs(timestamp) < 1e10 ? timestamp * 1000 : timestamp;
    return formatInstant(new Date(milliseconds)) ?? String(value);
  }
  if (typeof value !== 'string') return '—';
  const match = /^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,9}))?)?(Z|[+-]\d{2}:\d{2})?)?$/.exec(value);
  if (!match) return value;
  const [, year, month, day, hour, minute, second = '00', fraction, offset] = match;
  const numericYear = Number(year);
  const days = [31, numericYear % 4 === 0 && (numericYear % 100 !== 0 || numericYear % 400 === 0) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  if (Number(month) < 1 || Number(month) > 12 || Number(day) < 1 || Number(day) > days[Number(month) - 1]) return value;
  const date = `${year}-${month}-${day}`;
  if (hour === undefined) return date;
  if (Number(hour) > 23 || Number(minute) > 59 || Number(second) > 59) return value;
  const datetime = `${date} ${hour}:${minute}:${second}`;
  if (!offset) return datetime;
  if (offset !== 'Z') {
    const offsetHours = Number(offset.slice(1, 3));
    const offsetMinutes = Number(offset.slice(4, 6));
    if (offsetHours > 18 || offsetMinutes > 59 || (offsetHours === 18 && offsetMinutes !== 0)) return value;
  }
  const precision = fraction ? '.' + fraction.slice(0, 3) : '';
  return formatInstant(new Date(`${date}T${hour}:${minute}:${second}${precision}${offset}`)) ?? value;
}

/** 表格仅格式化单元格文字，排序、过滤和接口提交继续使用行内原始时间。 */
export function formatReportTimeCell(row: Record<string, unknown>, column: { property: string }): string {
  return formatReportDateTime(row[column.property]);
}
