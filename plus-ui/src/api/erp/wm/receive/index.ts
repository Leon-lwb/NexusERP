import request from '@/utils/request';
import { AxiosPromise } from 'axios';
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

export const addReceive = (data: ReceiveForm) => {
  return request({
    url: '/erp/wm/receive',
    method: 'post',
    data
  });
};

export const updateReceive = (data: ReceiveForm) => {
  return request({
    url: '/erp/wm/receive',
    method: 'put',
    data
  });
};

export const delReceive = (receiveId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/wm/receive/' + receiveId,
    method: 'delete'
  });
};

export const execute = (receiveId: string | number, data: any) => {
  return request({
    url: '/erp/wm/receive/execute/' + receiveId,
    method: 'post',
    data
  });
};

export const confirmShelf = (receiveId: string | number) => {
  return request({
    url: '/erp/wm/receive/shelf/' + receiveId,
    method: 'put'
  });
};

export const submitReceive = (receiveId: string, selectFlowId?: string) => {
  return request({
    url: '/erp/wm/receive/' + receiveId + '/submit',
    method: 'post',
    params: selectFlowId ? { selectFlowId } : {}
  });
};

export const getReceiveProcId = (receiveId: string) => {
  return request({
    url: '/erp/wm/receive/' + receiveId + '/proc',
    method: 'get'
  });
};
