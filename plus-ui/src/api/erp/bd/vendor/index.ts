import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { VendorQuery, VendorVO, VendorForm } from './types';

export const listVendor = (query: VendorQuery): AxiosPromise<VendorVO[]> => {
  return request({
    url: '/erp/bd/vendor/list',
    method: 'get',
    params: query
  });
};

export const getVendor = (vendorId: string | number): AxiosPromise<VendorVO> => {
  return request({
    url: '/erp/bd/vendor/' + vendorId,
    method: 'get'
  });
};

export const addVendor = (data: VendorForm) => {
  return request({
    url: '/erp/bd/vendor',
    method: 'post',
    data
  });
};

export const updateVendor = (data: VendorForm) => {
  return request({
    url: '/erp/bd/vendor',
    method: 'put',
    data
  });
};

export const delVendor = (vendorId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/bd/vendor/' + vendorId,
    method: 'delete'
  });
};
