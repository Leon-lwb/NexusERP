import request from '@/utils/request';
import type {
  ReportDefinition,
  ReportDetail,
  PublishedReport,
  ReportCatalog,
  ReportVersion,
  DatasetView,
  DatasetDescriptor,
  ReportDataset,
  ReportDsl,
  DraftPreview,
  PageData,
  RenderPlan,
  ReportRun,
  ExportJob,
  ExportFormat,
  ResourceGrant
} from '@/types/report';
/** 声明Api结构，保留服务端精确值和固定版本约束。 */
type Api<T> = Promise<{
  /** 保存精确数据结果。 */
  data: T;
}>;
/** 提供报表 `listReports` 操作入口。 */
export const listReports = (): Api<ReportDefinition[]> => request({ url: '/report/definitions' });
/** 提供报表 `reportDetail` 操作入口。 */
export const reportDetail = (id: string): Api<ReportDetail> => request({ url: '/report/definitions/' + id });
/** 提供报表 `publishedReport` 操作入口。 */
export const publishedReport = (id: string): Api<PublishedReport> => request({ url: '/report/definitions/' + id + '/published' });
/** 提供报表 `listCatalogs` 操作入口。 */
export const listCatalogs = (): Api<ReportCatalog[]> => request({ url: '/report/catalogs' });
/** 提供报表 `createCatalog` 操作入口。 */
export const createCatalog = (name: string, parentId: string | null): Api<ReportCatalog> =>
  request({ url: '/report/catalogs', method: 'post', data: { name, parentId } });
/** 提供报表 `listDatasets` 操作入口。 */
export const listDatasets = (): Api<DatasetView[]> => request({ url: '/report/datasets' });
/** 提供报表 `listProviders` 操作入口。 */
export const listProviders = (): Api<DatasetDescriptor[]> => request({ url: '/report/datasets/providers' });
/** 登记已安装的代码契约版本。 */
export const registerDataset = (code: string, version: number): Api<ReportDataset> =>
  request({ url: '/report/datasets', method: 'post', data: { code, version } });
/** 提供报表 `datasetState` 操作入口。 */
export const datasetState = (id: string, state: string, expectedStatus: string): Api<void> =>
  request({ url: '/report/datasets/' + id + '/state', method: 'put', data: { state, expectedStatus } });
/** 提供报表 `sampleDataset` 操作入口。 */
export const sampleDataset = (id: string, parameters: Record<string, string>): Api<DraftPreview> =>
  request({ url: '/report/datasets/' + id + '/sample', method: 'post', data: parameters });
/** 提供报表 `bootstrapReports` 操作入口。 */
export const bootstrapReports = (): Api<ReportDefinition[]> => request({ url: '/report/bootstrap', method: 'post' });
/** 提供报表 `createReport` 操作入口。 */
export const createReport = (dsl: ReportDsl, catalogId: string | null): Api<ReportDefinition> =>
  request({ url: '/report/definitions', method: 'post', data: { dsl, catalogId } });
/** 提供报表 `saveReport` 操作入口。 */
export const saveReport = (id: string, revision: number, dsl: ReportDsl): Api<ReportDefinition> =>
  request({ url: '/report/definitions/' + id, method: 'put', data: { revision, dsl } });
/** 提供报表 `validateDraft` 操作入口。 */
export const validateDraft = (
  reportId: string | null,
  dsl: ReportDsl
): Api<{
  /** 保存当前 `valid` 值。 */
  valid: boolean;
}> => request({ url: '/report/definitions/validate', method: 'post', data: { reportId, dsl } });
/** 提供报表 `previewDraft` 操作入口。 */
export const previewDraft = (reportId: string | null, dsl: ReportDsl, parameters: Record<string, string>): Api<DraftPreview> =>
  request({ url: '/report/definitions/preview', method: 'post', data: { reportId, dsl, parameters } });
/** 提供报表 `publishReport` 操作入口。 */
export const publishReport = (id: string, revision: number): Api<ReportVersion> =>
  request({ url: '/report/definitions/' + id + '/publish', method: 'post', data: { revision } });
