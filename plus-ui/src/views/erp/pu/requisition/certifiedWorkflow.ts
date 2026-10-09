import type { CertifiedPurchaseSession } from '@/api/erp/pu/requisition';
import type {
  PurchaseRequisitionVO,
  CertifiedPurchaseResponse,
  CertifiedRequisitionWorkflowContext,
  CertifiedRequisitionWorkflowResult
} from '@/api/erp/pu/requisition/types';

export type CertifiedRequisitionWorkflowOperation = 'approve' | 'convert';
export type CertifiedRequisitionWorkflowPhase = 'IDLE' | 'LOADING' | 'READY' | 'SENDING' | 'SUCCEEDED' | 'REFUSED' | 'UNKNOWN' | 'BLOCKED';

/** 组件仅消费安全快照；凭证、动态错误对象及内部锁内容绝不对外暴露。 */
export interface CertifiedRequisitionWorkflowSnapshot {
  phase: CertifiedRequisitionWorkflowPhase;
  document: PurchaseRequisitionVO | null;
  context: CertifiedRequisitionWorkflowContext | null;
  result: CertifiedRequisitionWorkflowResult | null;
  message: string;
  canApprove: boolean;
  canConvert: boolean;
  unknownLocked: boolean;
}

export interface CertifiedRequisitionWorkflowDependencies {
  readCredential: () => string | null;
  readContext: (session: CertifiedPurchaseSession) => Promise<CertifiedRequisitionWorkflowContext>;
  readDetail: (id: string, session: CertifiedPurchaseSession) => Promise<PurchaseRequisitionVO>;
  submit: (
    operation: CertifiedRequisitionWorkflowOperation,
    id: string,
    version: number,
    session: CertifiedPurchaseSession
  ) => Promise<CertifiedPurchaseResponse<CertifiedRequisitionWorkflowResult>>;
  storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;
  onChange?: (snapshot: CertifiedRequisitionWorkflowSnapshot) => void;
}

const MESSAGE = {
  idle: '尚未打开认证采购生命周期。',
  loading: '正在核验当前身份及完整请购单，请勿重复操作。',
  ready: '详情已刷新；按钮仅为提示，来源及写权限仍由后端核验。',
  sending: '原操作已安全留存，正在等待服务端结果，请勿重复操作。',
  succeeded: '原操作结果已确认，已只读刷新当前单据。',
  succeededReadMissing: '原操作结果已确认；当前详情未完整核验，请先手动刷新。',
  refused: '服务端明确拒绝本次操作；请人工核实并手动刷新后再操作。',
  unknown: '原操作结果未知，已保留文档锁；请人工核实，禁止重发或换版本绕锁。',
  blocked: '单据身份、版本、认证标记或审批流未确认，禁止认证写入。',
  identity: '当前身份或有效租户未确认，禁止继续操作；已留存的文档锁不会清除。',
  storage: '原操作不能安全留存或读取，禁止发送；请人工核实存储及原单据。',
  invalidated: '当前认证会话已失效；在途原操作锁保留，请重新核验身份。',
  closed: '认证采购窗口已关闭；已留存的原操作锁不会清除。'
} as const;

/**
 * 保留正数 Long 的 canonical 字符串；只允许安全正整数转字符串。
 * @param id 原业务 ID，不裁剪空白，不接收小数、前导零或不安全 Number
 * @returns 精确原 ID，长整数字符串绝不经过 Number
 */
export function normalizeRequisitionId(id: string | number): string {
  const value = typeof id === 'number' && Number.isSafeInteger(id) && id > 0 ? String(id) : id;
  if (typeof value !== 'string' || !/^[1-9]\d{0,18}$/.test(value) || (value.length === 19 && value > '9223372036854775807')) {
    throw new Error(MESSAGE.blocked);
  }
  return value;
}

const present = (value: unknown) => value !== null && value !== undefined;
const validVersion = (value: unknown): value is number => typeof value === 'number' && Number.isInteger(value) && value >= 0 && value < 2147483647;
const text = (value: unknown, limit: number): value is string =>
  typeof value === 'string' && value.length > 0 && value.length <= limit && value.trim() === value && !/[\u0000-\u001f\u007f]/.test(value);
