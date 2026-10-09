import axios, { AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { useUserStore } from '@/store/modules/user';
import { getToken } from '@/utils/auth';
import { tansParams, blobValidate } from '@/utils/ruoyi';
import cache from '@/plugins/cache';
import { HttpStatus } from '@/enums/RespEnum';
import { errorCode } from '@/utils/errorCode';
import { LoadingInstance } from 'element-plus/es/components/loading/src/loading';
import FileSaver from 'file-saver';
import { encryptBase64, encryptWithAes, generateAesKey, decryptWithAes, decryptBase64 } from '@/utils/crypto';
import { encrypt, decrypt } from '@/utils/jsencrypt';
import router from '@/router';

const encryptHeader = 'encrypt-key';
let downloadLoadingInstance: LoadingInstance;
// 是否显示重新登录
export const isRelogin = { show: false };

/** 认证业务页面自行给出固定文案；此客户端标记只隔离错误副作用，不授予业务权限。 */
type CertifiedErrorConfig = InternalAxiosRequestConfig & { _certifiedErrorBoundary?: boolean };
const hasCertifiedErrorBoundary = (config?: InternalAxiosRequestConfig): boolean =>
  (config as CertifiedErrorConfig | undefined)?._certifiedErrorBoundary === true;

export const globalHeaders = () => {
  return {
    Authorization: 'Bearer ' + getToken(),
    clientid: import.meta.env.VITE_APP_CLIENT_ID
  };
};

/**
 * 判断错误消息是否属于"认证失效"。
 * 正常情况认证失败返回 401；但当 NotLoginException 被当作未知 RuntimeException 抛出时，
 * 响应 code=500 且 msg 形如 "token 已被冻结：eyJxxx.yyy.zzz"（含 JWT 明文），
 * 此时不应把 token 明文弹给用户，需按认证失效处理。
 */
const isAuthExpiredError = (msg: string): boolean => {
  if (!msg) return false;
  return /NotLoginException|token\s*已被冻结|^eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/.test(msg);
};

/**
 * 统一弹出"重新登录"确认框，并发请求时只弹一次。
 */
const toReLogin = () => {
  if (isRelogin.show) return;
  isRelogin.show = true;
  ElMessageBox.confirm('登录状态已过期，您可以继续留在该页面，或者重新登录', '系统提示', {
    confirmButtonText: '重新登录',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(() => {
      isRelogin.show = false;
      useUserStore()
        .logout()
        .then(() => {
          router.replace({
            path: '/login',
            query: {
              redirect: encodeURIComponent(router.currentRoute.value.fullPath || '/')
            }
          });
        });
    })
    .catch(() => {
      isRelogin.show = false;
    });
};

/**
 * 从 `responseType: 'arraybuffer'` 的响应里解析「伪装成文件流的 JSON 错误信封」。
 *
 * <p>鉴权失败、无权限、业务异常都被 `@RestControllerAdvice` 以 **HTTP 200 + R{code,msg}** 应答，
 * 而 axios 对二进制响应直接交出 `res.data`：字节流没有 `code` 字段，拦截器会把业务码当成 200，
 * 把这段 JSON 当成「文件内容」交给上层 —— pdfjs 随即报 `InvalidPDFException`，
 * 用户看到的是「该文件不是有效的 PDF（或损坏）」，真实原因（无权限 / 会话过期 / 文件不存在）被吞掉。</p>
 *
 * <p>判定：先看响应头 `Content-Type` 是否声明 JSON，再看首个非空白字节是否为 `{`（0x7B；
 * PDF 以 `%PDF-` 开头，不会命中）；最后要求解析出的对象带**数字** `code` 字段（只有 R&lt;T&gt; 信封才有）。
 * 双重把关，避免把真正的 JSON 文件下载误判成错误。</p>
 */
const readJsonErrorEnvelope = (res: AxiosResponse): { code: number; msg?: string } | null => {
  const data = res.data;
  if (!(data instanceof ArrayBuffer) || data.byteLength === 0) return null;
  const bytes = new Uint8Array(data);
  let head = 0;
  while (head < bytes.length && (bytes[head] === 0x20 || bytes[head] === 0x09 || bytes[head] === 0x0a || bytes[head] === 0x0d)) head++;
  const contentType = String(res.headers?.['content-type'] ?? '');
  if (!/json/i.test(contentType) && bytes[head] !== 0x7b) return null;
  try {
    const body = JSON.parse(new TextDecoder().decode(bytes));
    return body && typeof body === 'object' && typeof body.code === 'number' ? { code: body.code, msg: body.msg } : null;
  } catch {
    // 首字节恰好是 `{` 的二进制文件：解析失败即按正常文件流返回
    return null;
  }
};

axios.defaults.headers['Content-Type'] = 'application/json;charset=utf-8';
axios.defaults.headers['clientid'] = import.meta.env.VITE_APP_CLIENT_ID;
// 创建 axios 实例
const service = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API,
  timeout: 50000,
  transitional: {
    // 超时错误更明确
    clarifyTimeoutError: true
  }
});

// 请求拦截器
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // 内部标记不发送到后端；固定会话的401迟到响应不能登出另一账号或弹出原始异常。
    if (config.headers?.certifiedErrorBoundary === 'true') {
      (config as CertifiedErrorConfig)._certifiedErrorBoundary = true;
      delete config.headers.certifiedErrorBoundary;
    }
    // 请求头语言(仅中文)
    config.headers['Content-Language'] = 'zh_CN';

    const isToken = config.headers?.isToken === false;
    // 是否需要防止数据重复提交
    const isRepeatSubmit = config.headers?.repeatSubmit === false;
    // 是否需要加密
    const isEncrypt = config.headers?.isEncrypt === 'true';

    if (getToken() && !isToken) {
      config.headers['Authorization'] = 'Bearer ' + getToken(); // 让每个请求携带自定义token 请根据实际情况自行修改
    }
    // get请求映射params参数
    if (config.method === 'get' && config.params) {
      let url = config.url + '?' + tansParams(config.params);
      url = url.slice(0, -1);
      config.params = {};
      config.url = url;
    }

    if (!isRepeatSubmit && (config.method === 'post' || config.method === 'put')) {
      const requestObj = {
        url: config.url,
        data: typeof config.data === 'object' ? JSON.stringify(config.data) : config.data,
        time: new Date().getTime()
      };
      const sessionObj = cache.session.getJSON('sessionObj');
      if (sessionObj === undefined || sessionObj === null || sessionObj === '') {
        cache.session.setJSON('sessionObj', requestObj);
      } else {
        const s_url = sessionObj.url; // 请求地址
        const s_data = sessionObj.data; // 请求数据
        const s_time = sessionObj.time; // 请求时间
        const interval = 500; // 间隔时间(ms)，小于此时间视为重复提交
        if (s_data === requestObj.data && requestObj.time - s_time < interval && s_url === requestObj.url) {
          const message = '数据正在处理，请勿重复提交';
          console.warn(`[${s_url}]: ` + message);
          return Promise.reject(new Error(message));
        } else {
          cache.session.setJSON('sessionObj', requestObj);
        }
      }
    }
    if (import.meta.env.VITE_APP_ENCRYPT === 'true') {
      // 当开启参数加密
      if (isEncrypt && (config.method === 'post' || config.method === 'put')) {
        // 生成一个 AES 密钥
        const aesKey = generateAesKey();
        config.headers[encryptHeader] = encrypt(encryptBase64(aesKey));
        config.data = typeof config.data === 'object' ? encryptWithAes(JSON.stringify(config.data), aesKey) : encryptWithAes(config.data, aesKey);
      }
    }
    // FormData数据去请求头Content-Type
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type'];
    }
    return config;
  },
  (error: any) => {
    return Promise.reject(error);
  }
);

