import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import type { AxiosRequestConfig } from 'axios';
import { ReceiveQuery, ReceiveVO, ReceiveForm } from './types';

export const listReceive = (query: ReceiveQuery): AxiosPromise<ReceiveVO[]> => {
  return request({
    url: '/erp/wm/receive/list',
    method: 'get',
    params: query
  });
};

export const getReceive = (receiveId: string | number): AxiosPromise<ReceiveVO> => {
  return request({
    url: '/erp/wm/receive/' + receiveId,
    method: 'get'
  });
};

export const addReceive = (data: ReceiveForm, session?: CertifiedBusinessSession) => {
  return guardedWarehouseRequest(
    {
      url: '/erp/wm/receive',
      method: 'post',
      data
    },
    session
  );
};

export const updateReceive = (data: ReceiveForm, session?: CertifiedBusinessSession) => {
  return guardedWarehouseRequest(
    {
      url: '/erp/wm/receive',
      method: 'put',
      data
    },
    session
  );
};

export const delReceive = (receiveId: string | number | (string | number)[], session?: CertifiedBusinessSession) => {
  return guardedWarehouseRequest(
    {
      url: '/erp/wm/receive/' + receiveId,
      method: 'delete'
    },
    session
  );
};

export const execute = (receiveId: string | number, data: any, session?: CertifiedBusinessSession) => {
  return guardedWarehouseRequest(
    {
      url: '/erp/wm/receive/execute/' + receiveId,
      method: 'post',
      data
    },
    session
  );
};

export const confirmShelf = (receiveId: string | number, session?: CertifiedBusinessSession) => {
  return guardedWarehouseRequest(
    {
      url: '/erp/wm/receive/shelf/' + receiveId,
      method: 'put'
    },
    session
  );
};

export const submitReceive = (receiveId: string, selectFlowId?: string, session?: CertifiedBusinessSession) => {
  return guardedWarehouseRequest(
    {
      url: '/erp/wm/receive/' + receiveId + '/submit',
      method: 'post',
      params: selectFlowId ? { selectFlowId } : {}
    },
    session
  );
};

export const getReceiveProcId = (receiveId: string) => {
  return request({
    url: '/erp/wm/receive/' + receiveId + '/proc',
    method: 'get'
  });
};

import { readCertifiedBusiness, postCertifiedBusiness, normalizeBusinessId } from '@/utils/certified-business';
import type { CertifiedBusinessSession } from '@/utils/certified-business';
import type { CertifiedWarehouseContext, CertifiedWarehouseDetail, WarehouseTarget } from '../arrivalnotice/types';
import type { CertifiedReceiveDraftRequest, CertifiedReceiveDraftResult, CertifiedReceivePostingResult, CertifiedReceiveRecovery } from './types';
const receiveRoot = '/erp/wm/receive/certified';
function certifiedReceiveId(value: unknown): string {
  const id = normalizeBusinessId(value);
  if (!id) throw new Error('无效收货身份');
  return id;
}
/** 独立收货 query 主体，不借用到货域权限。 */
export const getCertifiedReceiveContext = (session: CertifiedBusinessSession) =>
  readCertifiedBusiness<CertifiedWarehouseContext>(`${receiveRoot}/workflow-context`, session).then((data) => ({ code: 200, data }));
export const getCertifiedReceiveDetail = (kind: WarehouseTarget, id: string, session: CertifiedBusinessSession) => {
  if (kind !== 'ORDER' && kind !== 'RECEIVE') throw new Error('收货域不能读取到货详情');
  return readCertifiedBusiness<CertifiedWarehouseDetail>(
    `${receiveRoot}/${kind === 'ORDER' ? 'order/' : ''}${certifiedReceiveId(id)}/workflow-detail`,
    session
  ).then((data) => ({ code: 200, data }));
};
/** 只创建草稿；不提交审批、不分配批次、不产生库存。 */
export const createCertifiedReceiveDraft = (body: CertifiedReceiveDraftRequest, session: CertifiedBusinessSession) =>
  postCertifiedBusiness<CertifiedReceiveDraftResult>(`${receiveRoot}/draft`, body, session);
/** 实际接口无 body，不提交客户端数量/批准/IQC。 */
export const shelfCertifiedReceive = (id: string, session: CertifiedBusinessSession) =>
  postCertifiedBusiness<void>(`${receiveRoot}/shelf/${certifiedReceiveId(id)}`, undefined, session);
export const executeCertifiedReceive = (id: string, session: CertifiedBusinessSession) =>
  postCertifiedBusiness<CertifiedReceivePostingResult>(`${receiveRoot}/execute/${certifiedReceiveId(id)}`, undefined, session);
/** 永久恢复 GET 只读，不调用 execute、不自动释放 UNKNOWN。 */
export const recoverCertifiedReceive = (id: string, session: CertifiedBusinessSession) =>
  readCertifiedBusiness<CertifiedReceiveRecovery>(`${receiveRoot}/recovery/${certifiedReceiveId(id)}`, session).then((data) => ({ code: 200, data }));

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
