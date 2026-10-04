import type { CertifiedPurchaseContext, CertifiedPurchaseSession } from '@/api/erp/pu/requisition';
import type {
  CertifiedPurchaseSource,
  CertifiedPurchaseSourcePreview,
  CertifiedPurchaseResult,
  CertifiedPurchaseResponse,
  PuCertifiedPurchaseCommand
} from '@/api/erp/pu/requisition/types';

export type CertifiedPurchasePhase = 'editing' | 'previewing' | 'ready' | 'submitting' | 'unknown' | 'rejected' | 'succeeded' | 'blocked';

export interface CertifiedPurchaseState {
  source: CertifiedPurchaseSource;
  requestKey: string;
  preview: CertifiedPurchaseSourcePreview | null;
  command: Readonly<PuCertifiedPurchaseCommand> | null;
  result: CertifiedPurchaseResult | null;
  phase: CertifiedPurchasePhase;
  message: string;
}

interface FlowDependencies {
  state: CertifiedPurchaseState;
  preview: (source: CertifiedPurchaseSource) => Promise<CertifiedPurchaseResponse<CertifiedPurchaseSourcePreview>>;
  create: (command: PuCertifiedPurchaseCommand) => Promise<CertifiedPurchaseResponse<CertifiedPurchaseResult>>;
  newRequestKey: () => string;
  storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;
  storageKey: string;
}

export const createCertifiedPurchaseState = (): CertifiedPurchaseState => ({
  source: { materialId: '', vendorId: '', windowEnd: '', requirementDetailIds: [] },
  requestKey: '',
  preview: null,
  command: null,
  result: null,
  phase: 'editing',
  message: ''
});

/** 保留原始十进制 ID 文本，不经过 JS 数字转换。 */
export const parseRequirementDetailIds = (text: string): string[] => text.split(/[\s,，;；]+/).filter(Boolean);

/** 空记录只是未知，不以零或空数组伪装可靠库存/数量。 */
export const formatCertifiedValue = (value: unknown): string =>
  value === null || value === undefined || value === '' ? '未返回（不能按 0 处理）' : String(value);

const positiveId = (value: unknown): value is string =>
  typeof value === 'string' && /^[1-9]\d{0,18}$/.test(value) && (value.length < 19 || value <= '9223372036854775807');
const validKey = (value: unknown): value is string => typeof value === 'string' && /^[A-Za-z0-9:_-]{16,128}$/.test(value);
const validFingerprint = (value: unknown): value is string => typeof value === 'string' && /^[0-9a-f]{64}$/.test(value);
const date = (value: unknown): value is string =>
  typeof value === 'string' && /^[1-9]\d{3}-\d{2}-\d{2}$/.test(value) && new Date(value + 'T00:00:00Z').toISOString().slice(0, 10) === value;
const quantity = (value: unknown) => (typeof value === 'string' || typeof value === 'number') && /^\d+(?:\.\d+)?$/.test(String(value));
const errorMessage = (error: unknown) => (error instanceof Error ? error.message : typeof error === 'string' ? error : '未取得明确服务端结果');
const sameDetails = (expected: readonly string[], actual: readonly string[]) =>
  expected.length === actual.length && new Set(actual).size === actual.length && actual.every((id) => expected.includes(id));

function checkedSource(source: CertifiedPurchaseSource): CertifiedPurchaseSource {
  const result = {
    materialId: typeof source.materialId === 'string' ? source.materialId.trim() : source.materialId,
    vendorId: typeof source.vendorId === 'string' ? source.vendorId.trim() : source.vendorId,
    windowEnd: source.windowEnd,
    requirementDetailIds: Array.isArray(source.requirementDetailIds) ? [...source.requirementDetailIds] : []
  };
  if (
    !positiveId(result.materialId) ||
    !positiveId(result.vendorId) ||
    !date(result.windowEnd) ||
    result.requirementDetailIds.length === 0 ||
    result.requirementDetailIds.length > 1000 ||
    !result.requirementDetailIds.every(positiveId) ||
    new Set(result.requirementDetailIds).size !== result.requirementDetailIds.length
  ) {
    throw new Error('请填写真实物料/供应商字符串 ID、有效窗口日期及 1–1000 个不重复的需求明细字符串 ID');
  }
  return result;
}

