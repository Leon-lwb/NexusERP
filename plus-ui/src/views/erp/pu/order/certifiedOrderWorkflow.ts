import type { CertifiedOrderContext, CertifiedOrderDetail, CertifiedOrderQuote } from '@/api/erp/pu/order/types';
import { createBusinessSession, createBusinessIntentStore, normalizeBusinessId, normalizeBusinessVersion } from '@/utils/certified-business';
import type { CertifiedBusinessSession, CertifiedBusinessResponse, BusinessScope, BusinessIntent } from '@/utils/certified-business';

export type CertifiedOrderOperation = 'terms' | 'confirm' | 'approve' | 'close' | 'cancel';
export type CertifiedOrderPhase = 'IDLE' | 'LOADING' | 'READY' | 'SENDING' | 'SUCCEEDED' | 'REFUSED' | 'UNKNOWN' | 'BLOCKED';
export interface CertifiedOrderSnapshot {
  phase: CertifiedOrderPhase;
  orderId: string | null;
  context: CertifiedOrderContext | null;
  detail: CertifiedOrderDetail | null;
  readOnly: boolean;
  message: string;
}
export interface CertifiedOrderDependencies {
  readCredential: () => string | null | undefined;
  readContext: (session: CertifiedBusinessSession) => Promise<CertifiedOrderContext>;
  readDetail: (id: string, session: CertifiedBusinessSession) => Promise<CertifiedOrderDetail>;
  submit: (
    operation: CertifiedOrderOperation,
    id: string,
    body: Record<string, unknown>,
    session: CertifiedBusinessSession
  ) => Promise<CertifiedBusinessResponse<unknown>>;
  storage: { getItem(key: string): string | null; setItem(key: string, value: string): void; removeItem(key: string): void };
  onChange?: (snapshot: CertifiedOrderSnapshot) => void;
}
const FLAGS = ['termsAddAllowed', 'termsConfirmAllowed', 'approveAllowed', 'closeAllowed', 'cancelAllowed', 'priceVisible'] as const;
const TERMS = [
  'unitPriceExcludingTax',
  'taxRate',
  'currency',
  'exchangeRate',
  'promisedArrivalDate',
  'validFrom',
  'validUntil',
  'supplierQuoteReference',
  'supplierContact',
  'evidenceReference',
  'evidenceSha256',
  'paymentTerms',
  'deliveryTerms'
] as const;
const ORDER = 'ORDER_COMMITTED_NOT_TIMELY_SUPPLY';
const RECEIVING = ['DRAFT', 'APPROVED', 'PARTIALLY_RECEIVED', 'PARTIAL_RECEIVED', 'RECEIVED'];
const copy = <T>(value: T): T => JSON.parse(JSON.stringify(value));
function check(ok: unknown, message: string): asserts ok {
  if (!ok) throw new Error(message);
}
function object(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}
function text(value: unknown, max: number): value is string {
  return typeof value === 'string' && value.length > 0 && value.length <= max && value.trim() === value;
}
function date(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const d = new Date(value + 'T00:00:00Z');
  return Number.isFinite(d.getTime()) && d.toISOString().slice(0, 10) === value;
}
function decimal(value: unknown, zero = false): value is string {
  return typeof value === 'string' && /^(?:0|[1-9]\d{0,13})(?:\.\d{1,4})?$/.test(value) && (zero || /[1-9]/.test(value));
}
/** 仅比较十进制文本的同值表示，不用Number、不计算金额、税额或数量。 */
function decimalIdentity(value: unknown): string | null {
  if (typeof value !== 'string' || value.length > 128 || !/^\d+(?:\.\d+)?$/.test(value)) return null;
  const [whole, fraction = ''] = value.split('.');
  const integer = whole.replace(/^0+(?=\d)/, '');
  const tail = fraction.replace(/0+$/, '');
  return integer + (tail ? '.' + tail : '');
}
function hash(value: unknown): value is string {
  return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value);
}
function context(value: CertifiedOrderContext): CertifiedOrderContext {
  check(object(value) && typeof value.tenantId === 'string' && /^\d{6,20}$/.test(value.tenantId), '订单当前租户未确认');
  normalizeBusinessId(value.userId);
  for (const flag of FLAGS) check(typeof value[flag] === 'boolean', '订单权限快照未确认');
  return copy(value);
}
function sameActor(a: CertifiedOrderContext, b: CertifiedOrderContext): boolean {
  return a.tenantId === b.tenantId && normalizeBusinessId(a.userId) === normalizeBusinessId(b.userId) && FLAGS.every((k) => a[k] === b[k]);
}
function detail(value: CertifiedOrderDetail, actor: CertifiedOrderContext, id: string): CertifiedOrderDetail {
  check(
    object(value) && object(value.order) && normalizeBusinessId(value.order.orderId) === id && value.order.tenantId === actor.tenantId,
    '订单详情归属未确认'
  );
  check(
    Array.isArray(value.quotes) && value.quotes.length <= 100 && Array.isArray(value.allocations) && value.allocations.length <= 200,
    '报价或占用身份全集未确认'
  );
  check(
    typeof value.priceVisible === 'boolean' &&
      value.priceVisible === actor.priceVisible &&
      date(value.serverDate) &&
      typeof value.genericEditAllowedHint === 'boolean',
    '订单读取策略或日期未确认'
  );
  check(text(value.order.orderNo, 128) && text(value.order.status, 64), '订单头原生字段不完整');
  const quoteIds = new Set<string>();
  for (const q of value.quotes) {
    const qid = normalizeBusinessId(q.quoteId);
    check(
      normalizeBusinessId(q.orderId) === id && !quoteIds.has(qid) && Number.isInteger(q.quoteVersion) && q.quoteVersion > 0 && hash(q.quoteHash),
      '报价版本或归属未确认'
    );
    normalizeBusinessId(q.enteredBy);
    quoteIds.add(qid);
  }
  check(actor.priceVisible || value.quotes.length === 0, '当前角色不可读取报价价格');
  return copy(value);
}
/** 原生认证提示、版本和占用身份必须完整；普通或未知单不转授认证写资格。 */
function candidate(d: CertifiedOrderDetail, actor: CertifiedOrderContext): void {
  const h = d.order;
  check(h.originalCommandPresent === true && !d.genericEditAllowedHint && h.selectFlowId == null, '原永久来源未确认或已绑定普通审批流程');
  normalizeBusinessId(h.requisitionId);
  normalizeBusinessId(h.vendorId);
  if (h.requisitionSourceType === 'AI_DECISION') {
    normalizeBusinessId(h.decisionRunId);
    normalizeBusinessId(h.decisionActionId);
  } else check(h.requisitionSourceType === 'MANUAL' && h.decisionRunId == null && h.decisionActionId == null, '订单来源类型未确认');
  check(text(h.sourceRequestKey, 128), '原始来源键未确认');
  check(normalizeBusinessVersion(h.version) < 2147483647, '原生版本没有可用递增空间');
  check(d.allocations.length > 0, '原生逐来源占用身份缺失');
  const seen = new Set<string>();
  for (const a of d.allocations) {
    const id = normalizeBusinessId(a.allocationId);
    check(!seen.has(id), '占用身份重复');
    seen.add(id);
    normalizeBusinessId(a.requisitionLineId);
    normalizeBusinessId(a.orderLineId);
    normalizeBusinessVersion(a.version);
    check(a.allocationStatus === ORDER || a.allocationStatus === 'RELEASED', '占用状态未确认');
  }
  check(h.tenantId === actor.tenantId, '订单租户变化');
}
function currentQuote(d: CertifiedOrderDetail, id: unknown): CertifiedOrderQuote {
  const key = normalizeBusinessId(id);
  const q = d.quotes.find((q) => normalizeBusinessId(q.quoteId) === key);
  check(q && d.priceVisible && q.quoteVersion === Math.max(...d.quotes.map((q) => q.quoteVersion)), '必须选择当前可见的最新原报价');
  check(date(q.validFrom) && date(q.validUntil) && q.validFrom <= d.serverDate && q.validUntil >= d.serverDate, '报价尚未生效或已失效');
  return q;
}
function payload(
  op: CertifiedOrderOperation,
  input: Record<string, unknown>,
  d: CertifiedOrderDetail,
  a: CertifiedOrderContext,
  key: string
): Record<string, unknown> {
  candidate(d, a);
  const permission = {
    terms: 'termsAddAllowed',
    confirm: 'termsConfirmAllowed',
    approve: 'approveAllowed',
    close: 'closeAllowed',
    cancel: 'cancelAllowed'
  } as const;
  check(a[permission[op]], '当前角色没有此认证操作权限');
  const version = normalizeBusinessVersion(d.order.version);
  if (op === 'terms') {
    check(d.order.status === 'DRAFT', '只有原生草稿可录入真实供应条款');
    check(
      Object.keys(input).length === TERMS.length && TERMS.every((k) => Object.hasOwn(input, k)),
      '报价字段必须完整且不能夹带身份、数量或认证标记'
    );
    check(
      decimal(input.unitPriceExcludingTax) &&
        decimal(input.taxRate, true) &&
        (input.taxRate.startsWith('0') || /^1(?:\.0{1,4})?$/.test(input.taxRate)),
      '原价格或税率超出支持域'
    );
    check(input.currency === 'CNY' && input.exchangeRate === '1', '只支持经实际确认的CNY/汇率1条款');
    for (const k of ['promisedArrivalDate', 'validFrom', 'validUntil'] as const) check(date(input[k]), '报价日期不是有效日历日期');
    check(
      (input.validFrom as string) <= d.serverDate &&
        (input.validUntil as string) >= d.serverDate &&
        (input.promisedArrivalDate as string) >= d.serverDate &&
        (input.validUntil as string) >= (input.validFrom as string),
      '报价有效期或供应承诺日期不支持'
    );
    for (const k of ['supplierQuoteReference', 'supplierContact'] as const) check(text(input[k], 128), '供应商报价资料缺失');
    for (const k of ['evidenceReference', 'paymentTerms', 'deliveryTerms'] as const) check(text(input[k], 255), '条款或证据缺失');
    check(hash(input.evidenceSha256), '证据摘要未确认');
    return { ...copy(input), requestKey: key, expectedOrderVersion: version };
  }
  if (op === 'confirm') {
    check(Object.keys(input).length === 2 && Object.hasOwn(input, 'quoteId') && text(input.reviewNote, 500), '复核字段缺失或夹带不允许字段');
    check(d.order.status === 'DRAFT', '只有草稿可确认原报价');
    const q = currentQuote(d, input.quoteId);
    check(normalizeBusinessId(q.enteredBy) !== normalizeBusinessId(a.userId), '报价必须由不同操作人独立复核');
    check(q.confirmationId == null && q.confirmedBy == null && q.confirmationHash == null, '原报价已确认，不能另起键重复复核');
    return { quoteId: normalizeBusinessId(q.quoteId), quoteHash: q.quoteHash, reviewNote: input.reviewNote, requestKey: key };
  }
  if (op === 'approve') {
    check(Object.keys(input).length === 1 && Object.hasOwn(input, 'quoteId') && d.order.status === 'DRAFT', '批准必须明确选择原草稿报价');
    const q = currentQuote(d, input.quoteId);
    normalizeBusinessId(q.confirmationId);
    normalizeBusinessId(q.confirmedBy);
    check(normalizeBusinessId(q.enteredBy) !== normalizeBusinessId(q.confirmedBy) && q.confirmationHash === q.quoteHash, '原报价缺少独立有效确认');
    return { expectedVersion: version, quoteId: normalizeBusinessId(q.quoteId), requestKey: key };
  }
  check(Object.keys(input).length === 0 && RECEIVING.includes(d.order.status), '关闭或取消只能处理当前支持状态，不接收外部状态或数量');
  return { expectedVersion: version, requestKey: key };
}
function receipt(
  op: CertifiedOrderOperation,
  raw: unknown,
  original: CertifiedOrderDetail,
  actor: CertifiedOrderContext,
  body: Record<string, unknown>,
  after: CertifiedOrderDetail
): void {
  check(object(raw), '写入成功回执缺失');
  const immutable = [
    'tenantId',
    'orderId',
    'orderNo',
    'requisitionId',
    'vendorId',
    'selectFlowId',
    'requisitionSourceType',
    'decisionRunId',
    'decisionActionId',
    'sourceRequestKey',
    'originalCommandPresent'
  ] as const;
  check(
    immutable.every((k) => original.order[k] === after.order[k]),
    '成功后原生订单来源身份变化'
  );
  if (op === 'terms' || op === 'confirm') {
    // 报价及复核服务不改变占用；核对身份全集、状态和原生版本，不计算数量或 KPI。
    check(after.allocations.length === original.allocations.length, '报价后原占用全集变化');
    const seen = new Set<string>();
    for (const now of after.allocations) {
      const id = normalizeBusinessId(now.allocationId);
      const old = original.allocations.find((a) => normalizeBusinessId(a.allocationId) === id);
      check(
        old &&
          !seen.has(id) &&
          normalizeBusinessId(now.requisitionLineId) === normalizeBusinessId(old.requisitionLineId) &&
          normalizeBusinessId(now.orderLineId) === normalizeBusinessId(old.orderLineId) &&
          now.allocationStatus === old.allocationStatus &&
          normalizeBusinessVersion(now.version) === normalizeBusinessVersion(old.version),
        '报价后原占用身份、状态或版本变化'
      );
      seen.add(id);
    }
    const q = raw.quote;
    check(
      object(q) &&
        q.tenantId === actor.tenantId &&
        normalizeBusinessId(q.orderId) === normalizeBusinessId(original.order.orderId) &&
        hash(q.quoteHash),
      '报价回执不属于原租户订单'
    );
    const saved = after.quotes.find((x) => normalizeBusinessId(x.quoteId) === normalizeBusinessId(q.quoteId));
    check(
      saved &&
        saved.quoteHash === q.quoteHash &&
        saved.quoteVersion === q.quoteVersion &&
        after.order.version === original.order.version &&
        after.order.status === original.order.status,
      '原生报价读回与回执不一致'
    );
    if (op === 'terms') {
      check(
        q.requestKey === body.requestKey &&
          normalizeBusinessId(q.enteredBy) === normalizeBusinessId(actor.userId) &&
          saved.requestKey === body.requestKey &&
          normalizeBusinessId(saved.enteredBy) === normalizeBusinessId(actor.userId),
        '报价回执不属于本次原意图'
      );
      for (const k of TERMS) {
        const numeric = ['unitPriceExcludingTax', 'taxRate', 'exchangeRate'].includes(k);
        check(
          numeric ? decimalIdentity(saved[k]) !== null && decimalIdentity(saved[k]) === decimalIdentity(body[k]) : saved[k] === body[k],
          '持久报价条款与原输入不一致'
        );
      }
    } else {
      const c = raw.confirmation;
      check(
        object(c) &&
          c.tenantId === actor.tenantId &&
          normalizeBusinessId(c.quoteId) === normalizeBusinessId(body.quoteId) &&
          c.quoteHash === body.quoteHash &&
          normalizeBusinessId(c.actorId) === normalizeBusinessId(actor.userId) &&
          c.requestKey === body.requestKey,
        '复核回执未绑定原报价及本次主体'
      );
      check(
        normalizeBusinessId(saved.confirmationId) === normalizeBusinessId(c.confirmationId) &&
          normalizeBusinessId(saved.confirmedBy) === normalizeBusinessId(actor.userId) &&
          saved.confirmationHash === body.quoteHash &&
          saved.reviewNote === body.reviewNote,
        '原生独立确认读回不一致'
      );
    }
    return;
  }
  const status = { approve: 'APPROVED', close: 'CLOSED', cancel: 'CANCELLED' }[op];
  check(
    normalizeBusinessId(raw.orderId) === normalizeBusinessId(original.order.orderId) &&
      raw.orderNo === original.order.orderNo &&
      raw.requestKey === body.requestKey &&
      raw.status === status &&
      raw.version === normalizeBusinessVersion(original.order.version) + 1 &&
      after.order.status === status &&
      after.order.version === raw.version,
    '订单回执身份、状态或版本不一致'
  );
  if (op === 'approve')
    check(
      normalizeBusinessId(raw.quoteId) === normalizeBusinessId(body.quoteId) &&
        normalizeBusinessId(after.order.quoteId) === normalizeBusinessId(body.quoteId),
      '批准回执移植其他报价'
    );
  check(
    Array.isArray(raw.allocations) &&
      raw.allocations.length === original.allocations.length &&
      after.allocations.length === original.allocations.length,
    '逐来源回执全集不完整'
  );
  const seen = new Set<string>();
  const operation = { approve: 'APPROVE_ORDER', close: 'CLOSE_ORDER', cancel: 'CANCEL_ORDER' }[op];
  for (const e of raw.allocations) {
    check(object(e), '逐来源回执无效');
    normalizeBusinessId(e.eventId);
    const id = normalizeBusinessId(e.allocationId);
    const old = original.allocations.find((x) => normalizeBusinessId(x.allocationId) === id);
    const now = after.allocations.find((x) => normalizeBusinessId(x.allocationId) === id);
    check(old && now && !seen.has(id), '回执占用不属于原订单');
    seen.add(id);
    check(
      normalizeBusinessId(e.requisitionLineId) === normalizeBusinessId(old.requisitionLineId) &&
        normalizeBusinessId(e.orderLineId) === normalizeBusinessId(old.orderLineId) &&
        normalizeBusinessId(now.requisitionLineId) === normalizeBusinessId(old.requisitionLineId) &&
        normalizeBusinessId(now.orderLineId) === normalizeBusinessId(old.orderLineId),
      '原生回执行身份移植'
    );
    // 三种生命周期操作均经 ledger.updateWithVersion CAS，每个原占用的 native version 恰好 +1。
    check(normalizeBusinessVersion(now.version) === normalizeBusinessVersion(old.version) + 1, '原生占用版本未按本次生命周期CAS递增');
    check(
      normalizeBusinessId(e.actorId) === normalizeBusinessId(actor.userId) &&
        e.operation === operation &&
        e.fromStatus === old.allocationStatus &&
        e.toStatus === (op === 'approve' ? ORDER : 'RELEASED') &&
        now.allocationStatus === e.toStatus,
      '回执主体、操作或实际状态不一致'
    );
  }
}

