import request from '@/utils/request';
import { engineeringId, normalizeEngineeringDevice } from '@/views/ai/engineering/model';
import { rememberArtifactFileName } from '@/views/ai/engineering/artifact-model';

/** 工程协议版本；所有接口均由 Nexus 后端鉴权。 */
export const ENGINEERING_PROTOCOL = 'nexus-engineering/1' as const;
export type EngineeringState = 'CREATED' | 'WAITING_DEVICE' | 'RUNNING' | 'SUCCEEDED' | 'PARTIAL_SUCCESS' | 'FAILED' | 'CANCELED' | 'EXPIRED';
export type EngineeringStage = 'CHECKING' | 'PREPARING' | 'GENERATING' | 'CHECKING_DRC' | 'EXPORTING' | 'UPLOADING' | 'FINALIZING';
export type EngineeringAction = 'CHECK_CONNECTION' | 'OPEN_PROJECT' | 'CREATE_PCB_DRAFT' | 'EXPORT_ARTIFACTS' | 'RUN_DRC' | 'SUBMIT_DRAFT';
export type EngineeringArtifactType =
  | 'PCB_SOURCE'
  | 'PCB_PREVIEW_2D'
  | 'PCB_BOM'
  | 'PCB_NETLIST'
  | 'PCB_DRC_REPORT'
  | 'PCB_MODEL_OBJ'
  | 'PCB_MODEL_MTL'
  | 'PCB_MODEL_TEXTURE';
export type DrcStatus = 'PASS' | 'VIOLATIONS' | 'UNAVAILABLE' | 'NOT_RUN';

/** 普通浏览器接口沿用 R 包装，不把响应正文直接当领域对象。 */
export interface EngineeringResponse<T> {
  code: number;
  msg?: string;
  data: T;
  traceId?: string;
}
export interface EngineeringPage<T> {
  code: number;
  msg?: string;
  rows: T[];
  total: number;
}