function checkedPreview(preview: CertifiedPurchaseSourcePreview | undefined, source: CertifiedPurchaseSource): CertifiedPurchaseSourcePreview {
  if (
    !preview ||
    preview.materialId !== source.materialId ||
    preview.vendorId !== source.vendorId ||
    preview.windowEnd !== source.windowEnd ||
    !validFingerprint(preview.sourceFingerprint) ||
    !date(preview.snapshotDate) ||
    preview.catalog?.material?.materialId !== source.materialId ||
    !positiveId(preview.catalog?.baseUnit?.unitId) ||
    !positiveId(preview.catalog?.orderUnit?.unitId) ||
    !Array.isArray(preview.lines) ||
    preview.lines.length === 0 ||
    !sameDetails(
      source.requirementDetailIds,
      preview.lines.map((line) => line.detailId)
    ) ||
    !preview.lines.every(
      (line) =>
        positiveId(line.detailId) &&
        positiveId(line.baseUomId) &&
        positiveId(line.orderUomId) &&
        line.baseUomId === preview.catalog.baseUnit.unitId &&
        line.orderUomId === preview.catalog.orderUnit.unitId &&
        date(line.requiredDate) &&
        quantity(line.coveredDemandBase) &&
        quantity(line.orderQuantity)
    )
  ) {
    throw new Error('服务端完整来源预览缺失或与当前来源不符；无记录不能按 0 处理，禁止创建');
  }
  return preview;
}

function checkedResult(result: CertifiedPurchaseResult | undefined, command: PuCertifiedPurchaseCommand): CertifiedPurchaseResult {
  if (
    !result ||
    !positiveId(result.requisitionId) ||
    typeof result.requisitionNo !== 'string' ||
    !result.requisitionNo ||
    typeof result.status !== 'string' ||
    !result.status ||
    !Number.isInteger(result.version) ||
    result.version < 0 ||
    typeof result.replayed !== 'boolean' ||
    result.sourceFingerprint !== command.expectedSourceFingerprint ||
    !Array.isArray(result.lines) ||
    !sameDetails(
      command.requirementDetailIds,
      result.lines.map((line) => line.requirementDetailId)
    ) ||
    !result.lines.every(
      (line) =>
        positiveId(line.lineId) &&
        line.materialId === command.materialId &&
        positiveId(line.baseUomId) &&
        quantity(line.applyQuantity) &&
        date(line.requiredDate)
    )
  ) {
    throw new Error('写入响应缺失或与原请求不符，不能确认成功');
  }
  return result;
}

