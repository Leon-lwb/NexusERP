import type { DrcStatus, EngineeringArtifact, EngineeringDevice, EngineeringStage, EngineeringState, EngineeringTask } from '@/api/ai/engineering';

/** 工程标识只能采用后端提供的正整数文本，拒绝已失真的数字。 */
export function engineeringId(value: unknown): string {
  if (typeof value !== 'string' || !/^[1-9]\d{0,18}$/.test(value) || BigInt(value) > 9223372036854775807n)
    throw new Error('工程标识无效，请从任务列表重新进入');
  return value;
}
/** 请求令牌同时绑定任务和代数，切换任务后旧响应不可提交。 */
export class EngineeringRequestGate {
  private generation = 0;
  private taskId = '';
  begin(taskId: string) {
    this.taskId = engineeringId(taskId);
    return { taskId, generation: ++this.generation };
  }
  isCurrent(token: { taskId: string; generation: number }): boolean {
    return token.taskId === this.taskId && token.generation === this.generation;
  }
  invalidate(): void {
    this.taskId = '';
    this.generation++;
  }
}
export const ENGINEERING_STATES: Record<EngineeringState, { label: string; type: 'info' | 'primary' | 'success' | 'warning' | 'danger' }> = {
  CREATED: { label: '已创建', type: 'info' },
  WAITING_DEVICE: { label: '等待设备', type: 'warning' },
  RUNNING: { label: '执行中', type: 'primary' },
  SUCCEEDED: { label: '已完成', type: 'success' },
  PARTIAL_SUCCESS: { label: '部分成功', type: 'warning' },
  FAILED: { label: '失败', type: 'danger' },
  CANCELED: { label: '已取消', type: 'info' },
  EXPIRED: { label: '已过期', type: 'info' }
};
export const ENGINEERING_STAGES: Record<EngineeringStage, string> = {
  CHECKING: '检查连接',
  PREPARING: '准备模板',
  GENERATING: '生成草稿',
  CHECKING_DRC: '检查设计规则',
  EXPORTING: '导出产物',
  UPLOADING: '上传归档',
  FINALIZING: '确认结果'
};
export const ARTIFACT_LABELS: Record<string, string> = {
  PCB_SOURCE: '源工程',
  PCB_PREVIEW_2D: '线路预览',
  PCB_BOM: 'BOM',
  PCB_NETLIST: '网表',
  PCB_DRC_REPORT: 'DRC 报告',
  PCB_MODEL_OBJ: 'OBJ 模型',
  PCB_MODEL_MTL: '模型材质',
  PCB_MODEL_TEXTURE: '模型纹理'
};
/** 只对已知的非终态继续轮询。 */
export function shouldPoll(state?: string): boolean {
  return ['CREATED', 'WAITING_DEVICE', 'RUNNING'].includes(state || '');
}
/** 下载权限未授予时不产生下载入口。 */
export function downloadableArtifacts(artifacts: EngineeringArtifact[] = [], allowed: boolean): EngineeringArtifact[] {
  return allowed ? artifacts : [];
}
/** 空报告和不可用检查均不解释为通过。 */
export function drcPresentation(status?: DrcStatus): { label: string; type: 'success' | 'danger' | 'warning' | 'info' } {
  return (
    (
      {
        PASS: { label: 'DRC 通过', type: 'success' },
        VIOLATIONS: { label: 'DRC 存在违规', type: 'danger' },
        UNAVAILABLE: { label: 'DRC 不可用', type: 'warning' },
        NOT_RUN: { label: 'DRC 未运行', type: 'info' }
      } as const
    )[status || 'NOT_RUN'] || { label: 'DRC 状态未知', type: 'warning' }
  );
}
/** 将 HTTP、Blob 信封错误转换为可读状态，保留追踪号供核对。 */
export function engineeringError(error: unknown): { status?: number; message: string; traceId?: string } {
  const e = error as {
    status?: number;
    response?: { status?: number; data?: { msg?: string; traceId?: string } };
    message?: string;
    traceId?: string;
  };
  const status = e?.status || e?.response?.status;
  const fixed: Record<number, string> = {
    401: '登录已失效，请重新登录后读取任务',
    403: '没有此任务或产物的访问权限',
    404: '任务或产物不存在，可能已被清理',
    409: '任务状态已变化，请刷新后核对',
    413: '产物超过允许的大小'
  };
  return {
    status,
    message: fixed[status || 0] || e?.response?.data?.msg || e?.message || '工程服务暂时无法访问，请检查网络后重试',
    traceId: e?.traceId || e?.response?.data?.traceId
  };
}
/** 显示用户本地时间，兼容缺失时间。 */
export function engineeringTime(value?: string): string {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

type PollScheduler = { setTimeout: (callback: () => void, ms: number) => unknown; clearTimeout: (timer: unknown) => void };
/** 无重叠轮询：页面隐藏/卸载即中止，权限拒绝及终态停止，读失败最多重试五次。 */
export class EngineeringPoller {
  private active = false;
  private visible = true;
  private generation = 0;
  private failures = 0;
  private timer?: unknown;
  private controller?: AbortController;
  private scheduler: PollScheduler;
  constructor(private options: { load: (signal: AbortSignal) => Promise<boolean>; onError?: (error: unknown) => void; scheduler?: PollScheduler }) {
    this.scheduler = options.scheduler || {
      setTimeout: (fn, ms) => globalThis.setTimeout(fn, ms),
      clearTimeout: (id) => globalThis.clearTimeout(id as ReturnType<typeof setTimeout>)
    };
  }
  async start(): Promise<void> {
    this.stop();
    this.active = true;
    this.failures = 0;
    await this.refresh();
  }
  private pause(): void {
    this.generation++;
    if (this.timer !== undefined) this.scheduler.clearTimeout(this.timer);
    this.timer = undefined;
    this.controller?.abort();
  }
  stop(): void {
    this.active = false;
    this.pause();
  }
  async setVisible(visible: boolean): Promise<void> {
    this.visible = visible;
    this.pause();
    if (visible && this.active) await this.refresh();
  }
  async refresh(): Promise<void> {
    if (!this.active || !this.visible) return;
    this.pause();
    const generation = this.generation;
    const controller = new AbortController();
    this.controller = controller;
    let delay = 3000;
    let again = false;
    try {
      again = await this.options.load(controller.signal);
      this.failures = 0;
    } catch (error) {
      if (controller.signal.aborted || generation !== this.generation) return;
      this.options.onError?.(error);
      const status = engineeringError(error).status;
      again = ![401, 403, 404].includes(status || 0) && ++this.failures <= 5;
      const baseDelay = Math.min(16000, 1000 * 2 ** (this.failures - 1));
      const retryAfter = (error as { response?: { headers?: Record<string, string> } })?.response?.headers?.['retry-after'];
      const retryMs = retryAfter ? (/^\d+(\.\d+)?$/.test(retryAfter) ? Number(retryAfter) * 1000 : Date.parse(retryAfter) - Date.now()) : 0;
      delay = Math.max(Math.round(baseDelay * (1 + Math.random() * 0.2)), Number.isFinite(retryMs) ? retryMs : 0);
    }
    if (again && this.active && this.visible && !controller.signal.aborted && generation === this.generation)
      this.timer = this.scheduler.setTimeout(() => this.refresh(), delay);
  }
}

/** 只有显式字符串 true 才启用工程入口，缺省保持关闭。 */
export function engineeringFeatureEnabled(value: unknown): boolean {
  return value === 'true';
}
/** 传输诊断忠实展示上报信息，不将 CLI 兼容适配描述为官方 MCP 可用。 */
export function engineeringTransport(device: { transportMode?: string; diagnosticCode?: string; diagnosticMessage?: string }): {
  label: string;
  notice: string;
} {
  return {
    label: device.transportMode || '未上报',
    notice: [device.diagnosticCode, device.diagnosticMessage, 'CLI 与兼容适配能力以设备上报为准，未上报的 MCP 能力不视为可用']
      .filter(Boolean)
      .join('；')
  };
}

/** 适配实际后端 DeviceView：心跳对象嵌在 capabilities 中，凭证字段永不透传。 */
export function normalizeEngineeringDevice(value: unknown, now = Date.now()): EngineeringDevice {
  if (!value || typeof value !== 'object') throw new Error('设备响应格式无效');
  const row = value as Record<string, unknown>;
  const heartbeat =
    row.capabilities && typeof row.capabilities === 'object' && !Array.isArray(row.capabilities)
      ? (row.capabilities as Record<string, unknown>)
      : row;
  const text = (input: unknown): string | undefined => (typeof input === 'string' ? input : undefined);
  const lastHeartbeatAt = text(row.lastHeartbeatAt) || text(row.lastHeartbeat);
  const last = Date.parse(lastHeartbeatAt || '');
  const recent = Number.isFinite(last) && now - last >= -30000 && now - last <= 45000 && row.status !== 'REVOKED';
  const online = typeof row.online === 'boolean' ? row.online : recent;
  const capabilities = heartbeat.capabilities;
  return {
    deviceId: engineeringId(row.deviceId),
    deviceName: text(row.deviceName),
    status: text(row.status),
    online,
    onlineLabel:
      row.status === 'REVOKED'
        ? '已解绑'
        : typeof row.online === 'boolean'
          ? online
            ? '在线'
            : '离线'
          : !lastHeartbeatAt
            ? '尚无心跳'
            : recent
              ? '近期心跳'
              : '心跳超时',
    capabilities: Array.isArray(capabilities) ? capabilities.filter((capability): capability is string => typeof capability === 'string') : [],
    connectorVersion: text(heartbeat.connectorVersion),
    cliVersion: text(heartbeat.cliVersion),
    editorVersion: text(heartbeat.editorVersion),
    transportMode: text(heartbeat.transportMode) || text(row.transportMode),
    diagnosticCode: text(heartbeat.diagnosticCode) || text(row.diagnosticCode),
    diagnosticMessage: text(heartbeat.diagnosticMessage) || text(row.diagnosticMessage),
    editorAvailable: typeof heartbeat.editorAvailable === 'boolean' ? heartbeat.editorAvailable : undefined,
    busyTaskId: typeof row.busyTaskId === 'string' ? engineeringId(row.busyTaskId) : undefined,
    templateHashes:
      heartbeat.templateHashes && typeof heartbeat.templateHashes === 'object'
        ? Object.fromEntries(
            Object.entries(heartbeat.templateHashes).filter(
              (entry): entry is [string, string] => typeof entry[1] === 'string' && /^[a-fA-F0-9]{64}$/.test(entry[1])
            )
          )
        : undefined,
    lastHeartbeatAt,
    createTime: text(row.createTime) || text(row.createdAt)
  };
}

/** 动作名称来自受控协议，连接检查与文件生成分别展示。 */
export function engineeringActionLabel(action?: string): string {
  const labels: Record<string, string> = {
    CHECK_CONNECTION: '连接检查',
    OPEN_PROJECT: '打开工程',
    CREATE_PCB_DRAFT: 'PCB 草稿生成',
    EXPORT_ARTIFACTS: '导出产物',
    RUN_DRC: 'DRC 检查',
    SUBMIT_DRAFT: '草稿归档'
  };
  return labels[action || ''] || '工程任务';
}
export interface EngineeringResultPresentation {
  type: 'success' | 'warning' | 'info' | 'error';
  title: string;
  description: string;
}
/** 历史任务只根据已保存的终态展示结论，不补造客户端版本或当前设备事实。 */
export function connectionCheckPresentation(
  task: Pick<EngineeringTask, 'state' | 'connectionDiagnostic' | 'errorMessage'>
): EngineeringResultPresentation {
  const diagnostic = task.connectionDiagnostic;
  if (task.state === 'SUCCEEDED') {
    if (diagnostic?.connected === false) return { type: 'warning', title: '检查结果需要核对', description: '任务状态与连接诊断不一致，请刷新后核对' };
    return {
      type: 'success',
      title: '连接检查通过',
      description: diagnostic ? '嘉立创编辑器已连接' : '连接检查已完成。该历史任务未保存版本等详细诊断。'
    };
  }
  if (task.state === 'FAILED')
    return { type: 'error', title: '连接检查失败', description: task.errorMessage || '未能连接嘉立创编辑器，请检查本机客户端和连接器' };
  if (task.state === 'CANCELED') return { type: 'warning', title: '连接检查已取消', description: '本次检查已取消' };
  if (task.state === 'EXPIRED')
    return { type: 'warning', title: '连接检查已超时', description: task.errorMessage || '请核对设备在线状态后重新发起检查' };
  if (task.state === 'PARTIAL_SUCCESS')
    return { type: 'warning', title: '连接检查存在异常', description: task.errorMessage || '请核对返回的连接诊断' };
  return { type: 'info', title: task.state === 'RUNNING' ? '正在检查连接' : '等待设备检查', description: '等待配对设备返回检查结果' };
}
/** 仅提示同一任务从活动态进入终态；读取历史或重复终态不会重复通知。 */
export function engineeringCompletionNotice(
  previous: EngineeringTask | undefined,
  current: EngineeringTask
): { type: 'success' | 'warning' | 'error'; message: string } | undefined {
  if (
    !previous ||
    previous.taskId !== current.taskId ||
    !shouldPoll(previous.state) ||
    !['SUCCEEDED', 'PARTIAL_SUCCESS', 'FAILED', 'CANCELED', 'EXPIRED'].includes(current.state)
  )
    return;
  if (current.action === 'CHECK_CONNECTION') {
    const result = connectionCheckPresentation(current);
    return { type: result.type === 'info' ? 'warning' : result.type, message: result.title };
  }
  const label = engineeringActionLabel(current.action);
  if (current.state === 'SUCCEEDED') return { type: 'success', message: `${label}已完成` };
  if (current.state === 'FAILED') return { type: 'error', message: current.errorMessage || `${label}失败` };
  return { type: 'warning', message: current.errorMessage || `${label}：${ENGINEERING_STATES[current.state].label}` };
}

export { engineeringTaskOutcome } from './task-presentation';
