export interface SubTableColumn {
  id: string;
  label: string;
  field: string;
  type: string;
  props: Record<string, any>;
  required?: boolean;
  icon?: any;
  dbInfo: {
    tableName: string;
    columnName: string;
    columnType: string;
    columnComment: string;
    isNullable: string;
  };
}

export interface FormTableInfo {
  mainTable: {
    tableName: string;
    tableComment: string;
    columns: {
      columnName: string;
      columnType: string;
      columnComment: string;
      isNullable: string;
      isPk: string;
      dictType?: string;
      htmlType?: string;
    }[];
  };
  subTable?: {
    tableName: string;
    tableComment: string;
    fkName: string;
    columns: {
      columnName: string;
      columnType: string;
      columnComment: string;
      isNullable: string;
      isPk: string;
      dictType?: string;
      htmlType?: string;
    }[];
  };
}

export interface FormComponent {
  id: string;
  type: string;
  label: string;
  field: string;
  props: Record<string, any>;
  required?: boolean;
  isMaster?: boolean;
  masterField?: string;
  subTableName?: string;
  subTableColumns?: SubTableColumn[];
  icon?: any;
  dbInfo: {
    tableName: string;
    columnName: string;
    columnType: string;
    columnComment: string;
    isNullable: string;
    tableInfo?: FormTableInfo;
  };
}

export interface FormConfig {
  labelWidth: string;
  labelPosition: string;
  size: string;
  formName: string;
  formDesc: string;
  tableInfo: FormTableInfo;
}

export interface TableQuery {
  pageNum: number;
  pageSize: number;
  tableName: string;
  tableComment: string;
  dataName: string;
}

export interface ExtendedTableQuery extends TableQuery {
  [key: string]: any;
}

export interface TableVO {
  tableId: number;
  tableName: string;
  tableComment: string;
  subTableName?: string;
  subTableFkName?: string;
  columnsVo: ColumnVO[];
  subColumnsVo?: ColumnVO[];
}

export interface ColumnVO {
  columnName: string;
  columnType: string;
  columnComment: string;
  isNullable: string;
  isPk: string;
  isRequired?: string;
  onChange?: string;
  dictType?: string;
  dictFields?: string;
  mapping?: string;
  htmlType?: string;
}
