import request from '@/utils/request';

/** 本次已核验的凭证快照；只保留在内存，不并入业务命令、意图存储或错误对象。 */
export interface CertifiedBusinessSession {
  token: string;
  assertCurrent: () => void;
}

/** 普通 HTTP 接口的 R 信封；code 不能用字符串、消息文本或 truthy 默认值推断。 */
export interface CertifiedBusinessResponse<T> {
  code: number;
  data?: T;
  msg?: string;
}

/**
 * 业务意图的完整主体及文档作用域，不包含版本、操作或 requestKey。
 * 调用方应使用真实登录主体和已规范化的文档 ID；创建入口可使用固定的草稿作用域。
 * tenantId 保留原始字符串（包括前导零），不得按数值重新解释。
 */
export interface BusinessScope {
  tenantId: string;
  userId: string;
  feature: string;
  documentId: string;
}

/** 仅保存身份及原始意图；nonce 区分同作用域的不同尝试，不代表服务端授权。 */
export interface BusinessIntent {
  scope: BusinessScope;
  operation: string;
  requestKey: string;
  nonce: string;
}

const MESSAGE = {
  id: '业务 ID 未确认，请使用精确的正整数标识。',
  version: '原生业务版本未确认，禁止使用缺失值或字符串代替。',
  session: '当前身份或页面作用域已改变，请重新核验。',
  read: '认证只读结果未确认，请重新核验身份及单据。',
  unknown: '服务端未确认本次写入结果，请保留原意图，禁止换键重试。',
  refused: '服务端明确拒绝本次请求，请核对权限及业务条件。',
  intent: '业务意图未确认，请核对当前主体及单据。',
  blocked: '当前单据已有未释放意图或存储异常，禁止重复提交。',
  storage: '业务意图存储未确认，当前单据继续封闭，禁止重试。'
};

/**
 * 校验 Long 范围的正数 canonical ID；字符串全程不用 Number，避免雪花 ID 精度丢失。
 * @param value 原始 ID；仅安全的整数 number 可转为字符串
 * @returns 无符号、无前导零且不超过 Long.MAX_VALUE 的十进制字符串
 */
export function normalizeBusinessId(value: unknown): string {
  const normalized = typeof value === 'number' && Number.isSafeInteger(value) && value > 0 ? String(value) : value;
  if (typeof normalized !== 'string' || !/^[1-9]\d{0,18}$/.test(normalized) || (normalized.length === 19 && normalized > '9223372036854775807')) {
    throw new Error(MESSAGE.id);
  }
  return normalized;
}

/** 只接受原生整数 number 版本 0..2147483647（含上界）；NULL、字符串和缺失值一律拒绝。 */
export function normalizeBusinessVersion(value: unknown): number {
  if (typeof value !== 'number' || !Number.isInteger(value) || value < 0 || value > 2147483647) throw new Error(MESSAGE.version);
  return value;
}

/**
 * 固定当前凭证，后续每次复核同时检查凭证与调用方绑定的完整页面/主体作用域。
 * 不主动调用 assertScope 初始化页面；其核验发生在 assertCurrent 及派发/响应边界。
 * @param readCredential 读取当前内存/认证存储中的凭证，不得把凭证写入意图
 * @param assertScope 调用方核对租户、用户、选中单据及页面代次；不匹配时抛错
 */
export function createBusinessSession(readCredential: () => string | null | undefined, assertScope?: () => void): CertifiedBusinessSession {
  let token: string;
  try {
    const current = readCredential();
    if (typeof current !== 'string' || !current || /\s/.test(current)) throw new Error();
    token = current;
  } catch {
    throw new Error(MESSAGE.session);
  }
  return Object.freeze({
    token,
    assertCurrent() {
      try {
        if (readCredential() !== token) throw new Error();
        assertScope?.();
        // scope 回调也可能使凭证切换；不得依赖回调前的单次检查。
        if (readCredential() !== token) throw new Error();
      } catch {
        throw new Error(MESSAGE.session);
      }
    }
  });
}

function assertSession(session: CertifiedBusinessSession): void {
  if (!session || typeof session.token !== 'string' || !session.token || typeof session.assertCurrent !== 'function') {
    throw new Error(MESSAGE.session);
  }
  session.assertCurrent();
}

