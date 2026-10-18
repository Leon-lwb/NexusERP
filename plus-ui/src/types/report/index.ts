/** 声明值类型结构，保留服务端精确值和固定版本约束。 */
export type ValueType = 'STRING' | 'ID' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN' | 'DATE' | 'DATETIME';
/** 声明精确类型值结构，保留服务端精确值和固定版本约束。 */
export interface TypedValue {
  /** 保存当前 `type` 值。 */
  type: ValueType;
  /** 保存当前 `value` 值。 */
  value: string | null;
  /** 随服务端快照固定的字典显示名称，不替换原始编码。 */
  displayValue?: string | null;
}
/** 声明精确类型Row结构，保留服务端精确值和固定版本约束。 */
export type TypedRow = Record<string, TypedValue>;
/** 声明报表Style结构，保留服务端精确值和固定版本约束。 */
export interface ReportStyle {
  /** 保存当前 `fontSize` 值。 */
  fontSize: number;
  /** 保存当前 `bold` 值。 */
  bold: boolean;
  /** 保存当前 `align` 值。 */
  align: 'LEFT' | 'CENTER' | 'RIGHT';
  /** 保存当前 `color` 值。 */
  color: string;
  /** 保存当前 `background` 值。 */
  background: string;
}
/** 声明报表Column结构，保留服务端精确值和固定版本约束。 */
export interface ReportColumn {
  /** 保存当前 `field` 值。 */
  field: string;
  /** 保存展示标题。 */
  label: string;
  /** 保存当前 `width` 值。 */
  width: number;
  /** 保存当前 `scale` 值。 */
  scale: number;
  /** 保存当前 `expression` 值。 */
  expression: string | null;
  /** 保存当前 `style` 值。 */
  style: ReportStyle;
}
/** 声明报表参数结构，保留服务端精确值和固定版本约束。 */
export interface ReportParameter {
  /** 保存当前 `name` 值。 */
  name: string;
  /** 保存展示标题。 */
  label: string;
  /** 保存当前 `type` 值。 */
  type: ValueType;
  /** 保存当前 `required` 值。 */
  required: boolean;
  /** 保存当前 `defaultValue` 值。 */
  defaultValue?: string | null;
}
/** 声明报表Page结构，保留服务端精确值和固定版本约束。 */
export interface ReportPage {
  /** 保存当前 `width` 值。 */
  width: number;
  /** 保存当前 `height` 值。 */
  height: number;
  /** 保存当前 `marginTop` 值。 */
  marginTop: number;
  /** 保存当前 `marginRight` 值。 */
  marginRight: number;
  /** 保存当前 `marginBottom` 值。 */
  marginBottom: number;
  /** 保存当前 `marginLeft` 值。 */
  marginLeft: number;
  /** 保存当前 `rowHeight` 值。 */
  rowHeight: number;
}
/** 声明BandCell结构，保留服务端精确值和固定版本约束。 */
export interface BandCell {
  /** 保存当前 `column` 值。 */
  column: number;
  /** 保存当前 `colSpan` 值。 */
  colSpan: number;
  /** 保存当前 `rowSpan` 值。 */
  rowSpan: number;
  /** 保存当前 `text` 值。 */
  text: string | null;
  /** 保存当前 `field` 值。 */
  field: string | null;
  /** 保存当前 `style` 值。 */
  style: ReportStyle;
}
/** 声明BandRow结构，保留服务端精确值和固定版本约束。 */
export interface BandRow {
  /** 保存当前 `height` 值。 */
  height: number;
  /** 保存当前 `cells` 值。 */
  cells: BandCell[];
}
/** 声明报表Summary结构，保留服务端精确值和固定版本约束。 */
export interface ReportSummary {
  /** 保存当前 `field` 值。 */
  field: string | null;
  /** 保存当前 `function` 值。 */
  function: 'SUM' | 'COUNT' | 'COUNT_ROWS' | 'MIN' | 'MAX';
  /** 保存当前 `groupBy` 值。 */
  groupBy: string[];
  /** 保存展示标题。 */
  label: string;
  /** 保存当前 `scale` 值。 */
  scale: number;
}
/** 声明报表Dsl结构，保留服务端精确值和固定版本约束。 */
export interface ReportDsl {
  /** 第二版的交叉定义，第一版传输不添加该字段。 */
  pivot?: PivotDefinition;
  /** 第二版单据使用明确的私有资源、分组和重复区域。 */
  print?: PrintDefinition;
  /** 保存当前 `schemaVersion` 值。 */
  schemaVersion: 1 | 2;
  /** 保存业务编码。 */
  code: string;
  /** 保存当前 `title` 值。 */
  title: string;
  /** 保存受支持类型。 */
  kind: 'TABLE' | 'DOCUMENT' | 'PIVOT';
  /** 保存当前 `datasetCode` 值。 */
  datasetCode: string;
  /** 保存当前 `datasetVersionId` 值。 */
  datasetVersionId: string;
  /** 保存当前 `columns` 值。 */
  columns: ReportColumn[];
  /** 保存按契约解析的输入参数。 */
  parameters: ReportParameter[];
  /** 保存当前页。 */
  page: ReportPage;
  /** 保存当前 `headerRows` 值。 */
  headerRows: BandRow[];
  /** 保存当前 `footerRows` 值。 */
  footerRows: BandRow[];
  /** 保存当前 `summaries` 值。 */
  summaries: ReportSummary[];
  /** 保存当前 `repeatHeader` 值。 */
  repeatHeader: boolean;
}
/** 声明数据集字段结构，保留服务端精确值和固定版本约束。 */
export interface DatasetField {
  /** 保存当前 `name` 值。 */
  name: string;
  /** 保存展示标题。 */
  label: string;
  /** 保存当前 `type` 值。 */
  type: ValueType;
  /** 保存待核对权限。 */
  permission: string | null;
  /** 保存当前 `aggregationKeys` 值。 */
  aggregationKeys: string[];
  /** 保存当前 `aggregatable` 值。 */
  aggregatable: boolean;
}
/** 声明数据集契约描述结构，保留服务端精确值和固定版本约束。 */
export interface DatasetDescriptor {
  /** 保存业务编码。 */
  code: string;
  /** 保存当前 `name` 值。 */
  name: string;
  /** 保存固定版本。 */
  version: number;
  /** 保存待核对权限。 */
  permission: string;
  /** 保存当前 `document` 值。 */
  document: boolean;
  /** 保存允许访问的字段。 */
  fields: DatasetField[];
  /** 保存按契约解析的输入参数。 */
  parameters: ReportParameter[];
  /** 保存当前 `defaultColumns` 值。 */
  defaultColumns: string[];
}
/** 声明报表数据集结构，保留服务端精确值和固定版本约束。 */
export interface ReportDataset {
  /** 保存资源标识。 */
  id: string;
  /** 保存业务编码。 */
  code: string;
  /** 保存当前 `name` 值。 */
  name: string;
  /** 保存当前 `provider` 值。 */
  provider: string;
  /** 保存当前 `publishedVersion` 值。 */
  publishedVersion: string;
  /** 保存当前 `status` 值。 */
  status: 'ACTIVE' | 'OFFLINE' | 'ARCHIVED';
  /** 保存租约或资源持有者。 */
  owner: string;
}
/** 声明数据集View结构，保留服务端精确值和固定版本约束。 */
export interface DatasetView {
  /** 保存当前 `dataset` 值。 */
  dataset: ReportDataset;
  /** 保存可信数据集契约。 */
  descriptor: DatasetDescriptor;
  /** 保存当前 `canManage` 值。 */
  canManage: boolean;
}
/** 声明报表定义结构，保留服务端精确值和固定版本约束。 */
export interface ReportDefinition {
  /** 保存资源标识。 */
  id: string;
  /** 保存业务编码。 */
  code: string;
  /** 保存当前 `name` 值。 */
  name: string;
  /** 保存受支持类型。 */
  kind: 'TABLE' | 'DOCUMENT' | 'PIVOT';
  /** 保存当前 `catalogId` 值。 */
  catalogId: string | null;
  /** 保存待处理定义文本。 */
  json: string | null;
  /** 保存预期草稿修订。 */
  revision: number;
  /** 保存当前 `publishedVersion` 值。 */
  publishedVersion: string | null;
  /** 保存当前 `status` 值。 */
  status: 'DRAFT' | 'PUBLISHED' | 'OFFLINE' | 'ARCHIVED';
  /** 保存租约或资源持有者。 */
  owner: string;
  /** 保存当前 `updatedAt` 值。 */
  updatedAt: string;
}
/** 声明报表Catalog结构，保留服务端精确值和固定版本约束。 */
export interface ReportCatalog {
  /** 保存资源标识。 */
  id: string;
  /** 保存当前 `parentId` 值。 */
  parentId: string;
  /** 保存当前 `name` 值。 */
  name: string;
  /** 保存租约或资源持有者。 */
  owner: string;
}
/** 声明报表版本结构，保留服务端精确值和固定版本约束。 */
export interface ReportVersion {
  /** 保存资源标识。 */
  id: string;
  /** 保存当前 `reportId` 值。 */
  reportId: string;
  /** 保存当前序号。 */
  number: number;
  /** 保存定义格式版本。 */
  schema: number;
  /** 保存执行器版本。 */
  engine: string;
  /** 保存待处理定义文本。 */
  json: string;
  /** 保存预期完整性校验和。 */
  checksum: string;
  /** 保存当前 `datasetVersion` 值。 */
  datasetVersion: string;
  /** 保存当前 `createdAt` 值。 */
  createdAt: string;
}
/** 声明报表Detail结构，保留服务端精确值和固定版本约束。 */
export interface ReportDetail {
  /** 保存当前 `definition` 值。 */
  definition: ReportDefinition;
  /** 保存报表定义。 */
  dsl: ReportDsl;
}
/** 声明Published报表结构，保留服务端精确值和固定版本约束。 */
export interface PublishedReport {
  /** 固定数据集与模板合并后的默认提示，不修改已发布DSL。 */
  effectiveParameters?: ReportParameter[];
  /** 保存当前 `versionId` 值。 */
  versionId: string;
  /** 保存当前序号。 */
  number: number;
  /** 保存报表定义。 */
  dsl: ReportDsl;
}
/** 声明Summary值结构，保留服务端精确值和固定版本约束。 */
export interface SummaryValue {
  /** 保存展示标题。 */
  label: string;
  /** 保存当前 `group` 值。 */
  group: Record<string, TypedValue>;
  /** 保存当前 `value` 值。 */
  value: TypedValue;
}
/** 声明渲染Cell结构，保留服务端精确值和固定版本约束。 */
export interface RenderCell {
  /** 保存当前 `band` 值。 */
  band: string;
  /** 保存当前 `rowIndex` 值。 */
  rowIndex: number;
  /** 保存当前 `column` 值。 */
  column: number;
  /** 保存当前 `colSpan` 值。 */
  colSpan: number;
  /** 保存当前 `rowSpan` 值。 */
  rowSpan: number;
  /** 保存当前 `x` 值。 */
  x: number;
  /** 保存当前 `y` 值。 */
  y: number;
  /** 保存当前 `width` 值。 */
  width: number;
  /** 保存当前 `height` 值。 */
  height: number;
  /** 保存当前 `lines` 值。 */
  lines: string[];
  /** 保存当前 `style` 值。 */
  style: ReportStyle;
  /** 保存当前 `value` 值。 */
  value: TypedValue | null;
  /** 保存当前 `scale` 值。 */
  scale: number;
}
/** 声明渲染Page结构，保留服务端精确值和固定版本约束。 */
export interface RenderPage {
  /** 页面媒体只引用固定计划内的资源。 */
  media?: {
    /**
     * 当前契约的resource配置或状态
     */
    resource: string; /**
     * 当前契约的x配置或状态
     */
    x: number; /**
     * 当前契约的y配置或状态
     */
    y: number; /**
     * 布局宽度
     */
    width: number; /**
     * 布局高度
     */
    height: number;
  }[];
  /** 保存当前序号。 */
  number: number;
  /** 保存当前 `width` 值。 */
  width: number;
  /** 保存当前 `height` 值。 */
  height: number;
  /** 保存当前 `cells` 值。 */
  cells: RenderCell[];
}
/** 声明渲染计划结构，保留服务端精确值和固定版本约束。 */
export interface RenderPlan {
  /** 所有格式共用已鉴权的固定PNG资源。 */
  rasters?: Record<
    string,
    {
      /**
       * 当前契约的png配置或状态
       */
      png: string; /**
       * 实际内容的SHA-256校验和
       */
      checksum: string;
    }
  >;
  /** 保存当前 `schemaVersion` 值。 */
  schemaVersion: number;
  /** 保存当前 `rowCount` 值。 */
  rowCount: number;
  /** 保存当前 `fontName` 值。 */
  fontName: string;
  /** 保存当前 `pages` 值。 */
  pages: RenderPage[];
  /** 保存当前 `columns` 值。 */
  columns: ReportColumn[];
  /** 保存当前 `summaries` 值。 */
  summaries: SummaryValue[];
}
/** 声明DraftPreview结构，保留服务端精确值和固定版本约束。 */
export interface DraftPreview {
  /** 保存待处理业务行。 */
  rows: TypedRow[];
  /** 保存当前 `total` 值。 */
  total: number;
  /** 保存当前 `dataTime` 值。 */
  dataTime: string;
  /** 保存当前 `summaries` 值。 */
  summaries: SummaryValue[];
  /** 保存当前 `plan` 值。 */
  plan: RenderPlan | null;
}
/** 声明PageData结构，保留服务端精确值和固定版本约束。 */
export interface PageData {
  /** 保存当前 `columns` 值。 */
  columns: ReportColumn[];
  /** 保存待处理业务行。 */
  rows: TypedRow[];
  /** 保存当前 `total` 值。 */
  total: number;
  /** 保存当前 `dataTime` 值。 */
  dataTime: string;
  /** 保存运行一致性模式。 */
  mode: 'LIVE' | 'SNAPSHOT';
  /** 保存当前 `summaries` 值。 */
  summaries: SummaryValue[];
}
/** 声明报表Run结构，保留服务端精确值和固定版本约束。 */
export interface ReportRun {
  /** 保存资源标识。 */
  id: string;
  /** 保存当前 `reportId` 值。 */
  reportId: string;
  /** 保存固定版本。 */
  version: string;
  /** 保存运行一致性模式。 */
  mode: 'LIVE' | 'SNAPSHOT';
  /** 保存目标状态。 */
  state: string;
  /** 保存待处理业务行。 */
  rows: number;
  /** 保存当前 `dataTime` 值。 */
  dataTime: string;
  /** 保存资源到期时点。 */
  expires: string;
  /** 保存当前 `error` 值。 */
  error: string | null;
}
/** 声明导出Format结构，保留服务端精确值和固定版本约束。 */
export type ExportFormat = 'PDF' | 'XLSX_DATA' | 'XLSX_LAYOUT';
/** 声明导出任务结构，保留服务端精确值和固定版本约束。 */
export interface ExportJob {
  /** 保存当前 `phase` 值。 */
  phase?: string;
  /** 保存当前 `processedRows` 值。 */
  processedRows?: number;
  /** 保存当前 `totalRows` 值。 */
  totalRows?: number | null;
  /** 保存当前 `retryRound` 值。 */
  retryRound?: number;
  /** 保存当前 `heartbeat` 值。 */
  heartbeat?: string | null;
  /** 保存当前 `canRetry` 值。 */
  canRetry?: boolean;
  /** 保存资源标识。 */
  id: string;
  /** 保存当前 `runId` 值。 */
  runId: string;
  /** 保存当前 `format` 值。 */
  format: ExportFormat;
  /** 保存目标状态。 */
  state: 'QUEUED' | 'RUNNING' | 'CANCEL_REQUESTED' | 'CANCELLED' | 'SUCCEEDED' | 'FAILED' | 'EXPIRED';
  /** 保存当前 `attempts` 值。 */
  attempts: number;
  /** 保存受控字节数据。 */
  bytes: number | null;
  /** 保存资源到期时点。 */
  expires: string;
  /** 保存当前 `error` 值。 */
  error: string | null;
}
/** 声明ResourceGrant结构，保留服务端精确值和固定版本约束。 */
export interface ResourceGrant {
  /** 保存当前 `subjectType` 值。 */
  subjectType: 'USER' | 'ROLE' | 'DEPT';
  /** 保存当前 `subjectId` 值。 */
  subjectId: string;
  /** 保存资源操作。 */
  action: 'VIEW' | 'EDIT' | 'PUBLISH' | 'EXPORT' | 'MANAGE';
}

