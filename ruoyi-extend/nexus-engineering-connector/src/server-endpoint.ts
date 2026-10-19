/** 接口识别只返回受控机器码，不携带响应正文、地址参数或凭证。 */
export class EngineeringEndpointError extends Error {
  constructor(public code: 'API_ADDRESS_INVALID' | 'API_NOT_FOUND' | 'API_REDIRECTED' | 'API_UNREACHABLE' | 'API_ACCESS_DENIED' | 'API_UNAVAILABLE' | 'ENGINEERING_DISABLED', public status?: number) {
    super(code);
  }
}

/** 支持站点根地址、显式API前缀，以及误填的工程页面地址。 */
export function engineeringApiCandidates(raw: string): string[] {
  const url = new URL(raw);
  if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash)
    throw new EngineeringEndpointError('API_ADDRESS_INVALID');
  let path = url.pathname.replace(/\/+$/, '');
  const page = path.match(/^(.*)\/ai\/(?:chat\/)?engineering\/(?:device|task)(?:\/.*)?$/);
  if (page) path = page[1];
  const base = url.origin + path;
  return /\/(?:prod-api|dev-api)$/.test(path) ? [base] : [base, base + '/prod-api', base + '/dev-api'];
}

/** 只用无认证GET探测固定接口，确认路径后调用者才能发送配对码或设备凭证。 */
export async function resolveEngineeringApiBase(raw: string, fetcher: typeof fetch = fetch, signal?: AbortSignal): Promise<string> {
  let missing = false, redirected = false, reached = false, denied = false;
  let unavailableStatus: number | undefined;
  for (const base of engineeringApiCandidates(raw)) {
    signal?.throwIfAborted();
    const timeout = AbortSignal.timeout(5000);
    const probeSignal = signal ? AbortSignal.any([signal, timeout]) : timeout;
    try {
      const response = await fetcher(base + '/ai/engineering/connector/pair', {
        method: 'GET', headers: { Accept: 'application/json' }, redirect: 'manual', signal: probeSignal
      });
      reached = true;
      if (response.status >= 300 && response.status < 400) {
        redirected = true;
        await response.body?.cancel();
        continue;
      }
      if (response.status >= 500) {
        unavailableStatus = response.status;
        await response.body?.cancel();
        continue;
      }
      if (!(response.headers.get('content-type') ?? '').toLowerCase().includes('json')) {
        await response.body?.cancel();
        continue;
      }
      const body = await response.json();
      if (body?.code === 405 || response.status === 405) return base;
      if (body?.code === 404 || response.status === 404) missing = true;
      if (Number.isInteger(body?.code) && body.code >= 500 && body.code < 600) unavailableStatus = body.code;
      if ([401, 403].includes(body?.code) || [401, 403].includes(response.status))
        denied = true;
    } catch (error) {
      signal?.throwIfAborted();
      if (error instanceof EngineeringEndpointError) throw error;
      // 仅继续固定同站点候选；网络和JSON异常不回显原始正文。
    }
  }
  if (unavailableStatus !== undefined) throw new EngineeringEndpointError('API_UNAVAILABLE', unavailableStatus);
  if (denied) throw new EngineeringEndpointError('API_ACCESS_DENIED');
  if (missing) throw new EngineeringEndpointError('ENGINEERING_DISABLED');
  if (redirected) throw new EngineeringEndpointError('API_REDIRECTED');
  throw new EngineeringEndpointError(reached ? 'API_NOT_FOUND' : 'API_UNREACHABLE');
}