/** 有边界的本地请求状态机；服务端认证负责业务事实，前端只冻结意图和原载荷。 */
export function createCertifiedPurchaseFlow(deps: FlowDependencies) {
  const { state, storage, storageKey } = deps;
  let sequence = 0;
  let previewSource = '';
  let active = true;

  class SavedRequestError extends Error {}

  function assertSavedOwnership() {
    const text = storage.getItem(storageKey);
    if (!text) return;
    const saved = JSON.parse(text) as { requestKey: string; command: PuCertifiedPurchaseCommand | null };
    if (saved.requestKey !== state.requestKey || (saved.command && JSON.stringify(saved.command) !== JSON.stringify(state.command))) {
      throw new Error('发现另一份已保存的原请求或冻结载荷冲突；请重新打开并恢复原请求，禁止覆盖或清理旧记录');
    }
  }

  function persist() {
    try {
      // 另一窗口的待恢复意图也不能被本窗口的新键或变更载荷覆盖。
      assertSavedOwnership();
      const text = JSON.stringify({ requestKey: state.requestKey, source: state.source, command: state.command });
      storage.setItem(storageKey, text);
      if (storage.getItem(storageKey) !== text) throw new Error('本地原请求保存未确认，禁止发送');
    } catch (error) {
      throw new SavedRequestError(errorMessage(error));
    }
  }

  function editSource(source: CertifiedPurchaseSource): boolean {
    if (!active || state.command || state.phase === 'blocked') return false;
    sequence++;
    previewSource = '';
    state.source = { ...source, requirementDetailIds: [...source.requirementDetailIds] };
    state.preview = null;
    state.result = null;
    state.phase = 'editing';
    state.message = '';
    return true;
  }

  async function preview(): Promise<boolean> {
    if (!active || state.command || state.phase === 'blocked' || state.phase === 'previewing') return false;
    const current = ++sequence;
    state.preview = null;
    previewSource = '';
    state.message = '';
    try {
      const source = checkedSource(state.source);
      state.source = source;
      // 首次创建请求意图即分配永久键，不能在提交失败或恢复时临时重建。
      if (!state.requestKey) state.requestKey = deps.newRequestKey();
      if (!validKey(state.requestKey)) throw new Error('永久请求键无法安全生成，禁止继续');
      persist();
      state.phase = 'previewing';
      const response = await deps.preview(source);
      if (!active || current !== sequence) return false;
      if (response.code !== 200) throw new Error('来源拒绝：' + (response.msg || '服务端未通过完整核验'));
      state.preview = checkedPreview(response.data, source);
      previewSource = JSON.stringify(source);
      state.phase = 'ready';
      state.message = '服务端完整预览通过；创建时仍会重新核验同一来源，数量不可手工修改。';
      return true;
    } catch (error) {
      if (!active || current !== sequence) return false;
      state.preview = null;
      state.phase = error instanceof SavedRequestError ? 'blocked' : 'editing';
      state.message = '未通过完整来源预览：' + errorMessage(error);
      return false;
    }
  }

  async function send(): Promise<boolean> {
    const command = state.command;
    if (!active || !command) return false;
    try {
      // 必须先保存原载荷再发起写入，超时/断线/页面重载后只能恢复这份请求。
      persist();
    } catch (error) {
      state.phase = 'blocked';
      state.message = '原请求保存失败，本次未发送/重发，既有结果仍须核实：' + errorMessage(error);
      return false;
    }
    state.phase = 'submitting';
    state.result = null;
    state.message = '正在提交固定原请求，请勿另建请求键。';
    try {
      const response = await deps.create(command);
      if (!active) return false;
      if (response.code !== 200) {
        // 未明确业务类别的 R 5xx 也属于未知，不能通过新意图删除原键。
        if (typeof response.code !== 'number' || response.code < 400 || response.code >= 500) throw new Error('服务端未确认写入结果');
        state.phase = 'rejected';
        state.message = '服务端明确拒绝，未确认创建成功：' + (response.msg || '未通过业务校验');
        return false;
      }
      const result = checkedResult(response.data, command);
      state.result = result;
      state.phase = 'succeeded';
      state.message = result.replayed ? '已恢复原请求对应的原采购申请，未重复创建。' : '服务端已确认创建可信来源采购申请。';
      return true;
    } catch (error) {
      if (!active) return false;
      state.phase = 'unknown';
      state.message = '提交结果未知，不能视为成功或换键重试。请明确恢复原请求：' + errorMessage(error);
      return false;
    }
  }

  async function submit(): Promise<boolean> {
    if (!active || state.phase !== 'ready' || state.command || !state.preview || JSON.stringify(state.source) !== previewSource) {
      if (state.phase === 'editing') state.message = '请先完成当前来源的服务端完整预览';
      return false;
    }
    const source = checkedSource(state.source);
    state.command = Object.freeze({
      requestKey: state.requestKey,
      ...source,
      requirementDetailIds: Object.freeze([...source.requirementDetailIds]),
      expectedSourceFingerprint: state.preview.sourceFingerprint
    });
    return send();
  }

  /** 仅用户明确点击恢复后调用；不重新预览、不换键、不改载荷。 */
  async function recover(): Promise<boolean> {
    if (!active || state.phase !== 'unknown' || !state.command) return false;
    return send();
  }

  function startNew(): boolean {
    if (!active || state.phase === 'blocked' || state.phase === 'submitting' || state.phase === 'unknown' || state.phase === 'previewing')
      return false;
    try {
      assertSavedOwnership();
      storage.removeItem(storageKey);
      if (storage.getItem(storageKey) !== null) throw new Error('旧意图移除未确认');
    } catch (error) {
      state.phase = 'blocked';
      state.message = '不能安全开始新意图：' + errorMessage(error);
      return false;
    }
    state.command = null;
    state.requestKey = '';
    return editSource(state.source);
  }

  /** 本地记录不授予认证，也不代表创建成功；重新打开后必须显式恢复原请求。 */
  function restoreSaved(): boolean {
    if (!active) return false;
    try {
      const text = storage.getItem(storageKey);
      if (!text) return true;
      const saved = JSON.parse(text) as { requestKey: string; source: CertifiedPurchaseSource; command: PuCertifiedPurchaseCommand | null };
      if (!validKey(saved.requestKey)) throw new Error('原请求键损坏');
      const source = checkedSource(saved.source);
      if (saved.command) {
        const command = saved.command;
        const fields = ['requestKey', 'materialId', 'vendorId', 'windowEnd', 'requirementDetailIds', 'expectedSourceFingerprint'];
        if (
          Object.keys(command).length !== fields.length ||
          !fields.every((key) => Object.prototype.hasOwnProperty.call(command, key)) ||
          command.requestKey !== saved.requestKey ||
          !validFingerprint(command.expectedSourceFingerprint) ||
          JSON.stringify(checkedSource(command)) !== JSON.stringify(source)
        )
          throw new Error('原请求载荷损坏或与原来源不符');
        state.command = Object.freeze({ ...command, requirementDetailIds: Object.freeze([...command.requirementDetailIds]) });
      }
      state.source = source;
      state.requestKey = saved.requestKey;
      state.preview = null;
      state.result = null;
      state.phase = state.command ? 'unknown' : 'editing';
      state.message = state.command
        ? '发现已冻结的原请求；不会自动提交，请明确恢复原请求核实结果。'
        : '已恢复未提交意图，必须重新进行服务端完整预览。';
      return true;
    } catch (error) {
      state.phase = 'blocked';
      state.message = '待恢复原请求不可安全读取，请人工核实，禁止丢弃换键：' + errorMessage(error);
      return false;
    }
  }

  /** 认证上下文变化只失效内存 flow；绝不删除主体分键的原 intent。 */
  function invalidate() {
    active = false;
    sequence++;
    previewSource = '';
    state.preview = null;
    state.result = null;
    state.phase = 'blocked';
    state.message = '认证或有效租户上下文已变化，当前请求失效；原 intent 保留，请重新核验主体后再恢复。';
  }

  return { editSource, preview, submit, recover, startNew, restoreSaved, invalidate };
}