/** 浏览器设备视图不包含连接器访问或刷新凭证。 */
export interface EngineeringDevice {
  deviceId: string;
  deviceName?: string;
  status?: string;
  online: boolean;
  /** 若后端未给在线布尔值，则明确显示近期心跳/心跳超时。 */
  onlineLabel?: string;
  editorAvailable?: boolean;
  busyTaskId?: string;
  templateHashes?: Record<string, string>;
  connectorVersion?: string;
  cliVersion?: string;
  editorVersion?: string;
  capabilities: string[];
  /** 连接器实际传输，可为 CLI 兼容模式，不推断官方 MCP 可用。 */
  transportMode?: string;
  diagnosticCode?: string;
  diagnosticMessage?: string;
  lastHeartbeatAt?: string;
  createTime?: string;
}
export interface EngineeringPairingCode {
  pairingCode: string;
  expiresAt: string;
}
export interface DrcSummary {
  status: DrcStatus;
  errorCount?: number;
  warningCount?: number;
  truncated?: boolean;
  message?: string;
}
/** 服务端归一化后的 BOM，数量已由报告校验器保留为十进制文本。 */
export interface EngineeringBomRow {
  designator: string | null;
  value: string | null;
  footprint: string | null;
  manufacturerPartNumber: string | null;
  supplierPartNumber: string | null;
  quantity: string;
}
export interface EngineeringBomReport {
  items: EngineeringBomRow[];
  total: number;
  truncated: boolean;
}
/** DRC 条目保留服务端已校验的原始字段，展示层只选取规范文本。 */
export interface EngineeringDrcReport extends DrcSummary {
  issues: Record<string, unknown>[];
  truncated: boolean;
}
export interface EngineeringArtifact {
  artifactId: string;
  taskId?: string;
  type: EngineeringArtifactType;
  groupId?: string;
  relativePath: string;
  fileName?: string;
  size: number;
  sha256?: string;
  contentType?: string;
  contentPath?: string;
}
/** 服务端按组给出的材质降级信息；旧清单可不提供，由组内引用判断。 */
export interface EngineeringModelGroup {
  groupId: string;
  status: 'READY' | 'NO_MATERIAL' | 'DEGRADED' | 'MISSING_GEOMETRY';
  uniformMaterial: boolean;
  missingPaths: string[];
  truncated: boolean;
}
export interface EngineeringManifest {
  protocolVersion: typeof ENGINEERING_PROTOCOL;
  taskId: string;
  revision: number | string;
  state: EngineeringState;
  drc?: DrcSummary;
  artifacts: EngineeringArtifact[];
  missingTypes: EngineeringArtifactType[];
  modelGroups?: EngineeringModelGroup[];
  canManufacture: false;
}
/** 业务尺寸以十进制字符串传输，不在浏览器进行工程数值计算。 */
export interface EngineeringParameters {
  boardWidthMm?: string;
  boardHeightMm?: string;
  layers?: number;
  optionalModules?: string[];
  [key: string]: string | number | string[] | boolean | undefined;
}
export interface EngineeringTaskCommand {
  protocolVersion?: typeof ENGINEERING_PROTOCOL;
  deviceId: string;
  action: EngineeringAction;
  idempotencyKey: string;
  sessionId?: string;
  templateId?: string;
  templateVersion?: string;
  templateSha256?: string;
  parentTaskId?: string;
  parameters?: EngineeringParameters;
}
export interface EngineeringTask extends Omit<EngineeringTaskCommand, 'idempotencyKey'> {
  taskId: string;
  state: EngineeringState;
  stage?: EngineeringStage;
  revision: number | string;
  deviceName?: string;
  requestId?: string;
  missingTypes?: EngineeringArtifactType[];
  drc?: DrcSummary;
  cancelRequested?: boolean;
  errorCode?: string;
  errorMessage?: string;
  traceId?: string;
  createdAt?: string;
  updatedAt?: string;
  createTime?: string;
  completedAt?: string;
  attempt?: number;
  events?: { sequence: number; stage?: EngineeringStage; message?: string; revision: number | string }[];
}
export interface EngineeringTaskQuery {
  pageNum?: number;
  pageSize?: number;
  state?: EngineeringState;
  deviceId?: string;
  sessionId?: string;
}
/** 与后端 ParameterRule 对齐，不能用全局层数选项替代模板声明。 */
export interface EngineeringTemplateParameterRule {
  required: boolean;
  min?: string | number | null;
  max?: string | number | null;
  defaultValue?: string | number | boolean | string[] | null;
  enumValues?: number[];
}
export interface EngineeringTemplate {
  templateId: string;
  templateName?: string;
  title?: string;
  templateVersion: string;
  templateSha256: string;
  parameters?: Record<string, EngineeringTemplateParameterRule>;
  optionalModules?: string[];
  parameterConstraints?: Record<string, unknown>;
}

/** 读取当前账号已配对设备。 */
export const listEngineeringDevices = async (signal?: AbortSignal): Promise<EngineeringResponse<EngineeringDevice[]>> => {
  const result: EngineeringResponse<unknown[]> = await request.get('/ai/engineering/devices', { signal });
  return { ...result, data: (result.data || []).map((device) => normalizeEngineeringDevice(device)) };
};
/** 创建五分钟有效的一次性配对码。 */
export const createEngineeringPairingCode = (): Promise<EngineeringResponse<EngineeringPairingCode>> =>
  request.post('/ai/engineering/devices/pairing-code');
/** 撤销设备及其后续回调权限。 */
export const unpairEngineeringDevice = (deviceId: string): Promise<EngineeringResponse<void>> =>
  request.delete(`/ai/engineering/devices/${engineeringId(deviceId)}`);
/** 只读取服务端允许的模板及其摘要。 */
export const listEngineeringTemplates = (signal?: AbortSignal): Promise<EngineeringResponse<EngineeringTemplate[]>> =>
  request.get('/ai/engineering/templates', { signal });