/** 声明数据集版本View结构，保留服务端精确值和固定版本约束。 */
export interface DatasetVersionView {
  /** 保存资源标识。 */
  id: string;
  /** 保存当前 `datasetId` 值。 */
  datasetId: string;
  /** 保存业务编码。 */
  code: string;
  /** 保存当前序号。 */
  number: number;
  /** 保存当前 `current` 值。 */
  current: boolean;
  /** 保存可信数据集契约。 */
  descriptor: DatasetDescriptor;
}
/** 声明数据集版本Diff结构，保留服务端精确值和固定版本约束。 */
export interface DatasetVersionDiff {
  /** 保存当前 `from` 值。 */
  from: string;
  /** 保存当前 `to` 值。 */
  to: string;
  /** 保存当前 `breaking` 值。 */
  breaking: boolean;
  /** 保存当前 `changes` 值。 */
  changes: {
    /** 保存当前 `category` 值。 */
    category: string;
    /** 保存当前 `name` 值。 */
    name: string;
    /** 保存受支持类型。 */
    kind: string;
    /** 保存当前 `breaking` 值。 */
    breaking: boolean;
  }[];
}
/** 声明报表引擎Capability结构，保留服务端精确值和固定版本约束。 */
export interface ReportEngineCapability {
  /** 保存当前 `schemaVersion` 值。 */
  schemaVersion: number;
  /** 保存当前 `engineVersion` 值。 */
  engineVersion: string;
  /** 保存当前 `features` 值。 */
  features: string[];
  /** 保存当前 `enabled` 值。 */
  enabled: boolean;
  /** 保存当前 `defaultForDraft` 值。 */
  defaultForDraft: boolean;
}

