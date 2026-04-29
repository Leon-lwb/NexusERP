export interface TableVO extends BaseEntity {
  createDept: number | string;
  tableId: string | number;
  dataName: string;
  tableName: string;
  tableComment: string;
  subTableName?: any;
  subTableFkName?: any;
  className: string;
  tplCategory: string;
  packageName: string;
  moduleName: string;
  businessName: string;
  functionName: string;
  functionAuthor: string;
  genType: string;
  genPath: string;
  pkColumn?: any;
  columns?: any;
  options?: any;
  remark?: any;
  treeCode?: any;
  treeParentCode?: any;
  treeName?: any;
  menuIds?: any;
  parentMenuId?: any;
  parentMenuName?: any;
  tree: boolean;
  crud: boolean;
  columnsVo: OnlFieldVo[]
  subColumnsVo?: OnlFieldVo[]
}

export interface OnlFieldVo {
  columnName: string
  columnComment: string
  htmlType: 'input' | 'select' | 'date' | 'radio' | 'checkbox' | 'textarea' | 'number'
  dictType?: string
  isRequired: string  // 修改为字符串类型
  formSpan?: number
  columnType: string
  dictData?: DictItem[] // 新增字典数据
}

export interface DictItem {
  dictValue: string
  dictLabel: string
}

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

export interface DbColumnVO extends BaseEntity {
  createDept?: any;
  columnId?: any;
  tableId?: any;
  columnName?: any;
  columnComment?: any;
  columnType?: any;
  javaType?: any;
  javaField?: any;
  isPk?: any;
  isIncrement?: any;
  isRequired?: any;
  isInsert?: any;
  isEdit?: any;
  isList?: any;
  isQuery?: any;
  queryType?: any;
  htmlType?: any;
  dictType?: any;
  sort?: any;
  increment: boolean;
  capJavaField?: any;
  usableColumn: boolean;
  superColumn: boolean;
  list: boolean;
  pk: boolean;
  insert: boolean;
  edit: boolean;
  query: boolean;
  required: boolean;
}

export interface DbTableVO {
  createDept?: any;
  tableId?: any;
  tableName: string;
  tableComment: string;
  subTableName?: any;
  subTableFkName?: any;
  className?: any;
  tplCategory?: any;
  packageName?: any;
  moduleName?: any;
  businessName?: any;
  functionName?: any;
  functionAuthor?: any;
  genType?: any;
  genPath?: any;
  pkColumn?: any;
  columns: DbColumnVO[];
  options?: any;
  remark?: any;
  treeCode?: any;
  treeParentCode?: any;
  treeName?: any;
  menuIds?: any;
  parentMenuId?: any;
  parentMenuName?: any;
  tree: boolean;
  crud: boolean;
}

export interface DbTableQuery extends PageQuery {
  dataName: string;
  tableName: string;
  tableComment: string;
}

export interface GenTableVO {
  info: DbTableVO;
  rows: DbColumnVO[];
  tables: DbTableVO[];
}

export interface DbColumnForm extends BaseEntity {
  createDept: number;
  columnId: string;
  tableId: string;
  columnName: string;
  columnComment: string;
  columnType: string;
  javaType: string;
  javaField: string;
  isPk: string;
  isIncrement: string;
  isRequired: string;
  isInsert?: any;
  isEdit: string;
  isList: string;
  isQuery?: any;
  queryType: string;
  htmlType: string;
  dictType: string;
  sort: number;
  increment: boolean;
  capJavaField: string;
  usableColumn: boolean;
  superColumn: boolean;
  list: boolean;
  pk: boolean;
  insert: boolean;
  edit: boolean;
  query: boolean;
  required: boolean;
}

export interface DbParamForm {
  treeCode?: any;
  treeName?: any;
  treeParentCode?: any;
  parentMenuId: string;
}

export interface DbTableForm extends BaseEntity {
  createDept?: any;
  tableId: string | string;
  tableName: string;
  tableComment: string;
  subTableName?: any;
  subTableFkName?: any;
  className: string;
  tplCategory: string;
  packageName: string;
  moduleName: string;
  businessName: string;
  functionName: string;
  functionAuthor: string;
  genType: string;
  genPath: string;
  pkColumn?: any;
  columns: DbColumnForm[];
  options: string;
  remark?: any;
  treeCode?: any;
  treeParentCode?: any;
  treeName?: any;
  menuIds?: any;
  parentMenuId: string;
  parentMenuName?: any;
  tree: boolean;
  crud: boolean;
  params: DbParamForm;
}

export interface TableDictVO {
  tableId?: any;
  tableName: string;
  tableComment: string;
}
