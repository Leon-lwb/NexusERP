import request from '@/utils/request';
import type { AxiosPromise } from 'axios';
import type {
  CodeResolution,
  ResolveCodeRequest,
  SerialAliasRequest,
  SerialReservation,
  ManualSerialReservationRequest,
  SerialRetirementRequest,
  SerialRetirement
} from './types';

/** 统一解析条码、二维码及完整 OCR 对象，不做本地首条匹配。 */
export const resolveMaterialCode = (data: ResolveCodeRequest): AxiosPromise<CodeResolution> =>
  request({ url: '/erp/wm/code/resolve', method: 'post', data, headers: { repeatSubmit: false } });

/** 正式发号只登记 REGISTERED，同一命令可重放，不在客户端设置库存状态。 */
export const reserveSerialIdentities = (data: ManualSerialReservationRequest): AxiosPromise<SerialReservation> => {
  if (data.sourceType !== 'ERP_MANUAL' || data.sourceId !== null) return Promise.reject(new Error('PC手工登记仅允许ERP_MANUAL和空来源ID'));
  return request({ url: '/erp/wm/sn/reserve', method: 'post', data, headers: { repeatSubmit: false } });
};

/** 别名保留来源主体，不改写正式 SN。 */
export const registerSerialAlias = (identityId: string, data: SerialAliasRequest) =>
  request({ url: `/erp/wm/sn/${encodeURIComponent(identityId)}/aliases`, method: 'post', data, headers: { repeatSubmit: false } });

/** 弃用原命令可重放；expectedVersion必须来自服务端原身份，禁止Number化或补造。 */
export const retireSerialIdentity = (identityId: string, data: SerialRetirementRequest): AxiosPromise<SerialRetirement> => {
  if (!data.reason?.trim() || data.reason.length > 500) return Promise.reject(new Error('弃用原因必填且不能超过500字'));
  if (typeof data.expectedVersion !== 'string' || !/^[1-9]\d*$/.test(data.expectedVersion))
    return Promise.reject(new Error('缺少服务端身份原版本，不能弃用'));
  if (!identityId || !data.commandId?.trim()) return Promise.reject(new Error('弃用身份或命令缺失'));
  return request({ url: `/erp/wm/sn/${encodeURIComponent(identityId)}/retire`, method: 'post', data, headers: { repeatSubmit: false } });
};