const copy = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T;

/**
 * 旧编辑/Flowable/旧转单入口的保守防误写提示，不证明永久 command 或写授权。
 * 只有明确 MANUAL 且完全没有认证标记的头才放行，未知/非 MANUAL 一律保护。
 */
export function isProtectedRequisitionHint(row: Partial<PurchaseRequisitionVO> | null | undefined): boolean {
  return !row || row.sourceType !== 'MANUAL' || [row.decisionRunId, row.decisionActionId, row.decisionIdempotencyKey].some(present);
}

type BoundContext = CertifiedRequisitionWorkflowContext & { userId: string };
interface Binding {
  context: BoundContext;
  session: CertifiedPurchaseSession;
}
interface Intent {
  revision: number;
  key: string;
  encoded: string;
  fenceOwned?: boolean;
}

function checkedContext(context: CertifiedRequisitionWorkflowContext): BoundContext {
  if (!context || !text(context.tenantId, 20) || typeof context.approveAllowed !== 'boolean' || typeof context.convertAllowed !== 'boolean')
    throw new Error(MESSAGE.identity);
  return {
    tenantId: context.tenantId,
    userId: normalizeRequisitionId(context.userId),
    approveAllowed: context.approveAllowed,
    convertAllowed: context.convertAllowed
  };
}

function sameContext(left: BoundContext, right: BoundContext): boolean {
  return left.tenantId === right.tenantId && left.userId === right.userId;
}

function candidate(document: PurchaseRequisitionVO | null, id: string | null): boolean {
  try {
    if (
      !document ||
      !id ||
      normalizeRequisitionId(document.requisitionId) !== id ||
      !text(document.requisitionNo, 64) ||
      !validVersion(document.version) ||
      !text(document.decisionIdempotencyKey, 128) ||
      present(document.selectFlowId)
    )
      return false;
    // 来源只能用于封闭不完整候选；真实 command/账本的核验始终交给后端。
    if (document.sourceType === 'MRP') return !present(document.decisionRunId) && !present(document.decisionActionId);
    if (document.sourceType !== 'AI_DECISION') return false;
    normalizeRequisitionId(document.decisionRunId);
    normalizeRequisitionId(document.decisionActionId);
    return true;
  } catch {
    return false;
  }
}

/** 核对实际策略枚举、回执形状及精确ID；33字符转单状态不能被错误长度上限拒绝，不计算或伪造数量。 */
function validAllocations(result: CertifiedRequisitionWorkflowResult, operation: CertifiedRequisitionWorkflowOperation): boolean {
  try {
    if (!Array.isArray(result.allocations) || !result.allocations.length || result.allocations.length > 1000) return false;
    const quantity = (value: unknown) =>
      typeof value === 'number' ? Number.isFinite(value) && value >= 0 : typeof value === 'string' && /^\d+(?:\.\d+)?$/.test(value);
    const events = new Set<string>();
    const allocations = new Set<string>();
    return result.allocations.every((row) => {
      if (!row || typeof row !== 'object') return false;
      const eventId = normalizeRequisitionId(row.eventId);
      const allocationId = normalizeRequisitionId(row.allocationId);
      normalizeRequisitionId(row.actorId);
      normalizeRequisitionId(row.requisitionLineId);
      if (events.has(eventId) || allocations.has(allocationId)) return false;
      events.add(eventId);
      allocations.add(allocationId);
      if (operation === 'convert') normalizeRequisitionId(row.orderLineId);
      else if (row.orderLineId !== null) return false;
      return (
        row.operation === (operation === 'approve' ? 'APPROVE' : 'TRANSFER_TO_ORDER') &&
        row.fromStatus === 'REQUEST_INTENT' &&
        row.toStatus === (operation === 'convert' ? 'ORDER_COMMITTED_NOT_TIMELY_SUPPLY' : 'REQUEST_INTENT') &&
        quantity(row.occupiedBefore) &&
        quantity(row.occupiedAfter) &&
        typeof row.replayed === 'boolean'
      );
    });
  } catch {
    return false;
  }
}

