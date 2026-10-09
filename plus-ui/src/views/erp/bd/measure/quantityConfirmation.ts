import { createBusinessIntentStore, createBusinessSession, normalizeBusinessId } from '@/utils/certified-business';
import type { BusinessScope, CertifiedBusinessResponse, CertifiedBusinessSession } from '@/utils/certified-business';
import type { QuantityConfirmationBody, QuantityConfirmationContext } from '@/api/erp/bd/measure/types';

export type QuantityConfirmationPhase = 'closed' | 'loading' | 'ready' | 'submitting' | 'confirmed' | 'rejected' | 'invalid' | 'unknown';

/** 只保存核验实际使用的原生字段；服务器快照独立于人工表单及列表 props。 */
export interface QuantityUnitSnapshot {
  uomId: string;
  uomCode: string;
  uomName: string;
  category: string;
  isBase: 0 | 1;
  primaryId: string | null;
  status: 1;
  conversionRate: string | null;
  quantityScale: number | null;
  conversionVersion: string | null;
  conversionVerifiedAt: string | null;
}
export interface QuantitySnapshot {
  context: { tenantId: string; userId: string; confirmAllowed: boolean };
  unit: QuantityUnitSnapshot;
  root: QuantityUnitSnapshot;
}
export interface QuantityConfirmationState {
  phase: QuantityConfirmationPhase;
  documentId: string | null;
  snapshot: QuantitySnapshot | null;
  form: { conversionRate: string; quantityScale: string; reference: string };
  message: string;
}

/** 原始表单始终留空，避免把旧比例、精度或 NULL 原版本偷偷当作人工认证。 */
export function createQuantityConfirmationState(): QuantityConfirmationState {
  return { phase: 'closed', documentId: null, snapshot: null, form: { conversionRate: '', quantityScale: '', reference: '' }, message: '' };
}

export interface QuantityConfirmationPorts {
  api: {
    getContext(session: CertifiedBusinessSession): Promise<QuantityConfirmationContext>;
    getDetail(id: string, session: CertifiedBusinessSession): Promise<unknown>;
    confirm(id: string, body: QuantityConfirmationBody, session: CertifiedBusinessSession): Promise<CertifiedBusinessResponse<unknown>>;
  };
  storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;
  readCredential(): string | null | undefined;
  readDocumentId(): unknown;
  readRouteKey(): string;
  requestKey(): string;
  now?: () => number;
  state?: QuantityConfirmationState;
}

const UNKNOWN = '本次数量确认结果未知，已锁定此主体与单位。请仅只读回查并联系管理员核对，勿重发；关闭、重开或版本变化不会解锁。';
const INVALID = '单位原生资料、根关系或当前主体不完整，无法确认。请核对真实资料后重新读取；不会自动修复旧映射。';
function requireFact(valid: unknown): asserts valid {
  if (!valid) throw new Error(INVALID);
}
function object(value: unknown): Record<string, unknown> {
  requireFact(value && typeof value === 'object' && !Array.isArray(value));
  return value as Record<string, unknown>;
}
function text(value: unknown, max: number): value is string {
  return typeof value === 'string' && value.trim().length > 0 && value.length <= max;
}
function version(value: unknown): string | null {
  requireFact(value === null || text(value, 64));
  return value as string | null;
}

/** 十进制文本规范化仅用于精确相等比较；提交保留人工字符串，不经过浮点计算。 */
function decimal(value: unknown): string {
  requireFact(typeof value === 'string' || (typeof value === 'number' && Number.isFinite(value)));
  const source = String(value);
  requireFact(/^\d+(?:\.\d+)?$/.test(source));
  const [integer, fraction = ''] = source.split('.');
  const normalizedInteger = integer.replace(/^0+(?=\d)/, '');
  const normalizedFraction = fraction.replace(/0+$/, '');
  requireFact(normalizedInteger.length <= 12 && fraction.length <= 6 && /[1-9]/.test(normalizedInteger + normalizedFraction));
  return normalizedInteger + (normalizedFraction ? '.' + normalizedFraction : '');
}