// 响应拦截器
service.interceptors.response.use(
  (res: AxiosResponse) => {
    const isolated = hasCertifiedErrorBoundary(res.config);
    try {
      if (import.meta.env.VITE_APP_ENCRYPT === 'true') {
        // 加密后的 AES 秘钥
        const keyStr = res.headers[encryptHeader];
        // 加密
        if (keyStr != null && keyStr != '') {
          const data = res.data;
          // 请求体 AES 解密
          const base64Str = decrypt(keyStr);
          // base64 解码 得到请求头的 AES 秘钥
          const aesKey = decryptBase64(base64Str.toString());
          // aesKey 解码 data
          const decryptData = decryptWithAes(data, aesKey);
          // 将结果 (得到的是 JSON 字符串) 转为 JSON
          res.data = JSON.parse(decryptData);
        }
      }
    } catch (error) {
      if (isolated) return Promise.reject(new Error('认证接口响应解码未确认，请通过业务页面只读核对。'));
      throw error;
    }
    // 位于所有toast/通知/重新登录副作用之前；helper仍从真实HTTP及R信封区分明确拒绝和未知。
    if (isolated) {
      if (res.status === 200 && res.data && typeof res.data === 'object' && !Array.isArray(res.data) && res.data.code === HttpStatus.SUCCESS) {
        return Promise.resolve(res.data);
      }
      return Promise.reject(new Error('认证接口结果未确认，请通过业务页面核对；禁止自动重发。'));
    }
    // 未设置状态码则默认成功状态
    const code = res.data.code || HttpStatus.SUCCESS;
    // 获取错误信息
    const msg = errorCode[code] || res.data.msg || errorCode['default'];
    // 二进制响应里可能装着 JSON 错误信封（见 readJsonErrorEnvelope 注释）。
    // 命中时用信封里的 code/msg 走下面同一套错误分支 —— 否则「没有访问权限」会被当成文件内容，
    // 由 pdfjs 报成「不是有效的 PDF（或损坏）」，401 也不会触发重新登录。
    // 仅对 arraybuffer 生效：blob 响应的调用方（$download.oss / download()）本就按 blobValidate 自行识别 JSON。
    const jsonEnvelope = res.request.responseType === 'arraybuffer' ? readJsonErrorEnvelope(res) : null;
    if (jsonEnvelope) {
      const envelopeCode = jsonEnvelope.code || HttpStatus.SUCCESS;
      // 成功信封按普通二进制响应放行：理论上文件流端点不会包成功信封，但真出现时
      // 不该被当错误拒绝（L2：code=200 的信封此前也走 reject）
      if (envelopeCode === HttpStatus.SUCCESS) {
        return res.data;
      }
      const envelopeMsg = errorCode[envelopeCode] || jsonEnvelope.msg || errorCode['default'];
      if (envelopeCode === HttpStatus.UNAUTHORIZED || isAuthExpiredError(envelopeMsg)) {
        toReLogin();
      } else if (res.config.headers?.ignoreErrorMsg !== 'true') {
        ElMessage({ message: envelopeMsg, type: 'error' });
      }
      // 拒绝值统一用 Error 承载真实原因（普通 JSON 分支各 code 会拒绝字符串，
      // 上层只能拿到「加载失败」这类无信息量的兜底文案，例如 PdfSealPlacer 的错误提示区）
      return Promise.reject(Object.assign(new Error(envelopeMsg), { code: envelopeCode }));
    }
    // 二进制数据则直接返回
    if (res.request.responseType === 'blob' || res.request.responseType === 'arraybuffer') {
      return res.data;
    }
    if (code === 401) {
      toReLogin();
      return Promise.reject('无效的会话，或者会话已过期，请重新登录。');
    } else if (code === HttpStatus.SERVER_ERROR) {
      // 后端误将认证异常当作未知异常抛出时，msg 可能含 token/JWT 明文，按认证失效处理
      if (isAuthExpiredError(msg)) {
        toReLogin();
        return Promise.reject('无效的会话，或者会话已过期，请重新登录。');
      }
      // 调用方通过 headers: { ignoreErrorMsg: 'true' } 声明自行处理错误提示时，不在此处弹统一错误消息
      // （如知识库保存返回的错误令牌 REBUILD_INDEX_REQUIRED，由页面捕获后弹确认框再重发）
      if (res.config.headers?.ignoreErrorMsg !== 'true') {
        ElMessage({ message: msg, type: 'error' });
      }
      return Promise.reject(new Error(msg));
    } else if (code === HttpStatus.WARN) {
      ElMessage({ message: msg, type: 'warning' });
      return Promise.reject(new Error(msg));
    } else if (code !== HttpStatus.SUCCESS) {
      ElNotification.error({ title: msg });
      return Promise.reject('error');
    } else {
      return Promise.resolve(res.data);
    }
  },
  (error: any) => {
    if (hasCertifiedErrorBoundary(error?.config)) {
      return Promise.reject(new Error('认证接口网络或HTTP结果未确认，请保留原意图，只读核对。'));
    }
    let { message } = error;
    if (message == 'Network Error') {
      message = '后端接口连接异常';
    } else if (message.includes('timeout')) {
      message = '系统接口请求超时';
    } else if (message.includes('Request failed with status code')) {
      message = '系统接口' + message.substr(message.length - 3) + '异常';
    }
    ElMessage({ message: message, type: 'error', duration: 5 * 1000 });
    return Promise.reject(error);
  }
);
// 通用下载方法
export function download(url: string, params: any, fileName: string) {
  downloadLoadingInstance = ElLoading.service({ text: '正在下载数据，请稍候', background: 'rgba(0, 0, 0, 0.7)' });
  // prettier-ignore
  return service.post(url, params, {
      transformRequest: [
        (params: any) => {
          return tansParams(params);
        }
      ],
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      responseType: 'blob'
    }).then(async (resp: any) => {
      const isLogin = blobValidate(resp);
      if (isLogin) {
        const blob = new Blob([resp]);
        FileSaver.saveAs(blob, fileName);
      } else {
        const blob = new Blob([resp]);
        const resText = await blob.text();
        const rspObj = JSON.parse(resText);
        const errMsg = errorCode[rspObj.code] || rspObj.msg || errorCode['default'];
        ElMessage.error(errMsg);
      }
      downloadLoadingInstance.close();
    }).catch((r: any) => {
      console.error(r);
      ElMessage.error('下载文件出现错误，请联系管理员！');
      downloadLoadingInstance.close();
    });
}
// 导出 axios 实例
export default service;