/** 第二版交叉表只接受可信字段及白名单指标。 */
export interface PivotDefinition {
  /**
   * 当前契约的rowDimensions配置或状态
   */
  rowDimensions: string[];
  /**
   * 当前契约的columnDimensions配置或状态
   */
  columnDimensions: string[];
  /**
   * 当前契约的measures配置或状态
   */
  measures: {
    /**
     * 已批准的逻辑字段
     */
    field: string; /**
     * 白名单聚合函数
     */
    function: 'SUM' | 'COUNT' | 'COUNT_ROWS' | 'MIN' | 'MAX' | 'AVG'; /**
     * 用户可见标签
     */
    label: string; /**
     * 输出小数位数
     */
    scale: number;
  }[];
  /**
   * 当前契约的subtotals配置或状态
   */
  subtotals: boolean;
  /**
   * 按聚合分区计算的总计
   */
  totals: boolean;
  /**
   * 每分区保留的排名桶数量
   */
  topN: number;
}
/** 固定快照交叉结果和服务端钻取坐标。 */
export interface PivotResult {
  /**
   * 输出列契约
   */
  columns: TypedValue[][];
  /**
   * 精确类型的数据行
   */
  rows: {
    /**
     * 固定类型的轴维度值
     */
    keys: TypedValue[]; /**
     * 固定坐标的单元格列表
     */
    cells: {
      /**
       * 本次固定快照的服务端钻取坐标
       */
      coordinate: string; /**
       * 固定类型的精确值列表
       */
      values: TypedValue[];
    }[]; /**
     * 当前契约的total配置或状态
     */
    total: TypedValue[]; /**
     * 是否为TopN后的其他桶
     */
    other: boolean; /**
     * 是否为安全聚合分区内的小计
     */
    subtotal: boolean;
  }[];
  /**
   * 按聚合分区计算的总计
   */
  totals: {
    /**
     * 单位或币种等聚合分区
     */
    partition: Record<string, TypedValue>; /**
     * 固定类型的精确值列表
     */
    values: TypedValue[]; /**
     * 固定坐标的单元格列表
     */
    cells: {
      /**
       * 本次固定快照的服务端钻取坐标
       */
      coordinate: string; /**
       * 固定类型的精确值列表
       */
      values: TypedValue[];
    }[];
  }[];
  /**
   * 原始快照的数据行数
   */
  sourceRows: number;
}

/** 单据第二版打印配置。 */
export interface PrintDefinition {
  /**
   * 当前契约的orientation配置或状态
   */
  orientation: 'PORTRAIT' | 'LANDSCAPE';
  /**
   * 当前契约的groupBy配置或状态
   */
  groupBy: string[];
  /**
   * 当前契约的repeatFooter配置或状态
   */
  repeatFooter: boolean;
  /**
   * 当前契约的media配置或状态
   */
  media: {
    /**
     * 当前契约的kind配置或状态
     */
    kind: 'IMAGE' | 'QR' | 'CODE128';
    /**
     * 当前契约的assetVersionId配置或状态
     */
    assetVersionId: string | null;
    /**
     * 已批准的逻辑字段
     */
    field: string | null;
    /**
     * 当前契约的x配置或状态
     */
    x: number;
    /**
     * 当前契约的y配置或状态
     */
    y: number;
    /**
     * 布局宽度
     */
    width: number;
    /**
     * 布局高度
     */
    height: number;
    /**
     * 当前契约的repeat配置或状态
     */
    repeat: boolean;
  }[];
}