/** 提供报表 `listVersions` 操作入口。 */
export const listVersions = (id: string): Api<ReportVersion[]> => request({ url: '/report/definitions/' + id + '/versions' });
/** 提供报表 `rollbackReport` 操作入口。 */
export const rollbackReport = (id: string, versionId: string, revision: number): Api<void> =>
  request({ url: '/report/definitions/' + id + '/rollback', method: 'post', data: { versionId, revision } });
/** 提供报表 `reportState` 操作入口。 */
export const reportState = (id: string, state: string, revision: number): Api<void> =>
  request({ url: '/report/definitions/' + id + '/state', method: 'put', data: { state, revision } });
/** 提供报表 `listGrants` 操作入口。 */
export const listGrants = (type: string, id: string): Api<ResourceGrant[]> => request({ url: '/report/acl/' + type + '/' + id });
/** 提供报表 `saveGrants` 操作入口。 */
export const saveGrants = (type: string, id: string, grants: ResourceGrant[]): Api<void> =>
  request({ url: '/report/acl/' + type + '/' + id, method: 'put', data: grants });
/** 提供报表 `listReportAudit` 操作入口。 */
export const listReportAudit = (): Api<Record<string, unknown>[]> => request({ url: '/report/audit' });
/** 提供报表 `createRun` 操作入口。 */
export const createRun = (reportId: string, mode: string, parameters: Record<string, string>, expectedVersionId?: string): Api<ReportRun> =>
  request({ url: '/report/runtime/runs', method: 'post', data: { reportId, mode, parameters, expectedVersionId }, timeout: 120000 });
/** 重新鉴权后读取既有运行，完整页与抽屉共享固定快照。 */
export const runStatus = (id: string): Api<ReportRun> => request({ url: '/report/runtime/runs/' + id });
/** 提供报表 `runPage` 操作入口。 */
export const runPage = (id: string, page: number, size: number, sort?: string, descending = false): Api<PageData> =>
  request({ url: '/report/runtime/runs/' + id + '/page', params: { page, size, sort, descending } });
/** 提供报表 `runLayout` 操作入口。 */
export const runLayout = (id: string): Api<RenderPlan> => request({ url: '/report/runtime/runs/' + id + '/layout' });
/** 提供报表 `enqueueExport` 操作入口。 */
export const enqueueExport = (id: string, format: ExportFormat, requestKey: string): Api<ExportJob> =>
  request({ url: '/report/runtime/runs/' + id + '/exports', method: 'post', data: { format, requestKey } });
/** 提供报表 `listExportJobs` 操作入口。 */
export const listExportJobs = (): Api<ExportJob[]> => request({ url: '/report/runtime/exports' });
/** 提供报表 `cancelExport` 操作入口。 */
export const cancelExport = (id: string): Api<void> => request({ url: '/report/runtime/exports/' + id + '/cancel', method: 'post' });
/** 提供报表 `downloadExport` 操作入口。 */
export const downloadExport = (id: string): Promise<Blob> =>
  request({ url: '/report/runtime/exports/' + id + '/file', responseType: 'blob', timeout: 120000 });

/** 读取当前租户的固定数据集契约。 */
export const datasetVersion = (id: string): Api<import('@/types/report').DatasetVersionView> => request({ url: '/report/datasets/versions/' + id });
/** 提供报表 `datasetVersionDiff` 操作入口。 */
export const datasetVersionDiff = (from: string, target: string): Api<import('@/types/report').DatasetVersionDiff> =>
  request({ url: '/report/datasets/versions/' + from + '/diff', params: { target } });
/** 提供报表 `reportEngines` 操作入口。 */
export const reportEngines = (): Api<import('@/types/report').ReportEngineCapability[]> => request({ url: '/report/engines' });

