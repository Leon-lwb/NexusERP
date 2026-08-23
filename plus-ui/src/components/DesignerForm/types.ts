export interface ComponentDefinition {
  label: string;
  type: string;
  icon?: any;
  component?: any;
  props?: Record<string, any>;
  subTableColumns?: any[];
  dbInfo?: {
    tableName: string;
    columnName: string;
    columnType: string;
    columnComment: string;
    isNullable: string;
  };
}

export interface FormComponent {
  id: string;
  label: string;
  field: string;
  required: boolean;
  type: string;
  props: Record<string, any>;
  dbInfo: {
    tableName: string;
    columnName: string;
    columnType: string;
    columnComment: string;
    isNullable: string;
  };
  isMaster: boolean;
  masterField: string;
  subTableName: string | undefined;
  subTableColumns: SubTableColumn[];
  dictType?: string;
  dictFields?: string;
  mapping?: string;
  showDisplay?: boolean;
}

export interface SubTableColumn {
  id: string;
  label: string;
  field: string;
  type: string;
  props: Record<string, any>;
  required: boolean;
  dbInfo: {
    tableName: string;
    columnName: string;
    columnType: string;
    columnComment: string;
    isNullable: string;
  };
  dictType?: string;
  dictFields?: string;
  mapping?: string;
  showDisplay?: boolean;
}