function sessionConfig(session: CertifiedBusinessSession) {
  assertSession(session);
  const transforms = request.defaults.transformRequest;
  return {
    headers: { isToken: false, Authorization: 'Bearer ' + session.token, repeatSubmit: false, certifiedErrorBoundary: 'true' },
    transformRequest: [
      ...(Array.isArray(transforms) ? transforms : transforms ? [transforms] : []),
      (body: unknown) => {
        // 默认序列化/加密变换和全部请求拦截器之后，实际 adapter dispatch 之前复核。
        assertSession(session);
        return body;
      }
    ]
  };
}

function envelope(value: unknown): value is CertifiedBusinessResponse<unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value) || !Object.prototype.hasOwnProperty.call(value, 'code')) return false;
  const response = value as CertifiedBusinessResponse<unknown>;
  return Number.isInteger(response.code) && (response.msg === undefined || typeof response.msg === 'string');
}

function responseCapture() {
  let status: number | undefined;
  let received: CertifiedBusinessResponse<unknown> | undefined;
  const transforms = request.defaults.transformResponse;
  return {
    config: {
      transformResponse: [
        ...(Array.isArray(transforms) ? transforms : transforms ? [transforms] : []),
        (body: unknown, _headers: unknown, httpStatus?: number) => {
          status = httpStatus;
          received = envelope(body) ? body : undefined;
          return body;
        }
      ]
    },
    success(response: unknown) {
      return status === 200 && received?.code === 200 && envelope(response) && response.code === 200;
    },
    refusal(): number | undefined {
      // 真实 HTTP 2xx/4xx 和整数 R4xx 同时成立才是明确拒绝；HTTP/R 5xx、网络失败都未知。
      if (
        status !== undefined &&
        Number.isInteger(status) &&
        ((status >= 200 && status < 300) || (status >= 400 && status < 500)) &&
        received &&
        received.code >= 400 &&
        received.code < 500
      ) {
        return received.code;
      }
      return undefined;
    }
  };
}

/**
 * 经普通 GET 和固定会话读取真实 HTTP 200/R200 数据；不触碰或清除任何写意图。
 * data 可为数组/零/false/null，是否适合具体业务写入由调用方继续核验。
 * @param params 普通只读查询参数，不作为可信来源或写权限
 */
export async function readCertifiedBusiness<T>(url: string, session: CertifiedBusinessSession, params?: object): Promise<T> {
  try {
    const capture = responseCapture();
    const response = await request<unknown, CertifiedBusinessResponse<T>>({
      url,
      method: 'get',
      params,
      ...sessionConfig(session),
      ...capture.config
    });
    assertSession(session);
    if (!capture.success(response) || !Object.prototype.hasOwnProperty.call(response, 'data') || response.data === undefined) {
      throw new Error();
    }
    return response.data;
  } catch {
    // 不回传 Axios config、原错误、服务端认证异常或 cause 中的凭证。
    throw new Error(MESSAGE.read);
  }
}

/**
 * 经普通 POST 派发一次原命令；不自动重发、换 requestKey 或释放意图。
 * 仅 HTTP200/R200 返回成功；明确 HTTP2xx/4xx + R4xx 返回脱敏拒绝，其余抛未知结果。
 * 调用方应先 begin 并复核 assertPending；成功需再核对业务回执，明确拒绝需再核对会话后 release。
 */
export async function postCertifiedBusiness<T>(
  url: string,
  body: object | undefined,
  session: CertifiedBusinessSession
): Promise<CertifiedBusinessResponse<T>> {
  let capture: ReturnType<typeof responseCapture> | undefined;
  try {
    capture = responseCapture();
    const response = await request<unknown, CertifiedBusinessResponse<T>>({
      url,
      method: 'post',
      data: body,
      ...sessionConfig(session),
      ...capture.config
    });
    assertSession(session);
    if (!capture.success(response)) throw new Error();
    // 原 msg 可能含内部异常或凭证；只交付业务数据，不传播动态错误文本。
    return { code: 200, ...(response.data === undefined ? {} : { data: response.data }) };
  } catch {
    try {
      assertSession(session);
    } catch {
      throw new Error(MESSAGE.unknown);
    }
    const code = capture?.refusal();
    if (code !== undefined) return { code, msg: MESSAGE.refused };
    throw new Error(MESSAGE.unknown);
  }
}

type IntentStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;
interface SavedIntent {
  key: string;
  encoded: string;
}

// 同一模块内所有组件/store 共享已知事实；不能通过换组件、换 storage 或换版本绕过。
// null 表示存储读取失败；冲突哨兵表示已观察同 scope 的不同 metadata，均必须封闭。
const CONFLICTED_SCOPE = Symbol('conflicted-business-scope');
const scopeLatches = new Map<string, string | null | typeof CONFLICTED_SCOPE>();
const fenceKey = (key: string) => key + ':release-fence';

