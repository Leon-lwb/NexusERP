import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { CustomerOrderQuery, CustomerOrderVO, CustomerOrderForm } from './types';

export const listCustomerOrder = (query: CustomerOrderQuery): AxiosPromise<CustomerOrderVO[]> => {
  return request({
    url: '/erp/so/customerOrder/list',
    method: 'get',
    params: query
  });
};

export const getCustomerOrder = (coId: string | number): AxiosPromise<CustomerOrderVO> => {
  return request({
    url: '/erp/so/customerOrder/' + coId,
    method: 'get'
  });
};

export const addCustomerOrder = (data: CustomerOrderForm) => {
  return request({
    url: '/erp/so/customerOrder',
    method: 'post',
    data
  });
};

export const updateCustomerOrder = (data: CustomerOrderForm) => {
  return request({
    url: '/erp/so/customerOrder',
    method: 'put',
    data
  });
};

export const delCustomerOrder = (coId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/so/customerOrder/' + coId,
    method: 'delete'
  });
};
