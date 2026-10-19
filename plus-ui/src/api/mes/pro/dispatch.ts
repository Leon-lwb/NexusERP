import request from '@/utils/request';
import { AxiosPromise } from 'axios';

export interface ProDispatchVO {
  dispatchId: number;
  dispatchNo: string;
  mesWoId: number;
  woNo: string;
  cardId: number;
  cardNo: string;
  stepRecordId: number;
  sequenceNum: number;
  processId: number;
  processCode: string;
  processName: string;
  workcenterId: number;
  workcenterCode: string;
  operatorId: number;
  operatorName: string;
  plannedQty: number;
  completedQty: number;
  goodQty: number;
  defectQty: number;
  scrapQty: number;
  dispatchStatus: string;
  planStartTime: string;
  planEndTime: string;
  actualStartTime: string;
  actualEndTime: string;
  remark: string;
}

export interface ProDispatchQuery extends PageQuery {
  dispatchNo?: string;
  operatorId?: number;
  workcenterId?: number;
  dispatchStatus?: string;
  mesWoId?: number;
  cardId?: number;
}

export const listDispatch = (query?: ProDispatchQuery): AxiosPromise<ProDispatchVO[]> =>
  request({ url: '/mes/pro/dispatch/list', method: 'get', params: query });

export const getDispatch = (dispatchId: string | number): AxiosPromise<ProDispatchVO> =>
  request({ url: `/mes/pro/dispatch/${dispatchId}`, method: 'get' });

export const listDispatchByStep = (stepRecordId: string | number): AxiosPromise<ProDispatchVO[]> =>
  request({ url: `/mes/pro/dispatch/by-step/${stepRecordId}`, method: 'get' });

export const listDispatchByOperator = (operatorId: string | number): AxiosPromise<ProDispatchVO[]> =>
  request({ url: `/mes/pro/dispatch/by-operator/${operatorId}`, method: 'get' });

export const addDispatch = (cardId: number, stepRecordId: number, operatorId: number, operatorName: string, quantity: number) =>
  request({ url: '/mes/pro/dispatch', method: 'post', params: { cardId, stepRecordId, operatorId, operatorName, quantity } });

export const startDispatch = (dispatchId: string | number) => request({ url: `/mes/pro/dispatch/start/${dispatchId}`, method: 'put' });

export const completeDispatch = (dispatchId: string | number, goodQty: string | number, defectQty?: string | number, scrapQty?: string | number) =>
  request({ url: `/mes/pro/dispatch/complete/${dispatchId}`, method: 'put', params: { goodQty, defectQty, scrapQty } });

export const cancelDispatch = (dispatchId: string | number) => request({ url: `/mes/pro/dispatch/cancel/${dispatchId}`, method: 'put' });

export const getFlowCardByBarcode = (barcode: string): AxiosPromise<any> =>
  request({ url: `/mes/pro/flow-card/by-barcode/${barcode}`, method: 'get' });