function latch(key: string, encoded: string | null): void {
  const known = scopeLatches.get(key);
  // 冲突属于整个 scope，不是某个 store；后续重读原记录、读到 NULL 或故障都不能恢复独占资格。
  if (known === CONFLICTED_SCOPE) return;
  if (typeof known === 'string' && encoded !== null && known !== encoded) {
    scopeLatches.set(key, CONFLICTED_SCOPE);
    return;
  }
  if (!scopeLatches.has(key) || (known === null && encoded !== null)) scopeLatches.set(key, encoded);
}

function checkedScope(scope: BusinessScope): BusinessScope {
  try {
    const copy = { tenantId: scope.tenantId, userId: scope.userId, feature: scope.feature, documentId: scope.documentId };
    if (Object.values(copy).some((value) => typeof value !== 'string' || !value.trim())) throw new Error();
    return Object.freeze(copy);
  } catch {
    throw new Error(MESSAGE.intent);
  }
}

function scopeKey(scope: BusinessScope): string {
  try {
    return ['nexus:certified-business', scope.tenantId, scope.userId, scope.feature, scope.documentId].map(encodeURIComponent).join(':');
  } catch {
    throw new Error(MESSAGE.intent);
  }
}

/** 只允许本协议的完整 metadata 补回；损坏槽/额外业务字段只能保守锁住，不能回写原始内容。 */
function recoverableMetadata(encoded: string, key: string): boolean {
  try {
    const value = JSON.parse(encoded);
    if (!value || typeof value !== 'object' || Array.isArray(value) || Object.keys(value).length !== 4) return false;
    const bound = checkedScope(value.scope);
    return (
      Object.keys(value.scope).length === 4 &&
      scopeKey(bound) === key &&
      ['operation', 'requestKey', 'nonce'].every((field) => typeof value[field] === 'string' && value[field].trim())
    );
  } catch {
    return false;
  }
}
/** 优先使用 UUID；不具备 randomUUID 的浏览器仍使用安全随机字节，绝不降级为时间/Math.random。 */
function intentNonce(): string {
  if (typeof globalThis.crypto.randomUUID === 'function') return globalThis.crypto.randomUUID();
  const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('');
}
/**
 * 建立先持久写、后派发的 UNKNOWN 护栏，仅保存 scope/operation/requestKey/nonce。
 * 浏览器存储没有跨进程 CAS；服务端仍须执行租户、权限、版本及幂等校验。
 * 全部持久记录丢失且所有补写失败后，内存 latch 只能保护当前模块，硬 reload 不能恢复事实。
 * UNKNOWN/损坏记录及存储故障只能保守阻塞，读取/新实例不自动擦除。
 * 同 scope 观察到不同 metadata 即锁存不可逆冲突；任一旧对象不能复核/释放或清除其他已知锁。
 * @param storage 显式传入浏览器存储边界；get/set/remove 及读回异常一律封闭
 * 核验或释放失败后，原对象不再具有本 store 的派发/释放资格，锁仍保留；不自动恢复 UNKNOWN。
 * @returns isBlocked/begin/assertPending/release；仅本 store 发出的原对象可复核和释放
 */