function normalizedContext(context: CertifiedPurchaseContext): { tenantId: string; userId: string } {
  const userId =
    typeof context?.userId === 'number' && Number.isSafeInteger(context.userId) && context.userId > 0 ? String(context.userId) : context?.userId;
  if (!positiveId(userId) || typeof context?.tenantId !== 'string' || !/^\d{6,20}$/.test(context.tenantId)) {
    throw new Error('当前采购主体字符串身份未确认');
  }
  return { tenantId: context.tenantId, userId };
}

/** 仅安全的小整数身份可转字符串，雪花 ID 不得经过不安全的数字截断。 */
export function sameCertifiedPurchaseContext(left: CertifiedPurchaseContext, right: CertifiedPurchaseContext): boolean {
  try {
    const a = normalizedContext(left);
    const b = normalizedContext(right);
    return a.tenantId === b.tenantId && a.userId === b.userId;
  } catch {
    return false;
  }
}

/** 凭证只在闭包内存中保存；每次操作重新核对只读上下文，不以 Pinia 缓存代替服务端事实。 */
export function createCertifiedPurchaseAuthGuard(deps: {
  readCredential: () => string | null;
  readContext: (session: CertifiedPurchaseSession) => Promise<CertifiedPurchaseContext>;
  onInvalidated: () => void;
}) {
  let revision = 0;
  let bound: { context: { tenantId: string; userId: string }; session: CertifiedPurchaseSession } | undefined;

  function invalidate() {
    revision++;
    bound = undefined;
    deps.onInvalidated();
  }

  function changed(): never {
    invalidate();
    // 固定文案不得拼接凭证、服务端认证异常或动态错误对象。
    throw new Error('当前认证或有效租户上下文已变化/未确认，禁止继续发送；原 intent 保留');
  }

  function sessionFor(token: string, version: number): CertifiedPurchaseSession {
    return {
      token,
      assertCurrent: () => {
        if (revision !== version || deps.readCredential() !== token) changed();
      }
    };
  }

  async function open(): Promise<{ tenantId: string; userId: string }> {
    invalidate();
    const current = revision;
    const token = deps.readCredential();
    if (!token) changed();
    const session = sessionFor(token, current);
    try {
      const context = normalizedContext(await deps.readContext(session));
      session.assertCurrent();
      bound = { context, session };
      return context;
    } catch {
      return changed();
    }
  }

  async function verify(): Promise<CertifiedPurchaseSession> {
    const current = bound;
    if (!current) changed();
    try {
      current.session.assertCurrent();
      const context = normalizedContext(await deps.readContext(current.session));
      current.session.assertCurrent();
      if (bound !== current || !sameCertifiedPurchaseContext(context, current.context)) changed();
      return current.session;
    } catch {
      return changed();
    }
  }

  return { open, verify, invalidate };
}
