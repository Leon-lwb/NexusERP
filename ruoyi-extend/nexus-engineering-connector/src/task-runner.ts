import { setTimeout as delay } from 'node:timers/promises';
import { relative, sep } from 'node:path';
import type { TaskLease, LocalArtifact } from './types.js';
import { PROTOCOL } from './types.js';
import type { NexusClient } from './nexus-client.js';
import { ArtifactUploader } from './artifacts/upload.js';
import { containedPath } from './workspace.js';
import { TASK_STAGES, validateResumeState, type ResumeState, type ResumeStore, type TaskStage } from './resume-state.js';

/** 本地动作在阶段ACK返回前不得执行下一副作用。 */
export interface LocalExecutor {
  execute(task: TaskLease, signal?: AbortSignal, onStage?: (stage: string, signal?: AbortSignal) => Promise<void>): Promise<any>;
}
/** 可注入短周期和恢复仓库以验证租约、重启与取消。 */
export interface RunnerOptions {
  renewIntervalMs?: number;
  pollIntervalMs?: number;
  heartbeatIntervalMs?: number;
  resumeStore?: ResumeStore;
}
/** 后端已经终结任务时直接返回真实状态，不再写阶段或完成请求。 */
class RemoteTerminal extends Error {
  constructor(readonly task: any) {
    super('TASK_TERMINAL');
  }
}
const terminalStates = new Set(['SUCCEEDED', 'PARTIAL_SUCCESS', 'FAILED', 'CANCELED', 'EXPIRED']);
const executionErrors = new Set([
  'DEVICE_OFFLINE',
  'EDITOR_UNAVAILABLE',
  'TEMPLATE_UNAVAILABLE',
  'TEMPLATE_HASH_MISMATCH',
  'CAPABILITY_UNSUPPORTED',
  'EXECUTION_UNCERTAIN',
  'EXPORT_FAILED',
  'DRC_UNAVAILABLE'
]);

/** 控制请求按修订号串行，文件传输独立；不重放执行状态不确定的设计动作。 */
export class TaskRunner {
  private active = false;
  constructor(
    private api: Pick<NexusClient, 'request'>,
    private executor: LocalExecutor,
    private workspaceRoot: string,
    private options: RunnerOptions = {}
  ) {}

  /** 启动时先认证已保存任务，过期身份只清理密文，不修改旧工程或旧任务。 */
  async resume(signal?: AbortSignal): Promise<any> {
    const saved = await this.options.resumeStore?.load();
    if (!saved) return null;
    validateResumeState(saved);
    signal?.throwIfAborted();
    if (Date.now() >= Date.parse(saved.lease.leaseExpiresAt) || Date.now() >= Date.parse(saved.lease.deadlineAt)) {
      await this.options.resumeStore!.clear();
      return { resumed: false, errorCode: 'LEASE_EXPIRED' };
    }
    return this.executeLease(saved.lease, signal, saved);
  }

