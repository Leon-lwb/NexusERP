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
type Api<T> = Promise<{ data: T }>;
export const listReports = (): Api<ReportDefinition[]> => request({ url: '/report/definitions' });
export const reportDetail = (id: string): Api<ReportDetail> => request({ url: '/report/definitions/' + id });
export const publishedReport = (id: string): Api<PublishedReport> => request({ url: '/report/definitions/' + id + '/published' });
export const listCatalogs = (): Api<ReportCatalog[]> => request({ url: '/report/catalogs' });
export const createCatalog = (name: string, parentId: string | null): Api<ReportCatalog> =>
  request({ url: '/report/catalogs', method: 'post', data: { name, parentId } });
export const listDatasets = (): Api<DatasetView[]> => request({ url: '/report/datasets' });
export const listProviders = (): Api<DatasetDescriptor[]> => request({ url: '/report/datasets/providers' });
export const registerDataset = (code: string, version: number): Api<ReportDataset> =>
  request({ url: '/report/datasets', method: 'post', data: { code, version } });
export const datasetState = (id: string, state: string, expectedStatus: string): Api<void> =>
  request({ url: '/report/datasets/' + id + '/state', method: 'put', data: { state, expectedStatus } });
export const sampleDataset = (id: string, parameters: Record<string, string>): Api<DraftPreview> =>
  request({ url: '/report/datasets/' + id + '/sample', method: 'post', data: parameters });
export const bootstrapReports = (): Api<ReportDefinition[]> => request({ url: '/report/bootstrap', method: 'post' });
export const createReport = (dsl: ReportDsl, catalogId: string | null): Api<ReportDefinition> =>
  request({ url: '/report/definitions', method: 'post', data: { dsl, catalogId } });
export const saveReport = (id: string, revision: number, dsl: ReportDsl): Api<ReportDefinition> =>
  request({ url: '/report/definitions/' + id, method: 'put', data: { revision, dsl } });
export const validateDraft = (reportId: string | null, dsl: ReportDsl): Api<{ valid: boolean }> =>
  request({ url: '/report/definitions/validate', method: 'post', data: { reportId, dsl } });
export const previewDraft = (reportId: string | null, dsl: ReportDsl, parameters: Record<string, string>): Api<DraftPreview> =>
  request({ url: '/report/definitions/preview', method: 'post', data: { reportId, dsl, parameters } });
export const publishReport = (id: string, revision: number): Api<ReportVersion> =>
  request({ url: '/report/definitions/' + id + '/publish', method: 'post', data: { revision } });
export const listVersions = (id: string): Api<ReportVersion[]> => request({ url: '/report/definitions/' + id + '/versions' });
export const rollbackReport = (id: string, versionId: string, revision: number): Api<void> =>
  request({ url: '/report/definitions/' + id + '/rollback', method: 'post', data: { versionId, revision } });
export const reportState = (id: string, state: string, revision: number): Api<void> =>
  request({ url: '/report/definitions/' + id + '/state', method: 'put', data: { state, revision } });
export const listGrants = (type: string, id: string): Api<ResourceGrant[]> => request({ url: '/report/acl/' + type + '/' + id });
export const saveGrants = (type: string, id: string, grants: ResourceGrant[]): Api<void> =>
  request({ url: '/report/acl/' + type + '/' + id, method: 'put', data: grants });
export const listReportAudit = (): Api<Record<string, unknown>[]> => request({ url: '/report/audit' });
export const createRun = (reportId: string, mode: string, parameters: Record<string, string>): Api<ReportRun> =>
  request({ url: '/report/runtime/runs', method: 'post', data: { reportId, mode, parameters }, timeout: 120000 });
export const runPage = (id: string, page: number, size: number, sort?: string, descending = false): Api<PageData> =>
  request({ url: '/report/runtime/runs/' + id + '/page', params: { page, size, sort, descending } });
export const runLayout = (id: string): Api<RenderPlan> => request({ url: '/report/runtime/runs/' + id + '/layout' });
export const enqueueExport = (id: string, format: ExportFormat, requestKey: string): Api<ExportJob> =>
  request({ url: '/report/runtime/runs/' + id + '/exports', method: 'post', data: { format, requestKey } });
export const listExportJobs = (): Api<ExportJob[]> => request({ url: '/report/runtime/exports' });
export const cancelExport = (id: string): Api<void> => request({ url: '/report/runtime/exports/' + id + '/cancel', method: 'post' });
export const downloadExport = (id: string): Promise<Blob> =>
  request({ url: '/report/runtime/exports/' + id + '/file', responseType: 'blob', timeout: 120000 });

export const datasetVersion = (id: string): Api<import('@/types/report').DatasetVersionView> => request({ url: '/report/datasets/versions/' + id });
export const datasetVersionDiff = (from: string, target: string): Api<import('@/types/report').DatasetVersionDiff> =>
  request({ url: '/report/datasets/versions/' + from + '/diff', params: { target } });
export const reportEngines = (): Api<import('@/types/report').ReportEngineCapability[]> => request({ url: '/report/engines' });

export const exportTemplatePackage = (id: string): Api<unknown> => request({ url: '/report/templates/' + id + '/package' });
export const importTemplatePackage = (data: {
  templatePackage: unknown;
  datasetVersionId: string;
  code: string;
  title: string;
  catalogId: string | null;
}): Api<ReportDefinition> => request({ url: '/report/templates/import', method: 'post', data });
export const copyReport = (id: string, code: string, title: string, catalogId: string | null): Api<ReportDefinition> =>
  request({ url: '/report/templates/' + id + '/copy', method: 'post', data: { code, title, catalogId } });
export interface LookupBinding {
  datasetCode: string;
  providerVersion: number;
  parameter: string;
  label: string;
  permission: string;
}
export const reportLookupBindings = (version: string): Api<LookupBinding[]> => request({ url: '/report/lookups/' + version });
export const reportLookup = (
  version: string,
  parameter: string,
  keyword: string,
  page = 1
): Api<{ options: { value: string; label: string }[]; total: number }> =>
  request({ url: '/report/lookups/' + version + '/' + parameter, params: { keyword, page, size: 20 } });
export const businessReportTemplates = (datasetCode: string): Api<{ id: string; name: string }[]> =>
  request({ url: '/report/runtime/templates', params: { datasetCode } });

export const retryExport = (id: string, retryRound: number): Api<void> =>
  request({ url: '/report/runtime/exports/' + id + '/retry', method: 'post', data: { retryRound } });
