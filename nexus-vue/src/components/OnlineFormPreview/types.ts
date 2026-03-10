/**
 * 在线表单预览组件类型定义
 */

export type FormType = 'single' | 'main-sub' | null

export interface OnlineField {
  columnName: string
  columnComment: string
  htmlType: string
  formSpan?: number
  dictField?: string
  onChange?: string
  [key: string]: any
}

export interface PreviewData {
  type: FormType
  mainFields: OnlineField[]
  subFields: OnlineField[]
  mainForm: Record<string, any>
  subForms: Record<string, any>[]
  foreignKey: string
  mainTableId: string | number | null
  mainTableName: string
  subTableName: string
}

export interface UiState {
  visible: Record<string, boolean>
  disabled: Record<string, boolean>
  required: Record<string, boolean>
}

export interface RuntimeLinkageState {
  dictOptions: Record<string, any>
  popupParams: Record<string, any>
  clearFields: string[]
}
