import { NexusError } from './nexus-client.js';
import { EngineeringEndpointError } from './server-endpoint.js';

/** 将已校验的机器码转成可行动提示，绝不输出服务端正文、凭证或任意异常原文。 */
export function connectorErrorMessage(error: unknown): string {
  if (error instanceof EngineeringEndpointError) {
    const messages: Record<EngineeringEndpointError['code'], string> = {
      API_ADDRESS_INVALID: '服务地址格式无效，请填写HTTP/HTTPS站点根地址或API地址。',
      API_NOT_FOUND: '未找到工程API：当前地址返回网页，请核对是否需要 /prod-api 或 /dev-api。',
      API_REDIRECTED: '工程API发生重定向，请填写最终服务地址；连接器不会携带凭证跟随跳转。',
      API_UNREACHABLE: '无法访问工程API，请核对服务地址、端口和网络连接。',
      API_UNAVAILABLE: '后端服务或反向代理暂不可用，请核对服务运行状态。',
      API_ACCESS_DENIED: '工程API拒绝访问，请核对反向代理和服务端工程权限配置。',
      ENGINEERING_DISABLED: '后端工程功能尚未启用或工程接口不存在，请启用 nexus.ai.engineering.enabled 并重启对应后端。'
    };
    const status = Number.isInteger(error.status) && error.status! >= 500 && error.status! < 600 ? ' / HTTP ' + error.status : '';
    return '[' + error.code + status + '] ' + messages[error.code];
  }
  if (error instanceof NexusError) {
    const code = /^[A-Z_]{1,60}$/.test(error.code) ? error.code : 'UNKNOWN';
    let message = '工程接口请求未完成，请根据错误码核对服务端配置与日志。';
    if (code === 'PAIRING_INVALID') message = '配对码无效、已使用或已过期，请在网页重新生成并在5分钟内配对。';
    else if (code === 'PROTOCOL_UNSUPPORTED') message = '连接器协议与服务端不兼容，请更新连接器或后端。';
    else if (code === 'DEVICE_REVOKED') message = '设备绑定已失效，请在网页重新生成配对码并绑定。';
    else if (code === 'INVALID_RESPONSE') message = '服务端返回网页或无效JSON，请核对工程API地址及代理前缀。';
    else if (error.status === 404) message = '后端工程接口未找到，请核对工程开关与API代理前缀。';
    else if (error.status === 401 || error.status === 403) message = '服务端拒绝认证或授权，请核对工程设备权限与绑定状态。';
    else if (error.status >= 500) message = '服务端处理失败，请查看同一时间的后端错误日志。';
    return '[接口状态 ' + error.status + ' / ' + code + '] ' + message;
  }
  if (error instanceof Error && error.name === 'TimeoutError') return '[REQUEST_TIMEOUT] 工程服务请求超时，请核对服务状态与网络。';
  const localMessages: Record<string, string> = {
    '当前Nexus未配对，请先运行pair': '当前服务尚未配对，请先在网页生成配对码并绑定设备。',
    '本机绑定已失效': '本机设备绑定已失效，请重新配对。',
    'Windows凭证保护失败': 'Windows用户凭证保护失败，请确认使用原Windows账号并重新配对。'
  };
  if (error instanceof Error && Object.hasOwn(localMessages, error.message)) return localMessages[error.message];
  return '工程连接器执行未完成，请核对服务地址、设备配对和本机配置。';
}