/** 专属订单协议：fresh读、原意图固定、成功后原生对账；不自动串联操作或解除UNKNOWN。 */
export function createCertifiedOrderWorkflow(deps: CertifiedOrderDependencies) {
  const store = createBusinessIntentStore(deps.storage);
  let state: CertifiedOrderSnapshot = { phase: 'IDLE', orderId: null, context: null, detail: null, readOnly: false, message: '' };
  let session: CertifiedBusinessSession | null = null;
  let epoch = 0;
  let busy = false;
  function snapshot(): CertifiedOrderSnapshot {
    try {
      session?.assertCurrent();
      return copy(state);
    } catch {
      return { phase: 'BLOCKED', orderId: state.orderId, context: null, detail: null, readOnly: true, message: '当前会话已变化，请重新打开核验。' };
    }
  }
  function emit() {
    deps.onChange?.(snapshot());
  }
  function scope(a: CertifiedOrderContext, id: string): BusinessScope {
    return { tenantId: a.tenantId, userId: normalizeBusinessId(a.userId), feature: 'certified-order', documentId: id };
  }
  async function load(id: string, s: CertifiedBusinessSession): Promise<{ actor: CertifiedOrderContext; document: CertifiedOrderDetail }> {
    const actor = context(await deps.readContext(s));
    s.assertCurrent();
    const document = detail(await deps.readDetail(id, s), actor, id);
    s.assertCurrent();
    return { actor, document };
  }
  async function open(id: unknown, options: { readOnly?: boolean } = {}) {
    if (busy) return;
    const turn = ++epoch;
    state = { phase: 'LOADING', orderId: normalizeBusinessId(id), context: null, detail: null, readOnly: options.readOnly === true, message: '' };
    session = createBusinessSession(deps.readCredential, () => check(turn === epoch, '页面已经切换'));
    emit();
    try {
      const data = await load(state.orderId!, session);
      check(turn === epoch, '页面已变化');
      state.context = data.actor;
      state.detail = data.document;
      if (store.isBlocked(scope(data.actor, state.orderId!))) {
        state.phase = 'UNKNOWN';
        state.message = '本单存在原未知意图，只能只读核对，禁止换键重发。';
      } else {
        try {
          candidate(data.document, data.actor);
          state.phase = 'READY';
        } catch {
          state.phase = 'BLOCKED';
          state.message = '原生来源、版本或占用资料不完整，当前仅可查看；不能猜默认值。';
        }
      }
    } catch {
      state.phase = 'BLOCKED';
      state.message = '当前主体或原生订单读取未确认。';
    }
    emit();
  }
  async function refresh() {
    if (state.orderId && !busy) await open(state.orderId, { readOnly: state.readOnly });
  }
  async function execute(operation: CertifiedOrderOperation, input: Record<string, unknown>) {
    if (busy || state.readOnly || !state.orderId || !state.context || !session) return;
    if (!['terms', 'confirm', 'approve', 'close', 'cancel'].includes(operation)) return;
    busy = true;
    const s = session;
    const id = state.orderId;
    const bound = copy(state.context);
    const turn = epoch;
    const originalInput = copy(input);
    const reviewed = state.detail ? copy(state.detail) : null;
    let intent: BusinessIntent | null = null;
    let dispatched = false;
    state.phase = 'LOADING';
    state.message = '';
    emit();
    try {
      s.assertCurrent();
      check(!store.isBlocked(scope(bound, id)), '原意图未释放');
      const fresh = await load(id, s);
      check(sameActor(bound, fresh.actor), '当前主体或权限变化');
      check(reviewed && JSON.stringify(reviewed) === JSON.stringify(fresh.document), '用户确认后原生事实变化，请只读刷新重新复核');
      const key = 'pc-order-' + operation + '-' + crypto.randomUUID();
      const body = payload(operation, originalInput, fresh.document, fresh.actor, key);
      intent = store.begin(scope(bound, id), operation, key);
      store.assertPending(intent);
      s.assertCurrent();
      state.phase = 'SENDING';
      emit();
      dispatched = true;
      const response = await deps.submit(operation, id, body, s);
      s.assertCurrent();
      check(turn === epoch, '页面已切换');
      if (response?.code >= 400 && response.code < 500) {
        store.release(intent);
        state.phase = 'REFUSED';
        state.message = '服务端明确拒绝本次操作，请核对权限及资料；不会自动重发。';
      } else {
        check(response?.code === 200, '写入结果未知');
        const after = await load(id, s);
        check(sameActor(bound, after.actor), '响应期间主体或权限变化');
        receipt(operation, response.data, fresh.document, bound, body, after.document);
        store.release(intent);
        state.detail = after.document;
        state.context = after.actor;
        state.phase = 'SUCCEEDED';
        state.message = '服务端成功且原生结果已回查；下一步必须另行人工确认。';
      }
    } catch {
      state.phase = dispatched || intent ? 'UNKNOWN' : 'BLOCKED';
      state.message =
        dispatched || intent
          ? '本次结果或保护记录未确认，保留原意图；只读刷新不能授予重发资格。'
          : '权限、原生来源或实际资料未确认，本次未派发业务操作。';
    } finally {
      busy = false;
      emit();
    }
  }
  function close() {
    if (busy) return false;
    epoch++;
    session = null;
    state = { phase: 'IDLE', orderId: null, context: null, detail: null, readOnly: false, message: '' };
    emit();
    return true;
  }
  function invalidate() {
    epoch++;
    session = null;
    state = {
      phase: 'BLOCKED',
      orderId: state.orderId,
      context: null,
      detail: null,
      readOnly: true,
      message: '页面或当前身份已经变化，原意图保留，请重新核验。'
    };
    emit();
  }
  return { open, refresh, execute, snapshot, close, invalidate };
}
