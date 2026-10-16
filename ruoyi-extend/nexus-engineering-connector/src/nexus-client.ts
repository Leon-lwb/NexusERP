/** 网络异常只保留受限机器错误码，不回显服务端正文或凭证。 */
export class NexusError extends Error {
  constructor(
    public status: number,
    public code: string
  ) {
    super(`Nexus请求失败: ${status} ${code}`);
  }
}

/** 将请求、响应正文及刷新等待绑定到同一取消与超时范围。 */
async function abortable<T>(promise: Promise<T>, signal: AbortSignal): Promise<T> {
  signal.throwIfAborted();
  let cancel: () => void;
  const stopped = new Promise<never>((_, reject) => {
    cancel = () => reject(signal.reason);
    signal.addEventListener('abort', cancel, { once: true });
  });
  try {
    return await Promise.race([promise, stopped]);
  } finally {
    signal.removeEventListener('abort', cancel!);
  }
}

/** 只访问固定Nexus接口，禁止重定向；凭证只保存在内存。 */
export class NexusClient {
  private accessToken = '';
  private refreshing?: Promise<void>;
  private refreshHandler?: (signal?: AbortSignal) => Promise<void>;

  constructor(
    private base: string,
    private fetcher: typeof fetch = fetch,
    private options: { requestTimeoutMs?: number; uploadTimeoutMs?: number } = {}
  ) {}

  /** 仅401触发一次刷新；共享刷新也受原请求的取消与时限约束。 */
  setRefreshHandler(handler: (signal?: AbortSignal) => Promise<void>) {
    this.refreshHandler = handler;
  }
  /** 访问凭证不落盘、不通过URL或MCP传输。 */
  setAccessToken(token: string) {
    this.accessToken = token;
  }

  /** 发送JSON或multipart，调用者取消与独立请求超时始终同时生效。 */
  async request(path: string, body?: unknown, signal?: AbortSignal, extraHeaders: Record<string, string> = {}, retried = false): Promise<any> {
    if (
      !/^\/(?:connector|devices|tasks)\//.test(path) &&
      !['/connector/pair', '/connector/refresh', '/connector/heartbeat', '/connector/claim'].includes(path)
    )
      throw new Error('不允许的接口路径');
    if (path.includes('..') || path.includes('?') || path.includes('#')) throw new Error('不允许的接口路径');
    const form = body instanceof FormData;
    const timeout = form ? (this.options.uploadTimeoutMs ?? 300_000) : (this.options.requestTimeoutMs ?? 30_000);
    const deadline = AbortSignal.timeout(timeout);
    const requestSignal = signal ? AbortSignal.any([signal, deadline]) : deadline;
    requestSignal.throwIfAborted();
    const headers: Record<string, string> = { ...extraHeaders };
    if (this.accessToken) headers['X-Nexus-Device-Token'] = this.accessToken;
    if (body !== undefined && !form) headers['Content-Type'] = 'application/json';
    const response = await abortable(
      this.fetcher(this.base + '/ai/engineering' + path, {
        method: body === undefined ? 'GET' : 'POST',
        headers,
        body: body === undefined ? undefined : form ? body : JSON.stringify(body),
        redirect: 'error',
        signal: requestSignal
      }),
      requestSignal
    );
    if (response.status === 401 && !retried && this.refreshHandler && path !== '/connector/refresh') {
      await response.body?.cancel();
      if (!this.refreshing)
        this.refreshing = this.refreshHandler(requestSignal).finally(() => {
          this.refreshing = undefined;
        });
      await abortable(this.refreshing, requestSignal);
      return this.request(path, body, requestSignal, extraHeaders, true);
    }
    let result: any;
    try {
      result = await abortable(response.json(), requestSignal);
    } catch (error) {
      requestSignal.throwIfAborted();
      throw new NexusError(response.ok ? 502 : response.status, response.ok ? 'INVALID_RESPONSE' : 'HTTP_ERROR');
    }
    const code = typeof result?.data?.errorCode === 'string' && /^[A-Z_]{1,60}$/.test(result.data.errorCode) ? result.data.errorCode : undefined;
    if (!response.ok) throw new NexusError(response.status, code ?? 'HTTP_ERROR');
    if (result?.code !== 200) throw new NexusError(Number.isInteger(result?.code) ? result.code : 502, code ?? 'APPLICATION_ERROR');
    return result.data;
  }
}