  /** 执行一个尝试，事件原文、序号和租约必须先持久化再开始下一副作用。 */
  async executeLease(input: TaskLease, outerSignal?: AbortSignal, restored?: ResumeState): Promise<any> {
    if (this.active) throw new Error('DEVICE_BUSY');
    this.active = true;
    const state: ResumeState = restored ?? {
      protocolVersion: PROTOCOL,
      lease: { ...input, revision: Number(input.revision) },
      sequence: 0,
      stage: 'CHECKING',
      phase: 'PREPARED'
    };
    const task = state.lease;
    const controller = new AbortController();
    const controlSignal = outerSignal ?? new AbortController().signal;
    const signal = AbortSignal.any([controlSignal, controller.signal]);
    let cancelRequested = false,
      stopping = false,
      renewQueued = false;
    let tail: Promise<unknown> = Promise.resolve();
    let timer: ReturnType<typeof setInterval> | undefined;
    let expiryTimer: ReturnType<typeof setTimeout> | undefined;
    let savedTail: Promise<unknown> = Promise.resolve();
    const persist = (): Promise<void> => {
      const snapshot = structuredClone(state);
      const saved = savedTail.then(async () => {
        await this.options.resumeStore?.save(snapshot);
      });
      savedTail = saved.catch(() => {});
      return saved;
    };
    const clearSaved = async () => {
      await savedTail;
      await this.options.resumeStore?.clear();
    };
    const serialize = <T>(action: () => Promise<T>): Promise<T> => {
      const result = tail.then(action);
      tail = result.catch(() => {});
      return result;
    };
    const lease = () => ({ attempt: task.attempt, leaseToken: task.leaseToken, expectedRevision: task.revision });
    const ensureLive = () => {
      if (Date.now() >= Date.parse(task.leaseExpiresAt) || Date.now() >= Date.parse(task.deadlineAt)) throw new Error('LEASE_EXPIRED');
    };
    const watchExpiry = () => {
      if (expiryTimer) clearTimeout(expiryTimer);
      const remaining = Math.min(Date.parse(task.leaseExpiresAt), Date.parse(task.deadlineAt)) - Date.now();
      expiryTimer = setTimeout(() => controller.abort(new Error('LEASE_EXPIRED')), Math.max(0, remaining));
    };
    const update = (receipt: any) => {
      const revision = Number(receipt.revision);
      if (!Number.isSafeInteger(revision) || revision < 1) throw new Error('INVALID_TASK_RECEIPT');
      task.revision = Math.max(task.revision, revision);
      if (typeof receipt.leaseExpiresAt === 'string') {
        if (!Number.isFinite(Date.parse(receipt.leaseExpiresAt))) throw new Error('INVALID_TASK_RECEIPT');
        task.leaseExpiresAt = receipt.leaseExpiresAt;
        watchExpiry();
      }
      cancelRequested ||= receipt.cancelRequested === true;
      if (cancelRequested) controller.abort(new Error('CANCEL_REQUESTED'));
    };
    const readDetail = async (requestSignal = controlSignal) => {
      const detail = await this.api.request(`/connector/tasks/${task.taskId}`, undefined, requestSignal);
      if (String(detail.taskId) !== task.taskId || detail.attempt !== task.attempt) throw new Error('STALE_ATTEMPT');
      if (terminalStates.has(detail.state)) throw new RemoteTerminal(detail);
      if (detail.state !== 'RUNNING') throw new Error('STALE_ATTEMPT');
      update(detail);
      for (const item of detail.events ?? []) {
        if (!Number.isSafeInteger(item.sequence) || item.sequence < 1 || item.sequence > 64) throw new Error('INVALID_TASK_RECEIPT');
        state.sequence = Math.max(state.sequence, item.sequence);
      }
      if (TASK_STAGES.includes(detail.stage) && TASK_STAGES.indexOf(detail.stage) > TASK_STAGES.indexOf(state.stage)) state.stage = detail.stage;
      await persist();
      return detail;
    };
    /** 仅重试未入账事件；已经入账的序号必须沿用原始负载领取相同回执。 */
    const sendPending = async (requestSignal = controlSignal) => {
      const pending = state.pendingEvent;
      if (!pending) return;
      for (let retry = 0; ; retry++) {
        ensureLive();
        requestSignal.throwIfAborted();
        try {
          const receipt = await this.api.request(`/connector/tasks/${task.taskId}/events`, pending, requestSignal);
          update(receipt);
          state.sequence = Math.max(state.sequence, pending.sequence);
          if (TASK_STAGES.indexOf(pending.stage) >= TASK_STAGES.indexOf(state.stage)) state.stage = pending.stage;
          state.pendingEvent = undefined;
          await persist();
          return receipt;
        } catch (error) {
          if ((error as { status?: number }).status !== 409 || retry >= 1) throw error;
          const detail = await readDetail(requestSignal);
          const recorded = (detail.events ?? []).some((item: any) => item.sequence === pending.sequence);
          if (recorded) throw error;
          if (cancelRequested && !pending.cancelAcknowledged) {
            state.pendingEvent = undefined;
            await persist();
            throw new Error('CANCEL_REQUESTED');
          }
          if ((error as { code?: string }).code === 'IDEMPOTENCY_CONFLICT' || pending.expectedRevision === task.revision) throw error;
          pending.expectedRevision = task.revision;
          await persist();
        }
      }
    };
    const event = (next: string, extra: { cancelAcknowledged?: boolean; errorCode?: string } = {}, requestSignal = controlSignal) =>
      serialize(async () => {
        if (!TASK_STAGES.includes(next as TaskStage)) throw new Error('INVALID_STAGE');
        if (state.pendingEvent) await sendPending(requestSignal);
        if (cancelRequested && !extra.cancelAcknowledged) throw new Error('CANCEL_REQUESTED');
        if (TASK_STAGES.indexOf(next as TaskStage) < TASK_STAGES.indexOf(state.stage)) return;
        if (state.sequence >= 64) throw new Error('EVENT_LIMIT_EXCEEDED');
        ensureLive();
        requestSignal.throwIfAborted();
        state.pendingEvent = {
          ...lease(),
          sequence: state.sequence + 1,
          stage: next as TaskStage,
          message: '本地工程动作执行进度',
          cancelAcknowledged: false,
          ...extra
        };
        await persist();
        return sendPending(requestSignal);
      });
    const control = (suffix: string, body: () => unknown, requestSignal = controlSignal) =>
      serialize(async () => {
        if (suffix === 'renew' && stopping) return;
        for (let retry = 0; ; retry++) {
          ensureLive();
          requestSignal.throwIfAborted();
          if (cancelRequested) throw new Error('CANCEL_REQUESTED');
          try {
            const result = await this.api.request(`/connector/tasks/${task.taskId}/${suffix}`, body(), requestSignal);
            if (suffix === 'renew') {
              update(result);
              await persist();
            }
            return result;
          } catch (error) {
            if ((error as { status?: number }).status !== 409 || retry >= 1) throw error;
            const previousRevision = task.revision;
            await readDetail(requestSignal);
            if (cancelRequested) throw new Error('CANCEL_REQUESTED');
            if (previousRevision === task.revision) throw error;
          }
        }
      });
    const renew = () => control('renew', lease);
    const stopRenewal = () => {
      stopping = true;
      if (timer) clearInterval(timer);
    };
    const finish = async (errorCode?: string) => {
      await event('FINALIZING', errorCode ? { errorCode } : {});
      state.phase = 'COMPLETING';
      await persist();
      stopRenewal();
      const result = await control('complete', lease);
      await clearSaved();
      return result;
    };
    try {
      validateResumeState(state);
      ensureLive();
      controlSignal.throwIfAborted();
      await persist();
      watchExpiry();
      if (restored) {
        await readDetail();
        if (state.pendingEvent) await serialize(() => sendPending());
        if (cancelRequested) throw new Error('CANCEL_REQUESTED');
        await renew();
        if (state.phase === 'EXECUTING') throw new Error('EXECUTION_UNCERTAIN');
      }
      const intervalMs = this.options.renewIntervalMs ?? 30_000;
      if (intervalMs > 0)
        timer = setInterval(() => {
          if (stopping || renewQueued || cancelRequested) return;
          renewQueued = true;
          renew()
            .catch((error) => controller.abort(error))
            .finally(() => {
              renewQueued = false;
            });
        }, intervalMs);
      if (state.executionError) return await finish(state.executionError);
      if (state.phase === 'PREPARED') {
        await event('CHECKING');
        signal.throwIfAborted();
        state.phase = 'EXECUTING';
        await persist();
        signal.throwIfAborted();
        state.result = await this.executor.execute(task, signal, async (next, stageSignal) => {
          signal.throwIfAborted();
          await event(next, {}, stageSignal ? AbortSignal.any([signal, stageSignal]) : signal);
          signal.throwIfAborted();
        });
        state.phase = 'EXECUTED';
        await serialize(persist);
      }
      await tail;
      signal.throwIfAborted();
      const result = state.result;
      if (!result || !Array.isArray(result.artifacts)) throw new Error('INVALID_EXECUTION_RESULT');
      if (task.action === 'CHECK_CONNECTION' && result.diagnostic?.connected !== true) throw new Error('EDITOR_UNAVAILABLE');
      if (state.phase !== 'COMPLETING') {
        await event('UPLOADING');
        signal.throwIfAborted();
        const uploadApi: Pick<NexusClient, 'request'> = {
          request: async (path, body, uploadSignal, headers) => {
            if (path.endsWith('/upload-ticket')) return control('upload-ticket', () => ({ ...(body as object), ...lease() }), uploadSignal ?? signal);
            ensureLive();
            (uploadSignal ?? signal).throwIfAborted();
            return this.api.request(path, body, uploadSignal ?? signal, headers);
          }
        };
        const uploader = new ArtifactUploader(uploadApi);
        for (const item of result.artifacts) {
          signal.throwIfAborted();
          const relativePath = item.workspaceRelativePath ?? relative(this.workspaceRoot, item.localPath).split(sep).join('/');
          const artifact: LocalArtifact = { ...item, localPath: await containedPath(this.workspaceRoot, relativePath) };
          await uploader.upload(task, artifact, signal);
        }
      }
      return await finish();
    } catch (error) {
      if (error instanceof RemoteTerminal) {
        stopRenewal();
        await clearSaved();
        return error.task;
      }
      if (cancelRequested && !controlSignal.aborted) {
        stopRenewal();
        const receipt = await event('FINALIZING', { cancelAcknowledged: true });
        await clearSaved();
        return { ...receipt, state: 'CANCELED' };
      }
      const invalidLease = (error as { code?: string }).code ?? (error instanceof Error ? error.message : '');
      if (invalidLease === 'STALE_ATTEMPT' || invalidLease === 'LEASE_EXPIRED') {
        stopRenewal();
        await clearSaved();
        return { resumed: false, errorCode: invalidLease };
      }
      const status = (error as { status?: number }).status;
      if (signal.aborted || status === 401 || status === 403 || state.pendingEvent) throw error;
      const raw = error instanceof Error ? error.message : '';
      state.executionError = executionErrors.has(raw) ? raw : task.action === 'CHECK_CONNECTION' ? 'EDITOR_UNAVAILABLE' : 'EXPORT_FAILED';
      state.phase = 'EXECUTED';
      state.result ??= { artifacts: [], errors: [] };
      await serialize(persist);
      return await finish(state.executionError);
    } finally {
      stopRenewal();
      if (expiryTimer) clearTimeout(expiryTimer);
      await tail;
      await savedTail;
      this.active = false;
    }
  }

  /** 有恢复记录时优先核对旧尝试；身份被撤销立即停止领取。 */
  async run(signal: AbortSignal, onHeartbeat: () => Promise<void>): Promise<void> {
    await this.resume(signal);
    let lastHeartbeat = 0;
    while (!signal.aborted) {
      try {
        await this.resume(signal);
        if (Date.now() - lastHeartbeat >= (this.options.heartbeatIntervalMs ?? 15_000)) {
          await onHeartbeat();
          lastHeartbeat = Date.now();
        }
        const claim = await this.api.request('/connector/claim', {}, signal);
        if (claim) await this.executeLease(claim, signal);
      } catch (error) {
        if (signal.aborted) break;
        const status = (error as { status?: number }).status;
        if (status === 401 || status === 403) throw error;
        console.error('工程任务未完成，保留本地文件并等待服务端状态核对。');
      }
      await delay(this.options.pollIntervalMs ?? 3000, undefined, { signal }).catch(() => {});
    }
  }
}
