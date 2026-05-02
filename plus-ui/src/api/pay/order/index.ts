import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { PayOrderQuery, PayOrderVO, PayOrderForm } from './types';

export const listPayOrder = (query: PayOrderQuery): AxiosPromise<PayOrderVO[]> => {
  return request({
    url: '/pay/order/list',
    method: 'get',
    params: query
  });
};

export const getPayOrder = (orderId: string | number): AxiosPromise<PayOrderVO> => {
  return request({
    url: '/pay/order/' + orderId,
    method: 'get'
  });
};

export const addPayOrder = (data: PayOrderForm) => {
  return request({
    url: '/pay/order',
    method: 'post',
    data
  });
};

export const updatePayOrder = (data: PayOrderForm) => {
  return request({
    url: '/pay/order',
    method: 'put',
    data
  });
};

export const delPayOrder = (orderId: string | number | (string | number)[]) => {
  return request({
    url: '/pay/order/' + orderId,
    method: 'delete'
  });
};

export const delPayOrderByOrderNumber = (orderNumber: string) => {
  return request({
    url: '/pay/order/orderNumber/' + orderNumber,
    method: 'delete'
  });
};