/** 创建显式动作，幂等键由同一次用户意图持有。 */
export const createEngineeringTask = (command: EngineeringTaskCommand): Promise<EngineeringResponse<EngineeringTask>> =>
  request.post('/ai/engineering/tasks', { ...command, deviceId: engineeringId(command.deviceId), protocolVersion: ENGINEERING_PROTOCOL });
/** 查询可恢复的分页任务，包括按会话过滤的历史任务。 */
export const listEngineeringTasks = async (params: EngineeringTaskQuery, signal?: AbortSignal): Promise<EngineeringPage<EngineeringTask>> => {
  const query = {
    ...params,
    sessionId: params.sessionId ? engineeringId(params.sessionId) : undefined,
    deviceId: params.deviceId ? engineeringId(params.deviceId) : undefined
  };
  if (!query.state && !query.deviceId) return request.get('/ai/engineering/tasks', { params: query, signal });
  // v1 后端仅保证会话筛选。按用户授权分页读取后再过滤，避免把未支持的参数当成已生效。
  const rows = new Map<string, EngineeringTask>();
  let pageNum = 1;
  let total = 0;
  let read = 0;
  do {
    signal?.throwIfAborted();
    const page: EngineeringPage<EngineeringTask> = await request.get('/ai/engineering/tasks', {
      params: { ...query, pageNum, pageSize: 100 },
      signal
    });
    signal?.throwIfAborted();
    for (const task of page.rows || []) rows.set(task.taskId, task);
    total = page.total || 0;
    read += page.rows?.length || 0;
    pageNum++;
    if (!page.rows?.length) break;
  } while (read < total);
  const filtered = [...rows.values()].filter(
    (task) => (!query.state || task.state === query.state) && (!query.deviceId || task.deviceId === query.deviceId)
  );
  const offset = ((params.pageNum || 1) - 1) * (params.pageSize || 10);
  return { code: 200, rows: filtered.slice(offset, offset + (params.pageSize || 10)), total: filtered.length };
};
/** 读取实际状态；雪花 ID 不转数字。 */
export const getEngineeringTask = (taskId: string, signal?: AbortSignal): Promise<EngineeringResponse<EngineeringTask>> =>
  request.get(`/ai/engineering/tasks/${engineeringId(taskId)}`, { signal });
/** 请求取消，运行中的取消以服务端下一次状态为准。 */
export const cancelEngineeringTask = (taskId: string): Promise<EngineeringResponse<EngineeringTask>> =>
  request.post(`/ai/engineering/tasks/${engineeringId(taskId)}/cancel`);
/** 只读取已确认的私有产物清单。 */
export const getEngineeringManifest = (taskId: string, signal?: AbortSignal): Promise<EngineeringResponse<EngineeringManifest>> =>
  request.get(`/ai/engineering/tasks/${engineeringId(taskId)}/manifest`, { signal });
/** 展示只读取有界规范报告；原始文件由 content 接口供下载。 */
export const getEngineeringArtifactReport = (
  artifactId: string,
  signal?: AbortSignal
): Promise<EngineeringResponse<EngineeringBomReport | EngineeringDrcReport>> =>
  request.get(`/ai/engineering/artifacts/${engineeringId(artifactId)}/report`, { signal });
/** 经现有认证拦截器读取 Blob；不请求清单提供的任意 URL。 */
export const getEngineeringArtifactContent = (artifactId: string, signal?: AbortSignal): Promise<Blob> =>
  request.get(`/ai/engineering/artifacts/${engineeringId(artifactId)}/content`, {
    signal,
    responseType: 'blob',
    transformResponse: [
      (data: Blob, headers) => {
        const disposition = String(headers?.['content-disposition'] || '');
        const encoded = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(disposition)?.[1];
        const plain = /filename\s*=\s*"([^"]+)"|filename\s*=\s*([^;]+)/i.exec(disposition);
        let name = plain?.[1] || plain?.[2];
        try {
          if (encoded) name = decodeURIComponent(encoded);
        } catch {
          /* 编码异常时保留清单文件名。 */
        }
        if (data instanceof Blob && name) rememberArtifactFileName(data, name);
        return data;
      }
    ]
  });
