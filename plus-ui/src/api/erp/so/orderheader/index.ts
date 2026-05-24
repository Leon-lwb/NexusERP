import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { OrderheaderQuery, OrderheaderVO, OrderheaderForm, OrderLineVO } from './types';

export const listOrderheader = (query: OrderheaderQuery): AxiosPromise<OrderheaderVO[]> => {
  return request({
    url: '/erp/so/orderheader/list',
    method: 'get',
    params: query
  });
};

export const getOrderheader = (soId: string | number): AxiosPromise<OrderheaderVO> => {
  return request({
    url: '/erp/so/orderheader/' + soId,
    method: 'get'
  });
};

export const addOrderheader = (data: OrderheaderForm) => {
  return request({
    url: '/erp/so/orderheader',
    method: 'post',
    data
  });
};

export const updateOrderheader = (data: OrderheaderForm) => {
  return request({
    url: '/erp/so/orderheader',
    method: 'put',
    data
  });
};

export const delOrderheader = (soId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/so/orderheader/' + soId,
    method: 'delete'
  });
};

export const getOrderLine = (soNo: string): AxiosPromise<OrderLineVO[]> => {
  return request({
    url: '/erp/so/orderheader/listLine/' + soNo,
    method: 'get'
  });
};

export const getOrderLineById = (soLineId: string | number): AxiosPromise<OrderLineVO> => {
  return request({
    url: '/erp/so/orderheader/listLineById/' + soLineId,
    method: 'get'
  });
};

export const confirmOrder = (soId: number) =>
  request({ url: '/erp/so/orderheader/confirm/' + soId, method: 'put' });

export const fulfillOrder = (soId: number) =>
  request({ url: '/erp/so/orderheader/fulfill/' + soId, method: 'put' });

export const closeOrder = (soId: number) =>
  request({ url: '/erp/so/orderheader/close/' + soId, method: 'put' });

export const cancelOrder = (soId: number, reason: string) =>
  request({ url: '/erp/so/orderheader/cancel/' + soId, method: 'put', params: { reason } });

// ==================== Flowable 审批 ====================

export const submitOrderFlow = (id: string | number, selectFlowId?: string) => {
  return request({
    url: '/erp/so/orderheader/' + id + '/submit',
    method: 'post',
    params: { selectFlowId }
  });
};

export const getOrderProcId = (id: string | number) => {
  return request({
    url: '/erp/so/orderheader/' + id + '/proc',
    method: 'get'
  });
};
