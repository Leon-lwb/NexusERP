import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ProFlowCardVO, ProFlowCardQuery } from './flowcard';

/** 分页查询流转卡列表 */
export const listFlowCard = (query?: ProFlowCardQuery): AxiosPromise<ProFlowCardVO[]> => {
  return request({
    url: '/mes/pro/flow-card/list',
    method: 'get',
    params: query
  });
};

/** 查询流转卡详情 */
export const getFlowCard = (cardId: string | number): AxiosPromise<ProFlowCardVO> => {
  return request({
    url: `/mes/pro/flow-card/${cardId}`,
    method: 'get'
  });
};

/** 按工单查询流转卡列表 */
export const listFlowCardByWoId = (mesWoId: string | number): AxiosPromise<ProFlowCardVO[]> => {
  return request({
    url: `/mes/pro/flow-card/by-wo/${mesWoId}`,
    method: 'get'
  });
};

/** 创建流转卡 */
export const addFlowCard = (mesWoId: number | string, quantity: number) => {
  return request({
    url: '/mes/pro/flow-card',
    method: 'post',
    params: { mesWoId, quantity }
  });
};

/** 下达流转卡 */
export const releaseFlowCard = (cardId: string | number) => {
  return request({
    url: `/mes/pro/flow-card/release/${cardId}`,
    method: 'put'
  });
};

/** 开始工序 */
export const startStep = (cardId: number | string, stepRecordId: number | string, operatorId?: number) => {
  return request({
    url: '/mes/pro/flow-card/start-step',
    method: 'put',
    params: { cardId, stepRecordId, operatorId }
  });
};

/** 完成工序 */
export const completeStep = (cardId: number | string, stepRecordId: number | string,
  goodQty: number, defectQty?: number, scrapQty?: number) => {
  return request({
    url: '/mes/pro/flow-card/complete-step',
    method: 'put',
    params: { cardId, stepRecordId, goodQty, defectQty, scrapQty }
  });
};

/** 完成流转卡 */
export const completeFlowCard = (cardId: string | number) => {
  return request({
    url: `/mes/pro/flow-card/complete/${cardId}`,
    method: 'put'
  });
};

/** 关闭流转卡 */
export const closeFlowCard = (cardId: string | number) => {
  return request({
    url: `/mes/pro/flow-card/close/${cardId}`,
    method: 'put'
  });
};
