import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ProFlowCardVO, ProFlowCardQuery } from './flowcard';

/** 工序进站入参 */
export interface ProEntryBo {
  cardId: number;
  stepRecordId: number;
  operatorId: number;
  toolingId?: number;
  lotId?: number;
  materialId?: number;
}

/** 工序出站入参 */
export interface ProExitBo {
  cardId: number;
  stepRecordId: number;
  goodQty: string | number;
  defectQty?: string | number;
  scrapQty?: string | number;
}

/** 工序跳站入参 */
export interface ProSkipBo {
  cardId: number;
  stepRecordId: number;
  reason: string;
}

/** 校验结果 */
export interface ProCheckFailure {
  checkType: string;
  errorMsg: string;
}

/** 校验结果视图对象 */
export interface ProCheckResultVo {
  stepRecordId: number;
  passed: boolean;
  failures: ProCheckFailure[];
  sopPushed?: boolean;
}

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
export const startStep = (cardId: number | string, stepRecordId: number | string, operatorId?: number | string) => {
  return request({
    url: '/mes/pro/flow-card/start-step',
    method: 'put',
    params: { cardId, stepRecordId, operatorId }
  });
};

/** 完成工序 */
export const completeStep = (
  cardId: number | string,
  stepRecordId: number | string,
  goodQty: string | number,
  defectQty?: string | number,
  scrapQty?: string | number
) => {
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

/** 工序进站(P0-3): 执行进站校验,通过则开工并推送SOP */
export const entryFlowCard = (data: ProEntryBo): AxiosPromise<ProCheckResultVo> => {
  return request({
    url: '/mes/pro/flow-card/entry',
    method: 'post',
    data
  });
};

/** 工序出站(P0-3): 执行出站校验,通过则完工 */
export const exitFlowCard = (data: ProExitBo): AxiosPromise<ProCheckResultVo> => {
  return request({
    url: '/mes/pro/flow-card/exit',
    method: 'post',
    data
  });
};

/** 工序跳站(P0-3): 跳站防错,须填写原因 */
export const skipFlowCard = (data: ProSkipBo) => {
  return request({
    url: '/mes/pro/flow-card/skip',
    method: 'post',
    data
  });
};
