import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ClientQuery, ClientVO, ClientForm } from './types';

export const listClient = (query: ClientQuery): AxiosPromise<ClientVO[]> => {
  return request({
    url: '/erp/bd/client/list',
    method: 'get',
    params: query
  });
};

export const getClient = (clientId: string | number): AxiosPromise<ClientVO> => {
  return request({
    url: '/erp/bd/client/' + clientId,
    method: 'get'
  });
};

export const addClient = (data: ClientForm) => {
  return request({
    url: '/erp/bd/client',
    method: 'post',
    data
  });
};

export const updateClient = (data: ClientForm) => {
  return request({
    url: '/erp/bd/client',
    method: 'put',
    data
  });
};

export const delClient = (clientId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/bd/client/' + clientId,
    method: 'delete'
  });
};

export const listBd = listClient;
