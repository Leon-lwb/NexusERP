import type { BandCell, ReportDsl } from '@/types/report';
import { validateGridSelection, type FixedBand, type GridSelection } from '@/views/report/designer/gridCommands';

/** 选区属于编辑会话；固定格使用逻辑网格坐标，媒体校验当前数组位置。 */
export type EditorSelection =
  | { type: 'column'; index: number }
  | { type: 'cell'; band: FixedBand; row: number; column: number }
  | ({ type: 'range' } & GridSelection)
  | { type: 'media'; index: number };

/** 根据当前草稿解析的对象，不在撤销或合并之后复用旧对象引用。 */
export type ResolvedEditorSelection =
  | { type: 'column'; index: number; column: ReportDsl['columns'][number] }
  | { type: 'cell'; band: FixedBand; row: number; cell: BandCell; range: GridSelection }
  | { type: 'range'; range: GridSelection }
  | { type: 'media'; index: number; media: NonNullable<ReportDsl['print']>['media'][number] };

/** 在当前固定区中查找覆盖逻辑坐标的真实单元格。 */
export function cellAt(dsl: ReportDsl, band: FixedBand, row: number, column: number): { row: number; cell: BandCell } | undefined {
  if (!['headerRows', 'footerRows'].includes(band) || !Number.isInteger(row) || !Number.isInteger(column)) return;
  const rows = dsl[band];
  if (row < 0 || row >= rows.length || column < 0 || column >= dsl.columns.length) return;
  for (let origin = 0; origin <= row; origin++) {
    const cell = rows[origin].cells.find((item) => item.column <= column && item.column + item.colSpan > column && origin + item.rowSpan > row);
    if (cell) return { row: origin, cell };
  }
}

/** 每次从当前草稿解析选区，拒绝越界、跨区和切断合并格的矩形。 */
export function resolveEditorSelection(dsl: ReportDsl | undefined, selection: EditorSelection | undefined): ResolvedEditorSelection | undefined {
  if (!dsl || !selection) return;
  if (selection.type === 'column') {
    const column = dsl.columns[selection.index];
    if (Number.isInteger(selection.index) && selection.index >= 0 && column) return { type: 'column', index: selection.index, column };
  } else if (selection.type === 'media') {
    const media = dsl.print?.media[selection.index];
    if (Number.isInteger(selection.index) && selection.index >= 0 && media) return { type: 'media', index: selection.index, media };
  } else if (selection.type === 'cell') {
    const found = cellAt(dsl, selection.band, selection.row, selection.column);
    if (found)
      return {
        type: 'cell',
        band: selection.band,
        ...found,
        range: {
          band: selection.band,
          top: found.row,
          left: found.cell.column,
          bottom: found.row + found.cell.rowSpan - 1,
          right: found.cell.column + found.cell.colSpan - 1
        }
      };
  } else if (selection.type === 'range') {
    const { band, top, left, bottom, right } = selection;
    const range = { band, top, left, bottom, right };
    try {
      validateGridSelection(dsl, range);
      return { type: 'range', range };
    } catch {}
  }
}

/** 只为有效固定格或同区矩形提供批量工具上下文。 */
export function selectionRange(dsl: ReportDsl, selection: EditorSelection | undefined): GridSelection | undefined {
  const current = resolveEditorSelection(dsl, selection);
  return current?.type === 'cell' || current?.type === 'range' ? current.range : undefined;
}

/** 输入控件、可编辑区域及输入法组合输入保留自身快捷键语义。 */
export function isEditorInput(event: KeyboardEvent): boolean {
  return !!(
    event.isComposing ||
    event.keyCode === 229 ||
    (event.target as HTMLElement | null)?.closest?.(
      'input,textarea,select,[role="combobox"],[role="spinbutton"],[role="listbox"],[role="slider"],[contenteditable]:not([contenteditable="false"]),.el-select,.el-input-number'
    )
  );
}
