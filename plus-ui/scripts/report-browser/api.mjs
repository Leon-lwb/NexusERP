import { reactive } from 'vue';

/** 仅用于隔离浏览器的合成身份与完整雪花标识，不读取本地业务配置。 */
export const fixture = Object.freeze({
  tenant: '000000',
  userId: '9223372036854775800',
  assetId: '9223372036854775806',
  assetVersionId: '9223372036854775807',
  reportId: '9223372036854775805',
  reportVersionId: '9223372036854775804',
  nextReportVersionId: '9223372036854775785',
  datasetVersionId: '9223372036854775803',
  assetCode: 'SYNTHETIC_LOGO',
  reportCode: 'SYNTHETIC_ORDER',
  permissions: [
    'report:asset:list',
    'report:runtime:preview',
    'report:runtime:export',
    'report:definition:add',
    'report:definition:edit',
    'report:definition:list',
    'report:dataset:list',
    'report:batch:manage',
    'report:batch:list',
    'report:query:list',
    'report:dashboard:list',
    'report:subscription:list'
  ]
});

/** 当前合成会话只存在内存中，服务端还会独立复核身份和权限。 */
export const session = reactive({ tenant: fixture.tenant, userId: fixture.userId, permissions: [...fixture.permissions] });

/** 将真实API契约转发到本次Vite内的合成路由，禁止携带真实令牌或外部地址。 */
export default async function request(options) {
  if (!options.url.startsWith('/report/')) throw Error('隔离夹具拒绝非报表请求：' + options.url);
  const url = new URL('/__report_mock' + options.url, location.origin);
  for (const [key, value] of Object.entries(options.params || {})) if (value !== undefined) url.searchParams.set(key, String(value));
  const response = await fetch(url, {
    method: options.method || 'GET',
    headers: {
      'Content-Type': 'application/json',
      'X-Report-Test-Tenant': session.tenant,
      'X-Report-Test-User': session.userId,
      'X-Report-Test-Permissions': session.permissions.join(',')
    },
    body: options.data === undefined ? undefined : JSON.stringify(options.data),
    credentials: 'omit',
    redirect: 'error'
  });
  const data = options.responseType === 'blob' ? await response.blob() : await response.json();
  if (!response.ok || (typeof data.code === 'number' && data.code !== 200))
    throw Object.assign(Error(data.msg || '合成报表请求失败'), data, { status: response.status, response: { status: response.status, data } });
  return data;
}
