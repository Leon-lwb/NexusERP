import type { FormTableInfo } from '@/components/DesignerForm/types/index'

export interface TableQuery {
  pageNum: number
  pageSize: number
  tableName: string
  tableComment: string
  dataName: string
  queryType?: string
  queryFields?: string[]
  queryConditions?: any[]
  sortFields?: string[]
}

export interface TableVO {
  tableName: string
  tableComment: string
  columnsVo: TableColumn[]
  subTableName?: string
  subTableFkName?: string
  subColumnsVo?: TableColumn[]
  tplCategory?: string
}

export interface TableColumn {
  columnName: string
  columnType: string
  columnComment: string
  isNullable: string
  javaField: string
  htmlType: string
  dictType?: string
  dictFields?: string
  mapping?: string
  onChange?: string
}

export interface FormComponent {
  id: string
  type: string
  label: string
  field: string
  props: Record<string, any>
  required?: boolean
  isMaster?: boolean
  masterField?: string
  subTableName?: string
  subTableColumns?: SubTableColumn[]
  dbInfo: {
    tableName: string
    columnName: string
    columnType: string
    columnComment: string
    isNullable: string
    tableInfo?: FormTableInfo
  }
}

export interface SubTableColumn {
  id: string
  field: string
  label: string
  type: string
  props: Record<string, any>
  required?: boolean
  dbInfo: {
    tableName: string
    columnName: string
    columnType: string
    columnComment: string
    isNullable: string
    tableInfo?: FormTableInfo
  }
}

export interface FormConfig {
  labelWidth: string
  labelPosition: string
  size: string
  formName: string
  formDesc: string
  tableInfo: FormTableInfo
}

export interface FormContent {
  formConfig: FormConfig
  formComponents: FormComponent[]
  masterSubTableData: Record<string, any[]>
}

export interface FormState {
  formId?: string
  formName: string
  formDesc: string
  formContent: string
}