/** 原转单回执按 allocation 集合核对稳定身份，不比较顺序/replayed，也不计算数量。 */
function sameOriginalReceipts(previous: CertifiedRequisitionWorkflowResult, current: CertifiedRequisitionWorkflowResult): boolean {
  const binding = (row: CertifiedRequisitionWorkflowResult['allocations'][number]) =>
    JSON.stringify([
      normalizeRequisitionId(row.eventId),
      normalizeRequisitionId(row.requisitionLineId),
      normalizeRequisitionId(row.orderLineId),
      normalizeRequisitionId(row.actorId),
      row.operation,
      row.fromStatus,
      row.toStatus
    ]);
  const original = new Map(previous.allocations.map((row) => [normalizeRequisitionId(row.allocationId), binding(row)]));
  return (
    previous.allocations.length === current.allocations.length &&
    current.allocations.every((row) => original.get(normalizeRequisitionId(row.allocationId)) === binding(row))
  );
}

function checkedResult(
  response: CertifiedPurchaseResponse<CertifiedRequisitionWorkflowResult>,
  operation: CertifiedRequisitionWorkflowOperation,
  id: string,
  document: PurchaseRequisitionVO
): CertifiedRequisitionWorkflowResult {
  const result = response?.data;
  const orderedRecovery = operation === 'convert' && document.status === 'ORDERED';
  const expectedVersion = orderedRecovery ? document.version : document.version + 1;
  if (
    response?.code !== 200 ||
    !result ||
    normalizeRequisitionId(result.requisitionId) !== id ||
    result.requisitionNo !== document.requisitionNo ||
    result.status !== (operation === 'approve' ? 'APPROVED' : 'ORDERED') ||
    typeof result.version !== 'number' ||
    !Number.isInteger(result.version) ||
    result.version !== expectedVersion ||
    typeof result.replayed !== 'boolean' ||
    (orderedRecovery && !result.replayed) ||
    !validAllocations(result, operation)
  )
    throw new Error(MESSAGE.unknown);
  if (operation === 'convert') {
    normalizeRequisitionId(result.orderId);
    if (typeof result.orderNo !== 'string' || !/^[A-Za-z0-9_-]{1,64}$/.test(result.orderNo)) throw new Error(MESSAGE.unknown);
  } else if (result.orderId !== null || result.orderNo !== null) throw new Error(MESSAGE.unknown);
  return copy(result);
}

/**
 * 纯生命周期控制器：完整读取、固定会话、文档作用域意图与结果未知锁相互独立。
 * Browser storage 不是跨进程 CAS；本地防双击不替代后端事务/幂等最终保证。
 * @param deps 普通只读 API、认证提交函数和可故障注入的存储边界
 * @returns 稳定操作接口；异步操作不会把旧主体迟到响应应用到新快照
 */