export function createBusinessIntentStore(storage: IntentStorage) {
  const issued = new WeakMap<BusinessIntent, SavedIntent>();

  function stored(key: string): string | null {
    const value = storage.getItem(key);
    // 不把 undefined 或非字符串的异常实现解释为空存储。
    if (value !== null && typeof value !== 'string') throw new Error();
    return value;
  }

  function primary(key: string): string | null {
    const value = stored(key);
    // 第一时间锁存，再执行任何后续 fence I/O；其故障不得丢失已读到的 UNKNOWN。
    if (value !== null) latch(key, value);
    return value;
  }

  function fence(key: string): string | null {
    const value = stored(fenceKey(key));
    if (value !== null) latch(key, value);
    return value;
  }

  function isBlocked(scope: BusinessScope): boolean {
    let key: string;
    try {
      key = scopeKey(checkedScope(scope));
    } catch {
      return true;
    }
    try {
      const current = primary(key);
      const guard = fence(key);
      if (current !== null || guard !== null) return true;
      if (!scopeLatches.has(key)) return false;
      const known = scopeLatches.get(key);
      if (typeof known === 'string' && recoverableMetadata(known, key)) {
        // 两个槽都明确为空时，仅尽力补回已知原 metadata；失败仍锁住，不派发业务请求。
        try {
          storage.setItem(key, known);
        } catch {
          /* 内存中的已知事实不可清除。 */
        }
      }
      return true;
    } catch {
      latch(key, null);
      return true;
    }
  }

  function begin(scope: BusinessScope, operation: string, requestKey: string): BusinessIntent {
    const bound = checkedScope(scope);
    const key = scopeKey(bound);
    if (typeof operation !== 'string' || !operation.trim() || typeof requestKey !== 'string' || !requestKey.trim()) {
      throw new Error(MESSAGE.intent);
    }
    if (isBlocked(bound)) throw new Error(MESSAGE.blocked);
    try {
      const nonce = intentNonce();
      const intent: BusinessIntent = Object.freeze({ scope: bound, operation, requestKey, nonce });
      const encoded = JSON.stringify(intent);
      // 写失败/写后读失败时同 scope 的新组件仍封闭；完全确认持久读回前不得交付可派发意图。
      latch(key, encoded);
      storage.setItem(key, encoded);
      if (primary(key) !== encoded || fence(key) !== null || scopeLatches.get(key) !== encoded) throw new Error();
      issued.set(intent, { key, encoded });
      return intent;
    } catch {
      latch(key, null);
      throw new Error(MESSAGE.storage);
    }
  }

  function owned(intent: BusinessIntent): SavedIntent {
    const saved = issued.get(intent);
    if (!saved) throw new Error(MESSAGE.intent);
    return saved;
  }

  function assertPending(intent: BusinessIntent): void {
    const saved = owned(intent);
    try {
      const current = primary(saved.key);
      const guard = fence(saved.key);
      if (current !== saved.encoded || guard !== null || scopeLatches.get(saved.key) !== saved.encoded) throw new Error();
    } catch {
      issued.delete(intent);
      latch(saved.key, saved.encoded);
      throw new Error(MESSAGE.storage);
    }
  }

  function retainFence(saved: SavedIntent, removalAttempted: boolean): void {
    latch(saved.key, saved.encoded);
    try {
      if (fence(saved.key) !== null) return; // 不覆盖已观察到的其他 fence，更不能改/删其他 primary。
      storage.setItem(fenceKey(saved.key), saved.encoded);
    } catch {
      // 本次自有 fence 已尝试移除，后续 get 失效时尽力补回最后的 durable guard。
      // 这不是跨进程 CAS；补写也失败时只剩内存保护，不能声称硬 reload 可恢复。
      if (removalAttempted) {
        try {
          storage.setItem(fenceKey(saved.key), saved.encoded);
        } catch {
          /* 内存 latch 继续封闭。 */
        }
      }
    }
  }

  /**
   * 仅调用方已确认业务成功/明确拒绝后释放本次原意图。
   * 先写并读回 durable fence，才移除 primary；全部移除和读回成功后才清内存 latch。
   * 本函数不知道 HTTP 结果，不可用于 UNKNOWN 自动恢复或任何 finally 清理。
   */
  function release(intent: BusinessIntent): void {
    const saved = owned(intent);
    let fenceAttempted = false;
    let removalAttempted = false;
    try {
      assertPending(intent);
      fenceAttempted = true;
      storage.setItem(fenceKey(saved.key), saved.encoded);
      if (fence(saved.key) !== saved.encoded || scopeLatches.get(saved.key) !== saved.encoded) throw new Error();
      // fence 已读回后再核对原 primary 及 scope 独占资格；其他 store 在 I/O 期间观察到冲突也必须停止。
      if (primary(saved.key) !== saved.encoded || scopeLatches.get(saved.key) !== saved.encoded) throw new Error();
      storage.removeItem(saved.key);
      if (primary(saved.key) !== null || scopeLatches.get(saved.key) !== saved.encoded) throw new Error();
      if (fence(saved.key) !== saved.encoded || scopeLatches.get(saved.key) !== saved.encoded) throw new Error();
      removalAttempted = true;
      storage.removeItem(fenceKey(saved.key));
      if (primary(saved.key) !== null || fence(saved.key) !== null || scopeLatches.get(saved.key) !== saved.encoded) throw new Error();
      scopeLatches.delete(saved.key);
      issued.delete(intent);
    } catch {
      latch(saved.key, saved.encoded);
      issued.delete(intent);
      if (fenceAttempted) retainFence(saved, removalAttempted);
      throw new Error(MESSAGE.storage);
    }
  }

  return { isBlocked, begin, assertPending, release };
}
