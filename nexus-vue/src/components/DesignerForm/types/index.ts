import { Component } from 'vue'

export interface FormComponent {
  id: string
  type: string
  label: string
  field: string
  htmlType?: 'input' | 'textarea' | 'select' | 'radio' | 'checkbox' | 'date' | 'datetime'
  props: {
    [key: string]: any
    placeholder?: string
    defaultValue?: any
    disabled?: boolean
    clearable?: boolean
    size?: 'default' | 'large' | 'small'
    dbField?: DbField
    options?: Array<{ label: string; value: any }>
  }
  required?: boolean
  isMaster?: boolean
  masterField?: string
  subTableName?: string
  subTableColumns?: SubTableColumn[]
  dbInfo?: {
    tableName: string
    columnName: string
    columnType: string
    columnComment: string
    isNullable: string
    tableInfo?: FormTableInfo
  }
  children?: FormComponent[]
  dictType?: string
  dictFields?: string
  mapping?: string
  showDisplay?: boolean
  onChange?: string
}

export interface SubTableColumn {
  id: string
  field: string
  label: string
  type: string
  props: {
    [key: string]: any
    placeholder?: string
    defaultValue?: any
    disabled?: boolean
    clearable?: boolean
    size?: 'default' | 'large' | 'small'
    dbField?: DbField
  }
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
  labelPosition: 'left' | 'right' | 'top'
  size: 'large' | 'default' | 'small'
  formName: string
  formDesc: string
  dbConfig?: {
    mainTable?: string
    subTables?: string[]
  }
}

export interface FormContent {
  config: FormConfig
  components: FormComponent[]
  masterSubTableData: Record<string, any[]>
}

export interface FormState {
  formId?: string | number
  formName: string
  formDesc: string
  formContent: string
}

export interface TableColumn {
  columnId: number
  tableId: number
  columnName: string
  columnComment: string
  columnType: string
  javaType: string
  javaField: string
  isPk: string
  isIncrement: string
  isRequired: string
  isInsert: string
  isEdit: string
  isList: string
  isQuery: string
  queryType: string
  htmlType: string
  dictType: string
  sort: number
}

export interface Table {
  tableId: number
  tableName: string
  tableComment: string
  className: string
  columns: TableColumn[]
}

export interface ComponentDefinition {
  label: string
  type: string
  icon: any
  component?: any
  props: Record<string, any>
  subTableColumns?: SubTableColumn[]
  dbInfo?: {
    tableName: string
    columnName: string
    columnType: string
    columnComment: string
    isNullable: string
  }
}

export interface TableQuery {
  pageNum: number
  pageSize: number
  tableName?: string
}

export interface TableVO {
  id: string
  tableName: string
  tableComment: string
  createTime: string
  updateTime: string
}

export interface ColumnVO {
  id: string
  tableId: string
  columnName: string
  columnComment: string
  columnType: string
  isPrimary: boolean
  isNullable: boolean
  defaultValue: string
  createTime: string
  updateTime: string
}

export interface UserVO {
  id: string
  username: string
  nickname: string
  email: string
  phone: string
  deptId: string
  deptName: string
  createTime: string
  updateTime: string
}

export interface DbField {
  tableName: string
  columnName: string
  columnType: string
  columnComment: string
  isNullable: string
}

export interface DbTable {
  tableId: number
  tableName: string
  tableComment: string
  className: string
  columns: TableColumn[]
}

export interface DbColumn {
  columnId: number
  tableId: number
  columnName: string
  columnComment: string
  columnType: string
  javaType: string
  javaField: string
  isPk: string
  isIncrement: string
  isRequired: string
  isInsert: string
  isEdit: string
  isList: string
  isQuery: string
  queryType: string
  htmlType: string
  dictType: string
  sort: number
}

export interface TableColumnInfo {
  columnName: string
  columnType: string
  columnComment: string
  isNullable: string
  isPk: string
  dictType?: string
  htmlType?: string
}

export interface TableInfo {
  tableName: string
  tableComment: string
  columns: TableColumnInfo[]
}

export interface SubTableInfo extends TableInfo {
  fkName: string
}

export interface FormTableInfo {
  mainTable: TableInfo
  subTable?: SubTableInfo
}

export type { TableColumnInfo, TableInfo, SubTableInfo, FormTableInfo } 