/** Date 字段采用严格日历解析；支持原生 +08:00 文本与 ISO，拒绝坏日期及未来核验时间。 */
function verifiedDate(value: unknown, now: number): string | null {
  if (value === null) return null;
  requireFact(typeof value === 'string');
  const match = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,3}))?(Z|[+-]\d{2}:\d{2})?$/.exec(value);
  requireFact(match);
  const [, y, m, d, h, min, sec, milli = '', zone = '+08:00'] = match;
  const date = new Date(Date.UTC(Number(y), Number(m) - 1, Number(d), Number(h), Number(min), Number(sec)));
  requireFact(
    Number(y) >= 1000 &&
      Number(y) <= 9999 &&
      date.getUTCFullYear() === Number(y) &&
      date.getUTCMonth() === Number(m) - 1 &&
      date.getUTCDate() === Number(d) &&
      Number(h) <= 23 &&
      Number(min) <= 59 &&
      Number(sec) <= 59
  );
  const parsed = Date.parse(`${y}-${m}-${d}T${h}:${min}:${sec}${milli ? '.' + milli : ''}${zone}`);
  requireFact(Number.isFinite(parsed) && parsed <= now);
  return new Date(parsed).toISOString();
}
function readContext(value: unknown): QuantitySnapshot['context'] {
  const row = object(value);
  const userId = normalizeBusinessId(row.userId);
  requireFact(typeof row.tenantId === 'string' && /^\d{1,20}$/.test(row.tenantId) && userId && typeof row.confirmAllowed === 'boolean');
  return Object.freeze({ tenantId: row.tenantId as string, userId, confirmAllowed: row.confirmAllowed as boolean });
}
function readUnit(value: unknown, expectedId: string, now: number): QuantityUnitSnapshot {
  const row = object(value);
  const id = normalizeBusinessId(row.uomId),
    primaryId = row.primaryId === null ? null : normalizeBusinessId(row.primaryId);
  const isBase = row.isBase === 1 || row.isBase === '1' ? 1 : row.isBase === 0 || row.isBase === '0' ? 0 : null;
  requireFact(
    id === expectedId &&
      isBase !== null &&
      (row.status === 1 || row.status === '1') &&
      text(row.category, 64) &&
      text(row.uomCode, 64) &&
      text(row.uomName, 128)
  );
  // 与现有服务完全一致：isBase=1 明确以本单位为根，首次 NULL 原映射不改写快照。
  // 仅此自映射合同可供人工确认；衍生单位缺根、已有根矛盾或多级链仍封闭。
  requireFact(isBase === 1 ? primaryId === null || primaryId === id : primaryId !== null && primaryId !== id);
  const originalVersion = version(row.conversionVersion);
  const at = verifiedDate(row.conversionVerifiedAt, now);
  const rate = row.conversionRate === null ? null : decimal(row.conversionRate);
  if (isBase === 1) requireFact(rate === null || rate === '1');
  const scale = row.quantityScale;
  requireFact(scale === null || (typeof scale === 'number' && Number.isInteger(scale) && scale >= 0 && scale <= 4));
  if (originalVersion !== null) requireFact(at !== null && rate !== null && scale !== null && (isBase !== 1 || primaryId === id));
  else requireFact(at === null);
  return Object.freeze({
    uomId: id,
    uomCode: row.uomCode as string,
    uomName: row.uomName as string,
    category: row.category as string,
    isBase,
    primaryId,
    status: 1,
    conversionRate: rate,
    quantityScale: scale as number | null,
    conversionVersion: originalVersion,
    conversionVerifiedAt: at
  });
}
function certifiedRoot(root: QuantityUnitSnapshot): boolean {
  return (
    root.isBase === 1 &&
    root.uomId === root.primaryId &&
    root.conversionRate === '1' &&
    root.quantityScale !== null &&
    root.conversionVersion !== null &&
    root.conversionVerifiedAt !== null
  );
}
function data<T>(response: CertifiedBusinessResponse<T>): T {
  requireFact(response && response.code === 200 && response.data !== undefined && response.data !== null);
  return response.data as T;
}
function scope(snapshot: QuantitySnapshot): BusinessScope {
  return { tenantId: snapshot.context.tenantId, userId: snapshot.context.userId, feature: 'quantity-confirmation', documentId: snapshot.unit.uomId };
}
function equalSnapshot(left: QuantitySnapshot, right: QuantitySnapshot): boolean {
  return JSON.stringify(left) === JSON.stringify(right);
}
function matchesConfirmation(unit: QuantityUnitSnapshot, original: QuantitySnapshot, body: QuantityConfirmationBody): boolean {
  return (
    unit.uomId === original.unit.uomId &&
    unit.primaryId === original.root.uomId &&
    unit.isBase === original.unit.isBase &&
    unit.category === original.unit.category &&
    unit.uomCode === original.unit.uomCode &&
    unit.uomName === original.unit.uomName &&
    unit.conversionVersion !== null &&
    unit.conversionVersion !== body.expectedVersion &&
    unit.conversionVerifiedAt !== null &&
    (original.unit.conversionVerifiedAt === null || unit.conversionVerifiedAt >= original.unit.conversionVerifiedAt) &&
    unit.conversionRate === decimal(body.conversionRate) &&
    unit.quantityScale === body.quantityScale
  );
}

