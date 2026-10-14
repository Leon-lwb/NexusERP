export type ValueType = 'STRING' | 'ID' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN' | 'DATE' | 'DATETIME';
export interface TypedValue {
  type: ValueType;
  value: string | null;
}
export type TypedRow = Record<string, TypedValue>;
export interface ReportStyle {
  fontSize: number;
  bold: boolean;
  align: 'LEFT' | 'CENTER' | 'RIGHT';
  color: string;
  background: string;
}
export interface ReportColumn {
  field: string;
  label: string;
  width: number;
  scale: number;
  expression: string | null;
  style: ReportStyle;
}
export interface ReportParameter {
  name: string;
  label: string;
  type: ValueType;
  required: boolean;
  defaultValue?: string | null;
}
export interface ReportPage {
  width: number;
  height: number;
  marginTop: number;
  marginRight: number;
  marginBottom: number;
  marginLeft: number;
  rowHeight: number;
}
export interface BandCell {
  column: number;
  colSpan: number;
  rowSpan: number;
  text: string | null;
  field: string | null;
  style: ReportStyle;
}
export interface BandRow {
  height: number;
  cells: BandCell[];
}
export interface ReportSummary {
  field: string | null;
  function: 'SUM' | 'COUNT' | 'COUNT_ROWS' | 'MIN' | 'MAX';
  groupBy: string[];
  label: string;
  scale: number;
}
export interface ReportDsl {
  schemaVersion: 1;
  code: string;
  title: string;
  kind: 'TABLE' | 'DOCUMENT';
  datasetCode: string;
  datasetVersionId: string;
  columns: ReportColumn[];
  parameters: ReportParameter[];
  page: ReportPage;
  headerRows: BandRow[];
  footerRows: BandRow[];
  summaries: ReportSummary[];
  repeatHeader: boolean;
}
export interface DatasetField {
  name: string;
  label: string;
  type: ValueType;
  permission: string | null;
  aggregationKeys: string[];
  aggregatable: boolean;
}
export interface DatasetDescriptor {
  code: string;
  name: string;
  version: number;
  permission: string;
  document: boolean;
  fields: DatasetField[];
  parameters: ReportParameter[];
  defaultColumns: string[];
}
export interface ReportDataset {
  id: string;
  code: string;
  name: string;
  provider: string;
  publishedVersion: string;
  status: 'ACTIVE' | 'DISABLED';
  owner: string;
}
export interface DatasetView {
  dataset: ReportDataset;
  descriptor: DatasetDescriptor;
  canManage: boolean;
}
export interface ReportDefinition {
  id: string;
  code: string;
  name: string;
  kind: 'TABLE' | 'DOCUMENT';
  catalogId: string | null;
  json: string | null;
  revision: number;
  publishedVersion: string | null;
  status: 'DRAFT' | 'PUBLISHED' | 'OFFLINE' | 'ARCHIVED';
  owner: string;
  updatedAt: string;
}
export interface ReportCatalog {
  id: string;
  parentId: string;
  name: string;
  owner: string;
}
export interface ReportVersion {
  id: string;
  reportId: string;
  number: number;
  schema: number;
  engine: string;
  json: string;
  checksum: string;
  datasetVersion: string;
  createdAt: string;
}
export interface ReportDetail {
  definition: ReportDefinition;
  dsl: ReportDsl;
}
export interface PublishedReport {
  versionId: string;
  number: number;
  dsl: ReportDsl;
}
export interface SummaryValue {
  label: string;
  group: Record<string, TypedValue>;
  value: TypedValue;
}
export interface RenderCell {
  band: string;
  rowIndex: number;
  column: number;
  colSpan: number;
  rowSpan: number;
  x: number;
  y: number;
  width: number;
  height: number;
  lines: string[];
  style: ReportStyle;
  value: TypedValue | null;
  scale: number;
}
export interface RenderPage {
  number: number;
  width: number;
  height: number;
  cells: RenderCell[];
}
export interface RenderPlan {
  schemaVersion: number;
  rowCount: number;
  fontName: string;
  pages: RenderPage[];
  columns: ReportColumn[];
  summaries: SummaryValue[];
}
export interface DraftPreview {
  rows: TypedRow[];
  total: number;
  dataTime: string;
  summaries: SummaryValue[];
  plan: RenderPlan | null;
}
export interface PageData {
  columns: ReportColumn[];
  rows: TypedRow[];
  total: number;
  dataTime: string;
  mode: 'LIVE' | 'SNAPSHOT';
  summaries: SummaryValue[];
}
export interface ReportRun {
  id: string;
  reportId: string;
  version: string;
  mode: 'LIVE' | 'SNAPSHOT';
  state: string;
  rows: number;
  dataTime: string;
  expires: string;
  error: string | null;
}
export type ExportFormat = 'PDF' | 'XLSX_DATA' | 'XLSX_LAYOUT';
export interface ExportJob {
  phase?: string;
  processedRows?: number;
  totalRows?: number | null;
  retryRound?: number;
  heartbeat?: string | null;
  canRetry?: boolean;
  id: string;
  runId: string;
  format: ExportFormat;
  state: 'QUEUED' | 'RUNNING' | 'CANCEL_REQUESTED' | 'CANCELLED' | 'SUCCEEDED' | 'FAILED' | 'EXPIRED';
  attempts: number;
  bytes: number | null;
  expires: string;
  error: string | null;
}
export interface ResourceGrant {
  subjectType: 'USER' | 'ROLE' | 'DEPT';
  subjectId: string;
  action: 'VIEW' | 'EDIT' | 'PUBLISH' | 'EXPORT' | 'MANAGE';
}

export interface DatasetVersionView {
  id: string;
  datasetId: string;
  code: string;
  number: number;
  current: boolean;
  descriptor: DatasetDescriptor;
}
export interface DatasetVersionDiff {
  from: string;
  to: string;
  breaking: boolean;
  changes: { category: string; name: string; kind: string; breaking: boolean }[];
}
export interface ReportEngineCapability {
  schemaVersion: number;
  engineVersion: string;
  features: string[];
  enabled: boolean;
  defaultForDraft: boolean;
}