export function createCertifiedRequisitionWorkflow(deps: CertifiedRequisitionWorkflowDependencies) {
  let revision = 0;
  let id: string | null = null;
  let binding: Binding | null = null;
  let intent: Intent | null = null;
  // 独立于窗口/revision/版本的主体-文档锁存；存储丢失不能让 readonly 读值将 UNKNOWN 改回 READY。
  const unknownLocks = new Map<string, string>();
  const state: CertifiedRequisitionWorkflowSnapshot = {
    phase: 'IDLE',
    document: null,
    context: null,
    result: null,
    message: MESSAGE.idle,
    canApprove: false,
    canConvert: false,
    unknownLocked: false
  };
  class StorageFault extends Error {}
  class ExistingLock extends Error {}

  function tokenCurrent(): boolean {
    try {
      return !!binding && deps.readCredential() === binding.session.token;
    } catch {
      return false;
    }
  }

  /** 返回隔离副本，组件不能通过修改快照伪造版本、权限或成功结果。 */
  function snapshot(): CertifiedRequisitionWorkflowSnapshot {
    const ready = ['READY', 'SUCCEEDED'].includes(state.phase) && !state.unknownLocked && tokenCurrent() && candidate(state.document, id);
    return copy({
      ...state,
      canApprove: ready && state.context?.approveAllowed === true && state.document?.status === 'DRAFT',
      canConvert: ready && state.context?.convertAllowed === true && ['APPROVED', 'ORDERED'].includes(state.document?.status)
    });
  }

  function publish() {
    const view = snapshot();
    // UI 回调故障不能改变安全边界，也不能阻断未知锁留存。
    try {
      deps.onChange?.(view);
    } catch {
      /* 保留内部状态。 */
    }
    return snapshot();
  }

  function fixedSession(token: string, current: number): CertifiedPurchaseSession {
    return {
      token,
      assertCurrent() {
        if (revision !== current || deps.readCredential() !== token) throw new Error(MESSAGE.identity);
      }
    };
  }

  function storageKey(context: BoundContext, documentId: string) {
    // 版本/操作/key 都不参与分键，不能通过换版本绕过同一文档的未知锁。
    return ['nexus:pu:certified-workflow', context.tenantId, context.userId, documentId].map(encodeURIComponent).join(':');
  }

  function stored(key: string): string | null {
    try {
      return deps.storage.getItem(key);
    } catch {
      throw new StorageFault();
    }
  }

  const fenceKey = (key: string) => key + ':release-fence';

  function scopeLocked(key: string): boolean {
    try {
      const primary = stored(key);
      // 首次读到的原锁必须在任何后续I/O之前锁存；fence读取故障不能抹掉已知事实。
      if (primary !== null) unknownLocks.set(key, primary);
      const fence = stored(fenceKey(key));
      if (primary !== null || fence !== null) {
        unknownLocks.set(key, primary ?? fence);
        return true;
      }
    } catch (error) {
      if (unknownLocks.has(key)) return true;
      throw error;
    }
    if (!unknownLocks.has(key)) return false;
    // 同实例的事实仍在；存储恢复后只尽力补回原记录，绝不解除锁或重发业务操作。
    try {
      deps.storage.setItem(key, unknownLocks.get(key));
    } catch {
      /* 内存锁仍封闭当前文档。 */
    }
    return true;
  }

  async function verifyContext(bound: Binding): Promise<BoundContext> {
    bound.session.assertCurrent();
    const context = checkedContext(await deps.readContext(bound.session));
    bound.session.assertCurrent();
    if (!sameContext(context, bound.context)) throw new Error(MESSAGE.identity);
    return context;
  }

  async function readAll(current: number, documentId: string, previous: Binding | null) {
    const token = previous?.session.token ?? deps.readCredential();
    if (typeof token !== 'string' || !token) throw new Error(MESSAGE.identity);
    const session = previous?.session ?? fixedSession(token, current);
    session.assertCurrent();
    const context = checkedContext(await deps.readContext(session));
    session.assertCurrent();
    if (previous && !sameContext(context, previous.context)) throw new Error(MESSAGE.identity);
    const bound: Binding = { context, session };
    const document = copy(await deps.readDetail(documentId, session));
    session.assertCurrent();
    if (!document || normalizeRequisitionId(document.requisitionId) !== documentId) throw new Error(MESSAGE.blocked);
    // 详情读取期间也可能切换动态租户；不得把两个主体的只读事实拼在一起。
    const latest = await verifyContext(bound);
    const locked = scopeLocked(storageKey(latest, documentId));
    session.assertCurrent();
    return { bound: { context: latest, session }, document, locked };
  }

  async function load(current: number, documentId: string, previous: Binding | null) {
    try {
      const read = await readAll(current, documentId, previous);
      if (revision !== current) return snapshot();
      binding = read.bound;
      state.document = read.document;
      state.context = read.bound.context;
      state.unknownLocked = read.locked;
      state.phase = read.locked ? 'UNKNOWN' : candidate(state.document, documentId) ? 'READY' : 'BLOCKED';
      state.message = read.locked ? MESSAGE.unknown : state.phase === 'READY' ? MESSAGE.ready : MESSAGE.blocked;
    } catch (error) {
      if (revision !== current) return snapshot();
      state.document = null;
      state.context = null;
      state.phase = state.unknownLocked ? 'UNKNOWN' : 'BLOCKED';
      if (error instanceof StorageFault) state.unknownLocked = true;
      state.message = error instanceof StorageFault ? MESSAGE.storage : MESSAGE.identity;
    }
    return publish();
  }

  /** 打开必须新核验主体；既存未知锁只读保留，不解析后覆盖或删除。 */
  async function open(documentId: string | number) {
    revision++;
    binding = null;
    id = null;
    Object.assign(state, { phase: 'LOADING', document: null, context: null, result: null, message: MESSAGE.loading, unknownLocked: false });
    const current = revision;
    try {
      id = normalizeRequisitionId(documentId);
    } catch {
      state.phase = 'BLOCKED';
      state.message = MESSAGE.blocked;
      return publish();
    }
    const selected = id;
    publish();
    return load(current, selected, null);
  }

  /** 手动只读刷新；在途操作不重复读取，不清除持久化未知锁。 */
  async function refresh() {
    if (!id || ['LOADING', 'SENDING'].includes(state.phase)) return snapshot();
    const current = revision;
    const selected = id;
    const previous = binding;
    state.phase = 'LOADING';
    state.message = MESSAGE.loading;
    publish();
    return load(current, selected, previous);
  }

  function retainUnknown(saved: Intent) {
    unknownLocks.set(saved.key, saved.encoded);
    const target = saved.fenceOwned ? fenceKey(saved.key) : saved.key;
    try {
      if (stored(saved.key) !== null || stored(fenceKey(saved.key)) !== null) return;
      deps.storage.setItem(target, saved.encoded);
    } catch {
      // 释放 guard 已由本次准备；即使读不可用也尽力补回它，不盲改可能属于别人的 primary。
      if (saved.fenceOwned)
        try {
          deps.storage.setItem(target, saved.encoded);
        } catch {
          /* 无持久能力时只能保留同实例锁。 */
        }
    }
  }

  function releaseOwned(saved: Intent, session: CertifiedPurchaseSession) {
    const fence = fenceKey(saved.key);
    if (stored(saved.key) !== saved.encoded || stored(fence) !== null) throw new ExistingLock();
    saved.fenceOwned = true;
    try {
      // 先留存并读回 guard，不能先删掉唯一的未知事实，再指望异常路径可以恢复。
      deps.storage.setItem(fence, saved.encoded);
      if (stored(fence) !== saved.encoded) throw new StorageFault();
      session.assertCurrent();
      if (stored(saved.key) !== saved.encoded) throw new ExistingLock();
      deps.storage.removeItem(saved.key);
      if (stored(saved.key) !== null) throw new StorageFault();
      session.assertCurrent();
      if (stored(fence) !== saved.encoded) throw new ExistingLock();
      deps.storage.removeItem(fence);
      if (stored(fence) !== null) throw new StorageFault();
      session.assertCurrent();
    } catch (error) {
      retainUnknown(saved);
      if (error instanceof ExistingLock) throw error;
      throw new StorageFault();
    }
  }

  async function run(operation: CertifiedRequisitionWorkflowOperation) {
    const initial = snapshot();
    if (!(operation === 'approve' ? initial.canApprove : initial.canConvert) || !id || !binding) return initial;
    const current = revision;
    const selected = id;
    const previous = binding;
    const previousResult = state.result;
    let saved: Intent | null = null;
    // 第一个 await 之前关闭写按钮，双击和回调重入都不能排队提交。
    state.phase = 'LOADING';
    state.message = MESSAGE.loading;
    publish();
    try {
      const read = await readAll(current, selected, previous);
      if (revision !== current) return snapshot();
      binding = read.bound;
      state.document = read.document;
      state.context = read.bound.context;
      state.unknownLocked = read.locked;
      if (read.locked) throw new ExistingLock();
      const allowed =
        candidate(read.document, selected) &&
        (operation === 'approve'
          ? read.bound.context.approveAllowed && read.document.status === 'DRAFT'
          : read.bound.context.convertAllowed && ['APPROVED', 'ORDERED'].includes(read.document.status));
      if (!allowed) {
        state.phase = 'BLOCKED';
        state.message = MESSAGE.blocked;
        return publish();
      }
      const key = storageKey(read.bound.context, selected);
      if (stored(key) !== null) throw new ExistingLock();
      saved = {
        revision: current,
        key,
        encoded: JSON.stringify({
          schema: 1,
          tenantId: read.bound.context.tenantId,
          userId: read.bound.context.userId,
          requisitionId: selected,
          operation,
          expectedVersion: read.document.version,
          state: 'PENDING'
        })
      };
      read.bound.session.assertCurrent();
      intent = saved;
      unknownLocks.set(key, saved.encoded);
      try {
        deps.storage.setItem(key, saved.encoded);
        if (stored(key) !== saved.encoded) throw new StorageFault();
      } catch {
        throw new StorageFault();
      }
      read.bound.session.assertCurrent();
      state.phase = 'SENDING';
      state.message = MESSAGE.sending;
      state.unknownLocked = true;
      publish();
      read.bound.session.assertCurrent();
      const response = await deps.submit(operation, selected, read.document.version, read.bound.session);
      read.bound.session.assertCurrent();
      const refused = typeof response?.code === 'number' && response.code >= 400 && response.code < 500;
      const result = refused ? null : checkedResult(response, operation, selected, read.document);
      if (
        result &&
        operation === 'convert' &&
        previousResult?.status === 'ORDERED' &&
        normalizeRequisitionId(previousResult.requisitionId) === selected &&
        (normalizeRequisitionId(result.orderId) !== normalizeRequisitionId(previousResult.orderId) ||
          result.orderNo !== previousResult.orderNo ||
          !sameOriginalReceipts(previousResult, result))
      ) {
        throw new Error(MESSAGE.unknown);
      }
      // 写响应之后仍核验实际主体，不能把同 token 的动态租户漂移当作原作用域成功。
      let latest = await verifyContext(read.bound);
      let refreshed: PurchaseRequisitionVO | null = null;
      if (result) {
        try {
          const detail = copy(await deps.readDetail(selected, read.bound.session));
          read.bound.session.assertCurrent();
          if (
            detail &&
            normalizeRequisitionId(detail.requisitionId) === selected &&
            detail.requisitionNo === result.requisitionNo &&
            detail.status === result.status &&
            detail.version === result.version
          )
            refreshed = detail;
        } catch {
          read.bound.session.assertCurrent();
        }
        latest = await verifyContext(read.bound);
      }
      read.bound.session.assertCurrent();
      releaseOwned(saved, read.bound.session);
      read.bound.session.assertCurrent();
      intent = null;
      unknownLocks.delete(saved.key);
      state.unknownLocked = false;
      state.context = latest;
      state.result = result;
      state.phase = refused ? 'REFUSED' : 'SUCCEEDED';
      state.document = refused ? read.document : refreshed;
      state.message = refused ? MESSAGE.refused : refreshed ? MESSAGE.succeeded : MESSAGE.succeededReadMissing;
      return publish();
    } catch (error) {
      if (saved) retainUnknown(saved);
      if (revision !== current) return snapshot();
      state.result = null;
      state.phase = saved || error instanceof ExistingLock ? 'UNKNOWN' : 'BLOCKED';
      state.unknownLocked = !!saved || error instanceof ExistingLock || error instanceof StorageFault;
      state.message = error instanceof StorageFault ? MESSAGE.storage : state.phase === 'UNKNOWN' ? MESSAGE.unknown : MESSAGE.identity;
      return publish();
    }
  }

  /** 失效只废除内存会话；在途的 PENDING 锁视为未知，永不删除。 */
  function invalidate() {
    state.unknownLocked ||= intent?.revision === revision;
    revision++;
    binding = null;
    Object.assign(state, { phase: 'BLOCKED', document: null, context: null, result: null, message: MESSAGE.invalidated });
    return publish();
  }

  /** 关闭窗口不丢弃持久化原意图；迟到响应受 revision 隔离。 */
  function close() {
    state.unknownLocked ||= intent?.revision === revision;
    revision++;
    binding = null;
    id = null;
    Object.assign(state, { phase: 'IDLE', document: null, context: null, result: null, message: MESSAGE.closed });
    return publish();
  }

  return { open, refresh, approve: () => run('approve'), convert: () => run('convert'), invalidate, close, snapshot };
}