/**
 * 单位数量核验状态机：固定会话、先持久意图后派发、明确根/原版本的 fresh preflight。
 * UNKNOWN 以主体/功能/单位持久化，GET、换版本和重开都不释放；此状态机不签发库存或物料认证。
 */
export function createQuantityConfirmation(ports: QuantityConfirmationPorts) {
  const state = ports.state ?? createQuantityConfirmationState();
  const store = createBusinessIntentStore(ports.storage);
  let epoch = 0;
  let currentSession: CertifiedBusinessSession | null = null;
  let currentScope: BusinessScope | null = null;
  const now = ports.now ?? Date.now;

  function isBlocked(): boolean {
    if (!currentScope) return false;
    try {
      return store.isBlocked(currentScope);
    } catch {
      return true;
    }
  }
  function body(): QuantityConfirmationBody {
    const snapshot = state.snapshot;
    requireFact(snapshot);
    const form = state.form;
    requireFact(typeof form.conversionRate === 'string' && typeof form.quantityScale === 'string' && text(form.reference, 256));
    const rate = decimal(form.conversionRate);
    requireFact(/^[0-4]$/.test(form.quantityScale));
    if (snapshot.unit.isBase === 1) requireFact(rate === '1');
    return {
      expectedVersion: snapshot.unit.conversionVersion,
      primaryId: snapshot.root.uomId,
      conversionRate: form.conversionRate,
      quantityScale: Number(form.quantityScale),
      reference: form.reference
    };
  }
  function canSubmit(): boolean {
    if (!['ready', 'rejected'].includes(state.phase) || !state.snapshot?.context.confirmAllowed || isBlocked()) return false;
    try {
      currentSession?.assertCurrent();
      body();
      return currentSession !== null;
    } catch {
      return false;
    }
  }
  function assertAlive(session: CertifiedBusinessSession, capturedEpoch: number): void {
    requireFact(capturedEpoch === epoch && currentSession === session);
    session.assertCurrent();
  }
  async function readSnapshot(id: string, session: CertifiedBusinessSession, capturedEpoch: number, bindScope = false): Promise<QuantitySnapshot> {
    assertAlive(session, capturedEpoch);
    const context = readContext(await ports.api.getContext(session));
    assertAlive(session, capturedEpoch);
    // 主体一经 fresh 验明就绑定文档锁；后续坏详情/新族版本不能把已知 UNKNOWN 隐藏掉。
    if (bindScope) currentScope = { tenantId: context.tenantId, userId: context.userId, feature: 'quantity-confirmation', documentId: id };
    const unit = readUnit(await ports.api.getDetail(id, session), id, now());
    assertAlive(session, capturedEpoch);
    const root = unit.isBase === 1 ? unit : readUnit(await ports.api.getDetail(unit.primaryId, session), unit.primaryId, now());
    assertAlive(session, capturedEpoch);
    if (unit.isBase === 0) {
      requireFact(
        certifiedRoot(root) &&
          root.category === unit.category &&
          (unit.conversionVersion === null || unit.conversionVersion === root.conversionVersion)
      );
    }
    // VO 没有租户字段：读完实际单位/根后再读取主体，避免同 token 动态租户/权限在读取途中切换。
    const endingContext = readContext(await ports.api.getContext(session));
    assertAlive(session, capturedEpoch);
    requireFact(JSON.stringify(context) === JSON.stringify(endingContext));
    return Object.freeze({ context, unit, root });
  }
  async function open(rawId: unknown): Promise<void> {
    const capturedEpoch = ++epoch;
    currentSession = null;
    currentScope = null;
    state.documentId = null;
    state.snapshot = null;
    state.message = '';
    Object.assign(state.form, { conversionRate: '', quantityScale: '', reference: '' });
    state.phase = 'loading';
    try {
      const id = normalizeBusinessId(rawId),
        route = ports.readRouteKey();
      requireFact(id && normalizeBusinessId(ports.readDocumentId()) === id);
      state.documentId = id;
      const session = createBusinessSession(ports.readCredential, () => {
        requireFact(
          capturedEpoch === epoch && state.documentId === id && normalizeBusinessId(ports.readDocumentId()) === id && ports.readRouteKey() === route
        );
      });
      currentSession = session;
      const snapshot = await readSnapshot(id, session, capturedEpoch, true);
      assertAlive(session, capturedEpoch);
      currentScope = scope(snapshot);
      state.snapshot = snapshot;
      state.phase = isBlocked() ? 'unknown' : 'ready';
      state.message = isBlocked()
        ? UNKNOWN
        : snapshot.context.confirmAllowed
          ? '请依据真实资料手工明确比例、精度和证据；不会从旧值自动填充。'
          : '当前主体仅可查询，不能执行数量确认。权限提示不是认证。';
    } catch {
      if (capturedEpoch === epoch) {
        state.phase = isBlocked() ? 'unknown' : 'invalid';
        state.message = isBlocked() ? UNKNOWN : INVALID;
      }
    }
  }
  function close(): void {
    ++epoch;
    currentSession = null;
    currentScope = null;
    state.phase = 'closed';
    state.snapshot = null;
    state.documentId = null;
    state.message = '';
    Object.assign(state.form, { conversionRate: '', quantityScale: '', reference: '' });
    // 不操作持久意图：关闭窗口无法证明 POST 没有提交。
  }
  async function submit(): Promise<boolean> {
    if (!canSubmit()) return false;
    const original = state.snapshot!,
      session = currentSession!,
      capturedEpoch = epoch;
    const request = Object.freeze(body());
    state.phase = 'submitting';
    state.message = '正在重新读取当前主体及原生单位资料…';
    let intent: ReturnType<typeof store.begin> | null = null;
    try {
      const fresh = await readSnapshot(original.unit.uomId, session, capturedEpoch);
      assertAlive(session, capturedEpoch);
      requireFact(fresh.context.confirmAllowed && equalSnapshot(original, fresh));
      requireFact(!store.isBlocked(scope(original)));
      const key = ports.requestKey();
      requireFact(text(key, 128));
      intent = store.begin(scope(original), 'confirm-quantity', key);
      store.assertPending(intent);
      assertAlive(session, capturedEpoch);
      const response = await ports.api.confirm(original.unit.uomId, request, session);
      assertAlive(session, capturedEpoch);
      if (Number.isInteger(response?.code) && response.code >= 400 && response.code < 500) {
        store.release(intent);
        state.phase = 'rejected';
        state.message = '服务端明确拒绝本次确认，未自动重发。请核对权限、原版本及资料。';
        return false;
      }
      const confirmed = readUnit(data(response), original.unit.uomId, now());
      requireFact(matchesConfirmation(confirmed, original, request));
      const reread = await readSnapshot(original.unit.uomId, session, capturedEpoch);
      assertAlive(session, capturedEpoch);
      requireFact(
        reread.context.tenantId === original.context.tenantId &&
          reread.context.userId === original.context.userId &&
          reread.context.confirmAllowed &&
          matchesConfirmation(reread.unit, original, request) &&
          JSON.stringify(reread.unit) === JSON.stringify(confirmed) &&
          certifiedRoot(reread.root)
      );
      store.assertPending(intent);
      store.release(intent);
      state.snapshot = reread;
      state.phase = 'confirmed';
      state.message = '已收到有效确认响应并完成同主体原生回读。仅本次实际单位换算资料已核验，不代表历史库存或全物料认证。';
      return true;
    } catch {
      // 真实 helper 已将明确 HTTP/R4xx 返回为信封；抛出的错误没有可释放资格。
      if (capturedEpoch !== epoch || currentSession !== session) return false;
      try {
        assertAlive(session, capturedEpoch);
      } catch {
        return false;
      }
      if (intent || isBlocked()) {
        state.phase = 'unknown';
        state.message = UNKNOWN;
      } else {
        state.phase = 'invalid';
        state.message = '写前复核未通过或无法持久保存意图，未派发确认。请重新读取当前单位及真实资料。';
      }
      return false;
    }
  }
  return { state, open, close, submit, canSubmit, isBlocked };
}
