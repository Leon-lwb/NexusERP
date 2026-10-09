import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import type { AxiosRequestConfig } from 'axios';
import { ArrivalnoticeQuery, ArrivalnoticeVO, ArrivalnoticeForm } from './types';

export const listArrivalnotice = (query: ArrivalnoticeQuery): AxiosPromise<ArrivalnoticeVO[]> => {
  return request({
    url: '/erp/wm/arrivalnotice/list',
    method: 'get',
    params: query
  });
};

export const getArrivalnotice = (noticeId: string | number): AxiosPromise<ArrivalnoticeVO> => {
  return request({
    url: '/erp/wm/arrivalnotice/' + noticeId,
    method: 'get'
  });
};

export const addArrivalnotice = (data: ArrivalnoticeForm, session?: CertifiedBusinessSession) => {
  return guardedWarehouseRequest(
    {
      url: '/erp/wm/arrivalnotice',
      method: 'post',
      data
    },
    session
  );
};

export const updateArrivalnotice = (data: ArrivalnoticeForm, session?: CertifiedBusinessSession) => {
  return guardedWarehouseRequest(
    {
      url: '/erp/wm/arrivalnotice',
      method: 'put',
      data
    },
    session
  );
};

export const delArrivalnotice = (noticeId: string | number | (string | number)[], session?: CertifiedBusinessSession) => {
  return guardedWarehouseRequest(
    {
      url: '/erp/wm/arrivalnotice/' + noticeId,
      method: 'delete'
    },
    session
  );
};

export const submitArrivalnotice = (noticeId: string, selectFlowId?: string, session?: CertifiedBusinessSession) => {
  return guardedWarehouseRequest(
    {
      url: '/erp/wm/arrivalnotice/' + noticeId + '/submit',
      method: 'post',
      params: selectFlowId ? { selectFlowId } : {}
    },
    session
  );
};

export const getArrivalnoticeProcId = (noticeId: string) => {
  return request({
    url: '/erp/wm/arrivalnotice/' + noticeId + '/proc',
    method: 'get'
  });
};

import { readCertifiedBusiness, postCertifiedBusiness, normalizeBusinessId } from '@/utils/certified-business';
import type { CertifiedBusinessSession } from '@/utils/certified-business';
import type { CertifiedWarehouseContext, CertifiedWarehouseDetail, WarehouseTarget, CertifiedArrivalRequest, CertifiedArrivalResult } from './types';
const arrivalRoot = '/erp/wm/arrivalnotice/certified';
/** ID 只按精确原字符串使用；不经 Number 转换。 */
function certifiedArrivalId(value: unknown): string {
  const id = normalizeBusinessId(value);
  if (!id) throw new Error('无效到货身份');
  return id;
}
/** 独立到货 query 主体，绝不请求收货域上下文。 */
export const getCertifiedArrivalContext = (session: CertifiedBusinessSession) =>
  readCertifiedBusiness<CertifiedWarehouseContext>(`${arrivalRoot}/workflow-context`, session).then((data) => ({ code: 200, data }));
/** 全量原生只读详情；订单只返回仓储所需的非价格事实。 */
export const getCertifiedArrivalDetail = (kind: WarehouseTarget, id: string, session: CertifiedBusinessSession) => {
  if (kind !== 'ORDER' && kind !== 'NOTICE') throw new Error('到货域不能读取收货');
  return readCertifiedBusiness<CertifiedWarehouseDetail>(
    `${arrivalRoot}/${kind === 'ORDER' ? 'order/' : ''}${certifiedArrivalId(id)}/workflow-detail`,
    session
  ).then((data) => ({ code: 200, data }));
};
/** 创建仅一份通知，不自动提交流程。 */
export const createCertifiedArrival = (body: CertifiedArrivalRequest, session: CertifiedBusinessSession) =>
  postCertifiedBusiness<CertifiedArrivalResult>(arrivalRoot, body, session);
/** 实际合同是 selectFlowId 查询参数，不伪造 JSON Request DTO。 */
export const submitCertifiedArrival = (id: string, flowId: string, session: CertifiedBusinessSession) => {
  if (!flowId || flowId.length > 64) throw new Error('请选择实际流程');
  return postCertifiedBusiness<void>(
    `${arrivalRoot}/${certifiedArrivalId(id)}/submit?selectFlowId=${encodeURIComponent(flowId)}`,
    undefined,
    session
  );
};
/** 人工独立确认后同步，不按关联行 actStatus 猜批准。 */
export const synchronizeCertifiedArrival = (id: string, session: CertifiedBusinessSession) =>
  postCertifiedBusiness<void>(`${arrivalRoot}/${certifiedArrivalId(id)}/synchronize`, undefined, session);

/** 通用普通单也固定当前会话，在 Axios transform 的 dispatch 边界阻止迟到账号/路由写。 */
function guardedWarehouseRequest(config: AxiosRequestConfig, session?: CertifiedBusinessSession) {
  if (!session) return request(config);
  session.assertCurrent();
  const transforms = request.defaults.transformRequest;
  const frozenConfig = {
    ...config,
    ...(config.data === undefined ? {} : { data: JSON.parse(JSON.stringify(config.data)) }),
    ...(config.params ? { params: { ...config.params } } : {})
  };
  return request({
    ...frozenConfig,
    headers: { ...config.headers, isToken: false, Authorization: 'Bearer ' + session.token },
    transformRequest: [
      ...(Array.isArray(transforms) ? transforms : transforms ? [transforms] : []),
      (body: unknown) => {
        session.assertCurrent();
        return body;
      }
    ]
  });
}
