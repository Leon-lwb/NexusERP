import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ProductQuery, ProductVO, ProductForm } from './types';

export const listProduct = (query: ProductQuery): AxiosPromise<ProductVO[]> => {
  return request({
    url: '/iot/product/list',
    method: 'get',
    params: query
  });
};

export const getProduct = (productId: string | number): AxiosPromise<ProductVO> => {
  return request({
    url: '/iot/product/' + productId,
    method: 'get'
  });
};

export const addProduct = (data: ProductForm) => {
  return request({
    url: '/iot/product',
    method: 'post',
    data
  });
};

export const updateProduct = (data: ProductForm) => {
  return request({
    url: '/iot/product',
    method: 'put',
    data
  });
};

export const delProduct = (productId: string | number | (string | number)[]) => {
  return request({
    url: '/iot/product/' + productId,
    method: 'delete'
  });
};
