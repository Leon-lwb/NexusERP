import type { AxiosResponse } from 'axios';

/** 经报表接口契约和凭证脱敏检查后的用户可见错误。 */
interface ReportResponseError {
  code: number;
  errorCode: string;
  message: string;
}

/** 只识别报表JSON错误信封，认证、文件和其他模块仍使用原有响应边界。 */
export function readReportResponseError(response?: AxiosResponse): ReportResponseError | null {
  if (!response || response.status === 401) return null;
  if ([response.config?.responseType, response.request?.responseType].some((type) => type === 'blob' || type === 'arraybuffer')) return null;

  let path: string;
  try {
    const requestPath = new URL(response.config?.url || '', 'http://localhost').pathname;
    const basePath = new URL(response.config?.baseURL || '/', 'http://localhost').pathname.replace(/\/+$/, '');
    path = basePath && requestPath.startsWith(basePath + '/') ? requestPath.slice(basePath.length) : requestPath;
  } catch {
    return null;
  }
  if (!path.startsWith('/report/')) return null;

  const data = response.data;
  if (!data || typeof data !== 'object' || Array.isArray(data)) return null;
  if (!Number.isInteger(data.code) || data.code < 400 || data.code > 599 || data.code === 401) return null;
  if (typeof data.errorCode !== 'string' || !/^[A-Z][A-Z0-9_]{0,127}$/.test(data.errorCode)) return null;

  const message = typeof data.msg === 'string' ? data.msg.trim() : '';
  const safeMessage =
    message.length <= 500 &&
    /[\u3400-\u9fff]/.test(message) &&
    !/token|jwt|bearer|authorization|exception|stacktrace|eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/i.test(message);
  return { code: data.code, errorCode: data.errorCode, message: safeMessage ? message : '报表处理失败，请稍后重试' };
}