/** 提供报表 `exportTemplatePackage` 操作入口。 */
export const exportTemplatePackage = (id: string): Api<unknown> => request({ url: '/report/templates/' + id + '/package' });
/** 提供报表 `importTemplatePackage` 操作入口。 */
export const importTemplatePackage = (data: {
  /** 保存当前 `templatePackage` 值。 */
  templatePackage: unknown;
  /** 保存当前 `datasetVersionId` 值。 */
  datasetVersionId: string;
  /** 保存业务编码。 */
  code: string;
  /** 保存当前 `title` 值。 */
  title: string;
  /** 保存当前 `catalogId` 值。 */
  catalogId: string | null;
}): Api<ReportDefinition> => request({ url: '/report/templates/import', method: 'post', data });
/** 提供报表 `copyReport` 操作入口。 */
export const copyReport = (id: string, code: string, title: string, catalogId: string | null): Api<ReportDefinition> =>
  request({ url: '/report/templates/' + id + '/copy', method: 'post', data: { code, title, catalogId } });
/** 声明选择器绑定结构，保留服务端精确值和固定版本约束。 */
export interface LookupBinding {
  /** 保存当前 `datasetCode` 值。 */
  datasetCode: string;
  /** 保存当前 `providerVersion` 值。 */
  providerVersion: number;
  /** 保存当前 `parameter` 值。 */
  parameter: string;
  /** 保存展示标题。 */
  label: string;
  /** 保存待核对权限。 */
  permission: string;
}
/** 业务选择器的人类可读标签与完整字符串值。 */
export interface LookupOption {
  /** 完整ID或业务编码，不经过Number转换。 */
  value: string;
  /** 经服务端权限复核的业务编码与名称。 */
  label: string;
}
/** 按固定版本及参数回显已传入的ID，服务端对对象重新鉴权。 */
export const reportLookupSelected = (version: string, parameter: string, value: string): Api<LookupOption | null> =>
  request({ url: '/report/lookups/' + version + '/' + parameter + '/selected', params: { value } });

/** 提供报表 `reportLookupBindings` 操作入口。 */
export const reportLookupBindings = (version: string): Api<LookupBinding[]> => request({ url: '/report/lookups/' + version });
/** 提供报表 `reportLookup` 操作入口。 */
export const reportLookup = (
  version: string,
  parameter: string,
  keyword: string,
  page = 1
): Api<{
  /** 保存当前 `options` 值。 */
  options: {
    /** 保存当前 `value` 值。 */
    value: string;
    /** 保存展示标题。 */
    label: string;
  }[];
  /** 保存当前 `total` 值。 */
  total: number;
}> => request({ url: '/report/lookups/' + version + '/' + parameter, params: { keyword, page, size: 20 } });
/** 业务入口的已发布模板，版本标识始终保留完整字符串。 */
export interface BusinessReportTemplate {
  /** 报表定义标识。 */
  id: string;
  /** 已授权的模板名称。 */
  name: string;
  /** 模板类型，业务打印入口仅接受DOCUMENT。 */
  kind: string;
  /** 不可变的已发布版本标识。 */
  versionId: string;
}
/** 查询当前业务数据集下可访问的已发布模板。 */
export const businessReportTemplates = (datasetCode: string): Api<BusinessReportTemplate[]> =>
  request({ url: '/report/runtime/templates', params: { datasetCode } });

/** 提供报表 `retryExport` 操作入口。 */
export const retryExport = (id: string, expectedRound: number): Api<void> =>
  request({ url: '/report/runtime/exports/' + id + '/retry', method: 'post', data: { retryRound: expectedRound } });

/** 获取固定快照交叉结果，保留精确数值和钻取坐标。 */
export const runPivot = (id: string): Api<import('@/types/report').PivotResult> => request({ url: '/report/runtime/runs/' + id + '/pivot' });
/** 服务端验证同一快照内的坐标后返回原始记录。 */
export const runDrill = (
  id: string,
  coordinate: string,
  offset: number,
  size: number
): Api<{
  /**
   * 精确类型的数据行
   */
  rows: import('@/types/report').TypedRow[]; /**
   * 当前契约的total配置或状态
   */
  total: number;
}> => request({ url: '/report/runtime/runs/' + id + '/drill', params: { coordinate, offset, size } });
