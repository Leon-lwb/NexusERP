import { join } from 'node:path';
import { deflateRawSync, inflateRawSync } from 'node:zlib';
import { containedPath, ensureDirectory } from './workspace.js';
import { CredentialStore } from './auth/credential-store.js';
import { PROTOCOL, type TaskLease } from './types.js';

/** 服务端固定阶段与事件序号独立，恢复时不得倒退。 */
export const TASK_STAGES = ['CHECKING', 'PREPARING', 'GENERATING', 'CHECKING_DRC', 'EXPORTING', 'UPLOADING', 'FINALIZING'] as const;
export type TaskStage = (typeof TASK_STAGES)[number];
/** 保存原始请求以重取同一幂等回执，不能改写已提交事件的摘要。 */
export interface PendingEvent {
  attempt: number;
  leaseToken: string;
  expectedRevision: number;
  sequence: number;
  stage: TaskStage;
  message: string;
  cancelAcknowledged: boolean;
  errorCode?: string;
}
/** 仅在DPAPI密文内保存租约、执行边界和待确认回执。 */
export interface ResumeState {
  protocolVersion: typeof PROTOCOL;
  lease: TaskLease;
  sequence: number;
  stage: TaskStage;
  phase: 'PREPARED' | 'EXECUTING' | 'EXECUTED' | 'COMPLETING';
  pendingEvent?: PendingEvent;
  result?: any;
  executionError?: string;
}
/** 恢复存储契约；实际CLI只注入绑定当前设备的加密实现。 */
export interface ResumeStore {
  load(): Promise<ResumeState | null>;
  save(state: ResumeState): Promise<void>;
  clear(): Promise<void>;
}

/** 拒绝损坏或不兼容的恢复身份，避免把未知状态当成可重放任务。 */
export function validateResumeState(value: unknown): ResumeState {
  const state = value as ResumeState;
  const lease = state?.lease;
  if (
    state?.protocolVersion !== PROTOCOL ||
    !lease ||
    !/^\d{1,20}$/.test(lease.taskId) ||
    !Number.isSafeInteger(lease.attempt) ||
    lease.attempt < 1 ||
    !Number.isSafeInteger(lease.revision) ||
    lease.revision < 1 ||
    typeof lease.leaseToken !== 'string' ||
    lease.leaseToken.length < 1 ||
    lease.leaseToken.length > 256 ||
    !Number.isFinite(Date.parse(lease.leaseExpiresAt)) ||
    !Number.isFinite(Date.parse(lease.deadlineAt)) ||
    !['CHECK_CONNECTION', 'OPEN_PROJECT', 'CREATE_PCB_DRAFT', 'EXPORT_ARTIFACTS', 'RUN_DRC', 'SUBMIT_DRAFT'].includes(lease.action) ||
    !lease.parameters ||
    typeof lease.parameters !== 'object' ||
    Array.isArray(lease.parameters) ||
    !Number.isSafeInteger(state.sequence) ||
    state.sequence < 0 ||
    state.sequence > 64 ||
    !TASK_STAGES.includes(state.stage) ||
    !['PREPARED', 'EXECUTING', 'EXECUTED', 'COMPLETING'].includes(state.phase)
  )
    throw new Error('INVALID_RESUME_STATE');
  const event = state.pendingEvent;
  if (
    event &&
    (event.attempt !== lease.attempt ||
      event.leaseToken !== lease.leaseToken ||
      !Number.isSafeInteger(event.expectedRevision) ||
      event.expectedRevision < 1 ||
      !Number.isSafeInteger(event.sequence) ||
      event.sequence < 1 ||
      event.sequence > 64 ||
      !TASK_STAGES.includes(event.stage) ||
      typeof event.message !== 'string' ||
      typeof event.cancelAcknowledged !== 'boolean')
  )
    throw new Error('INVALID_RESUME_STATE');
  return state;
}

/** 复用现有CredentialStore的CurrentUser DPAPI与原子写入，单独保存不用于认证的恢复信封。 */
export class ProtectedResumeStore implements ResumeStore {
  private protectedRecord: CredentialStore;
  constructor(
    private workspaceRoot: string,
    private deviceId: string,
    private serverUrl: string
  ) {
    this.protectedRecord = new CredentialStore(join(workspaceRoot, '.resume'));
  }
  /** 只有同一Nexus地址与设备身份能读取这份租约恢复记录。 */
  async load(): Promise<ResumeState | null> {
    await containedPath(this.workspaceRoot, '.resume/identity.dpapi');
    const envelope = await this.protectedRecord.load();
    if (!envelope) return null;
    if (envelope.deviceId !== this.deviceId || envelope.serverUrl !== this.serverUrl) throw new Error('RESUME_IDENTITY_MISMATCH');
    if (!envelope.refreshToken.startsWith('resume/1:')) throw new Error('INVALID_RESUME_STATE');
    const payload = inflateRawSync(Buffer.from(envelope.refreshToken.slice(9), 'base64'), { maxOutputLength: 256 * 1024 });
    return validateResumeState(JSON.parse(payload.toString('utf8')));
  }
  /** 先压缩为ASCII以无损保存中文与多产物元数据；refreshToken字段仅作为现有加密仓库的不透明记录载体，绝不提交给刷新接口。 */
  async save(state: ResumeState): Promise<void> {
    validateResumeState(state);
    const payload = JSON.stringify(state);
    if (Buffer.byteLength(payload) > 256 * 1024) throw new Error('RESUME_STATE_TOO_LARGE');
    const protectedPayload = 'resume/1:' + deflateRawSync(Buffer.from(payload, 'utf8')).toString('base64');
    if (protectedPayload.length > 40 * 1024) throw new Error('RESUME_STATE_TOO_LARGE');
    await ensureDirectory(this.workspaceRoot, '.resume');
    await containedPath(this.workspaceRoot, '.resume/identity.dpapi');
    await this.protectedRecord.save({ deviceId: this.deviceId, serverUrl: this.serverUrl, refreshToken: protectedPayload });
  }
  /** 仅删除恢复密文，保留身份凭证、执行journal和全部工程文件。 */
  async clear(): Promise<void> {
    await containedPath(this.workspaceRoot, '.resume/identity.dpapi');
    await this.protectedRecord.clear();
  }
}
