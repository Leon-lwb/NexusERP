import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import type { MeasureQuery, MeasureVO, MeasureForm, QuantityConfirmationContext, QuantityConfirmationBody } from './types';
import { normalizeBusinessId, readCertifiedBusiness, postCertifiedBusiness } from '@/utils/certified-business';
import type { CertifiedBusinessSession } from '@/utils/certified-business';

export const listMeasure = (query: MeasureQuery): AxiosPromise<MeasureVO[]> => {
  return request({
    url: '/erp/bd/measure/list',
    method: 'get',
    params: query
  });
};

export const getMeasure = (uomId: string | number): AxiosPromise<MeasureVO> => {
  return request({
    url: '/erp/bd/measure/' + uomId,
    method: 'get'
  });
};

export const addMeasure = (data: MeasureForm) => {
  return request({
    url: '/erp/bd/measure',
    method: 'post',
    data
  });
};

export const updateMeasure = (data: MeasureForm) => {
  return request({
    url: '/erp/bd/measure',
    method: 'put',
    data
  });
};

export const delMeasure = (uomId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/bd/measure/' + uomId,
    method: 'delete'
  });
};

export const listMeasureAll = (query?: MeasureQuery): AxiosPromise<MeasureVO[]> => {
  return request({
    url: '/erp/bd/measure/all',
    method: 'get',
    params: query
  });
};

export const listByPrimary = (primaryId: string | number): AxiosPromise<MeasureVO[]> => {
  return request({
    url: '/erp/bd/measure/byPrimary/' + primaryId,
    method: 'get'
  });
};

export const listCategories = (): AxiosPromise<string[]> => {
  return request({
    url: '/erp/bd/measure/categories',
    method: 'get'
  });
};

/** 独立 query-only 主体提示，不依赖请购权限，不授予数量写资格。 */
export const getQuantityConfirmationContext = (session: CertifiedBusinessSession) =>
  readCertifiedBusiness<QuantityConfirmationContext>('/erp/bd/measure/quantity-context', session);

/** 核验前后均使用原生单位详情；绑定固定凭证，禁用普通 CRUD 的动态账号读取。 */
export const getQuantityConfirmationDetail = (uomId: string, session: CertifiedBusinessSession) => {
  const id = normalizeBusinessId(uomId);
  if (!id) throw new Error('单位身份无效');
  return readCertifiedBusiness<MeasureVO>('/erp/bd/measure/' + id, session);
};

/** 只挑选既有 BO 字段，证据必须人工录入；不提交身份、时间、verified 或意图 requestKey。 */
export const confirmMeasureQuantity = (uomId: string, body: QuantityConfirmationBody, session: CertifiedBusinessSession) => {
  const id = normalizeBusinessId(uomId);
  if (!id) throw new Error('单位身份无效');
  return postCertifiedBusiness<MeasureVO>(
    '/erp/bd/measure/' + id + '/confirm-quantity',
    {
      expectedVersion: body.expectedVersion,
      primaryId: body.primaryId,
      conversionRate: body.conversionRate,
      quantityScale: body.quantityScale,
      reference: body.reference
    },
    session
  );
};
