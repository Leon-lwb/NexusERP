import type { BandCell, ReportDsl, ReportPage, ReportStyle } from '@/types/report';

/** 固定区域选择不进入持久化定义。 */
export type FixedBand = 'headerRows' | 'footerRows';
/** 同一固定区域内按网格坐标表示的闭合矩形。 */
export interface GridSelection {
  /** 所属固定区域。 */
  band: FixedBand;
  /** 起始行。 */
  top: number;
  /** 起始列。 */
  left: number;
  /** 结束行。 */
  bottom: number;
  /** 结束列。 */
  right: number;
}
/** 受支持的纸张方向。 */
export type PaperOrientation = 'PORTRAIT' | 'LANDSCAPE';
/** 纸张预设使用毫米，定义仍保存pt。 */
export type PaperPreset = 'A4' | 'A5' | 'CUSTOM';
/** 每毫米对应的印刷点数。 */
const pointsPerMm = 72 / 25.4;

/** 创建独立草稿，避免撤销历史和源对象被修改。 */
function clone(dsl: ReportDsl): ReportDsl {
  return JSON.parse(JSON.stringify(dsl));
}
/** 拒绝无穷值和非数值尺寸。 */
function finite(value: number): number {
  if (!Number.isFinite(value)) throw Error('请输入有效尺寸');
  return value;
}
/** 毫米转换为pt，保留内部精度。 */
export function mmToPt(value: number): number {
  return finite(value) * pointsPerMm;
}
/** pt转换为毫米，显示组件自行处理展示小数位。 */
export function ptToMm(value: number): number {
  return finite(value) / pointsPerMm;
}
/** 获取与服务端PrintPlanner相同的实际纸张。 */
export function effectivePaper(dsl: ReportDsl): ReportPage {
  return dsl.print?.orientation === 'LANDSCAPE' ? { ...dsl.page, width: dsl.page.height, height: dsl.page.width } : { ...dsl.page };
}
/** 检查纸张、固定区和媒体能否容纳现有内容，并适配明细列。 */
function fitPaper(dsl: ReportDsl): ReportDsl {
  const page = effectivePaper(dsl);
  if (Object.values(page).some((value) => !Number.isFinite(value))) throw Error('纸张尺寸无效');
  const width = page.width - page.marginLeft - page.marginRight;
  const height = page.height - page.marginTop - page.marginBottom;
  if (
    dsl.page.width < 150 ||
    dsl.page.width > 1200 ||
    dsl.page.height < 200 ||
    dsl.page.height > 1700 ||
    [page.marginTop, page.marginRight, page.marginBottom, page.marginLeft].some((value) => value < 0) ||
    width < 40 ||
    height < 80 ||
    page.rowHeight < 12 ||
    page.rowHeight > 160
  )
    throw Error('纸张、边距或行高超出支持范围');
  if (dsl.kind === 'DOCUMENT') {
    const total = dsl.columns.reduce((sum, column) => sum + column.width, 0);
    if (!Number.isFinite(total) || total <= 0) throw Error('明细列宽无效');
    const widths = dsl.columns.map((column) => (width * column.width) / total);
    if (widths.some((value) => value < 20 || value > 600)) throw Error('纸张无法容纳当前列，请调整列数或纸张');
    dsl.columns.forEach((column, index) => {
      column.width = widths[index];
    });
  }
  for (const media of dsl.print?.media || []) {
    if (
      media.x < page.marginLeft ||
      media.y < page.marginTop ||
      media.x + media.width > page.width - page.marginRight ||
      media.y + media.height > page.height - page.marginBottom
    )
      throw Error('新纸张会使图片或条码越界，请先调整媒体位置');
  }
  return dsl;
}
/** 切换纸张与方向；旧草稿通过宽高表达方向，新草稿复用print方向。 */
export function setPaper(dsl: ReportDsl, preset: PaperPreset, orientation: PaperOrientation, custom?: { width: number; height: number }): ReportDsl {
  if (!['A4', 'A5', 'CUSTOM'].includes(preset) || !['PORTRAIT', 'LANDSCAPE'].includes(orientation)) throw Error('纸张类型或方向无效');
  const actual = effectivePaper(dsl);
  const size =
    preset === 'A4'
      ? [mmToPt(210), mmToPt(297)]
      : preset === 'A5'
        ? [mmToPt(148), mmToPt(210)]
        : [finite(custom?.width ?? actual.width), finite(custom?.height ?? actual.height)].sort((left, right) => left - right);
  const next = clone(dsl);
  if (next.print) {
    next.page.width = size[0];
    next.page.height = size[1];
    next.print.orientation = orientation;
  } else {
    next.page.width = orientation === 'LANDSCAPE' ? size[1] : size[0];
    next.page.height = orientation === 'LANDSCAPE' ? size[0] : size[1];
  }
  return fitPaper(next);
}
/** 按实际纸张修改毫米输入转换后的尺寸，不重复交换方向。 */
export function updatePaper(dsl: ReportDsl, patch: Partial<ReportPage>): ReportDsl {
  const next = clone(dsl);
  const actual = { ...effectivePaper(dsl), ...patch };
  next.page = next.print?.orientation === 'LANDSCAPE' ? { ...actual, width: actual.height, height: actual.width } : actual;
  return fitPaper(next);
}
/** 显式开启第二版媒体能力，同时保持原纸张朝向和实际尺寸。 */
export function enablePrint(dsl: ReportDsl): ReportDsl {
  if (dsl.kind !== 'DOCUMENT') throw Error('打印扩展仅支持单据');
  const next = clone(dsl);
  if (next.print) return next;
  next.schemaVersion = 2;
  const landscape = next.page.width > next.page.height;
  if (landscape) [next.page.width, next.page.height] = [next.page.height, next.page.width];
  next.print = { orientation: landscape ? 'LANDSCAPE' : 'PORTRAIT', groupBy: [], repeatFooter: false, media: [] };
  return next;
}
/** 相邻列分摊边界位移，保持总宽度且不生成非法列宽。 */
export function resizeColumnBoundary(dsl: ReportDsl, index: number, delta: number): ReportDsl {
  if (!Number.isInteger(index) || index < 0 || index >= dsl.columns.length - 1) throw Error('请选择有效列边界');
  finite(delta);
  const next = clone(dsl);
  const left = next.columns[index],
    right = next.columns[index + 1];
  const change = Math.max(Math.max(20 - left.width, right.width - 600), Math.min(delta, Math.min(600 - left.width, right.width - 20)));
  left.width += change;
  right.width -= change;
  return next;
}
/** 修改单列宽度时同步调整相邻列，单列单据保持纸张宽度。 */
export function resizeColumn(dsl: ReportDsl, index: number, width: number): ReportDsl {
  finite(width);
  if (!dsl.columns[index]) throw Error('请选择有效列');
  if (dsl.kind !== 'DOCUMENT') {
    const next = clone(dsl);
    next.columns[index].width = Math.max(20, Math.min(600, width));
    return next;
  }
  if (dsl.columns.length === 1) return clone(dsl);
  return index < dsl.columns.length - 1
    ? resizeColumnBoundary(dsl, index, width - dsl.columns[index].width)
    : resizeColumnBoundary(dsl, index - 1, dsl.columns[index].width - width);
}
/** 固定行拖动受现有12到160pt约束。 */
export function resizeBandRow(dsl: ReportDsl, band: FixedBand, row: number, delta: number): ReportDsl {
  if (!['headerRows', 'footerRows'].includes(band) || !Number.isInteger(row) || !dsl[band]?.[row]) throw Error('请选择有效固定行');
  const next = clone(dsl);
  next[band][row].height = Math.max(12, Math.min(160, next[band][row].height + finite(delta)));
  return next;
}
/** 判断单元格与选区相交。 */
function intersects(row: number, cell: BandCell, selection: GridSelection): boolean {
  return (
    row <= selection.bottom &&
    row + cell.rowSpan - 1 >= selection.top &&
    cell.column <= selection.right &&
    cell.column + cell.colSpan - 1 >= selection.left
  );
}
/** 检查选区不会跨区、越界或截断既有合并。 */
function selectedCells(dsl: ReportDsl, selection: GridSelection): { row: number; cell: BandCell }[] {
  if (
    !['headerRows', 'footerRows'].includes(selection.band) ||
    ![selection.top, selection.bottom, selection.left, selection.right].every(Number.isInteger) ||
    selection.top < 0 ||
    selection.bottom < selection.top ||
    selection.bottom >= dsl[selection.band].length ||
    selection.left < 0 ||
    selection.right < selection.left ||
    selection.right >= dsl.columns.length
  )
    throw Error('请选择同一固定区域内的矩形单元格');
  const result: { row: number; cell: BandCell }[] = [];
  dsl[selection.band].forEach((bandRow, row) => {
    for (const cell of bandRow.cells) {
      if (!intersects(row, cell, selection)) continue;
      if (
        row < selection.top ||
        row + cell.rowSpan - 1 > selection.bottom ||
        cell.column < selection.left ||
        cell.column + cell.colSpan - 1 > selection.right
      )
        throw Error('选区切断已有合并，请完整选择合并单元格');
      result.push({ row, cell });
    }
  });
  return result;
}
/** 合并同一区域的完整矩形并保留左上格内容。 */
export function mergeCells(dsl: ReportDsl, selection: GridSelection): ReportDsl {
  selectedCells(dsl, selection);
  const next = clone(dsl);
  const origin = next[selection.band][selection.top].cells.find((cell) => cell.column === selection.left);
  const merged: BandCell = origin
    ? { ...origin }
    : { column: selection.left, colSpan: 1, rowSpan: 1, text: '', field: null, style: { ...next.columns[selection.left].style } };
  merged.colSpan = selection.right - selection.left + 1;
  merged.rowSpan = selection.bottom - selection.top + 1;
  next[selection.band].forEach((row, index) => {
    row.cells = row.cells.filter((cell) => !intersects(index, cell, selection));
  });
  next[selection.band][selection.top].cells.push(merged);
  next[selection.band][selection.top].cells.sort((left, right) => left.column - right.column);
  return next;
}
/** 将完整选区中的跨格拆开，内容只保留在各原始左上格。 */
export function splitCells(dsl: ReportDsl, selection: GridSelection): ReportDsl {
  const next = clone(dsl);
  const cells = selectedCells(next, selection);
  for (const item of cells) {
    next[selection.band][item.row].cells = next[selection.band][item.row].cells.filter((cell) => cell !== item.cell);
    for (let row = item.row; row < item.row + item.cell.rowSpan; row++) {
      for (let column = item.cell.column; column < item.cell.column + item.cell.colSpan; column++) {
        next[selection.band][row].cells.push({
          ...item.cell,
          column,
          rowSpan: 1,
          colSpan: 1,
          style: { ...item.cell.style },
          text: row === item.row && column === item.cell.column ? item.cell.text : '',
          field: row === item.row && column === item.cell.column ? item.cell.field : null
        });
      }
    }
  }
  next[selection.band].forEach((row) => row.cells.sort((left, right) => left.column - right.column));
  return next;
}
/** 对完整矩形内的固定单元格应用样式，不修改明细或其他固定区。 */
export function styleCells(dsl: ReportDsl, selection: GridSelection, style: Partial<ReportStyle>): ReportDsl {
  const next = clone(dsl);
  for (const item of selectedCells(next, selection)) item.cell.style = { ...item.cell.style, ...style };
  return next;
}
/** 媒体移动及缩放始终落在可打印区域内，二维码保持正方形。 */
export function positionMedia(dsl: ReportDsl, index: number, patch: Partial<{ x: number; y: number; width: number; height: number }>): ReportDsl {
  if (!dsl.print?.media[index]) throw Error('请选择有效图片或条码');
  Object.values(patch).forEach(finite);
  const next = clone(dsl),
    page = effectivePaper(next),
    media = next.print!.media[index];
  Object.assign(media, patch);
  const maxWidth = page.width - page.marginLeft - page.marginRight;
  const maxHeight = page.height - page.marginTop - page.marginBottom;
  media.width = Math.max(16, Math.min(maxWidth, media.width));
  media.height = Math.max(16, Math.min(maxHeight, media.height));
  if (media.kind === 'QR') media.width = media.height = Math.min(maxWidth, maxHeight, patch.width === undefined ? media.height : media.width);
  media.x = Math.max(page.marginLeft, Math.min(page.width - page.marginRight - media.width, media.x));
  media.y = Math.max(page.marginTop, Math.min(page.height - page.marginBottom - media.height, media.y));
  return next;
}
