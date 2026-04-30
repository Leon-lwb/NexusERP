import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { CategoryQuery, CategoryVO, CategoryForm, CategoryWithMaterialsVO } from './types';

export const listCategory = (query: CategoryQuery): AxiosPromise<CategoryVO[]> => {
  return request({
    url: '/erp/bd/category/list',
    method: 'get',
    params: query
  });
};

export const listDeptExcludeChild = (deptId: string | number): AxiosPromise<CategoryVO[]> => {
  return request({
    url: '/erp/bd/category/list/exclude/' + deptId,
    method: 'get'
  });
};

export const getCategory = (categoryId: string | number): AxiosPromise<CategoryVO> => {
  return request({
    url: '/erp/bd/category/' + categoryId,
    method: 'get'
  });
};

export const addCategory = (data: CategoryForm) => {
  return request({
    url: '/erp/bd/category',
    method: 'post',
    data
  });
};

export const updateCategory = (data: CategoryForm) => {
  return request({
    url: '/erp/bd/category',
    method: 'put',
    data
  });
};

export const delCategory = (categoryId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/bd/category/' + categoryId,
    method: 'delete'
  });
};

export const getAllChildrenIds = (categoryIds: (string | number)[]): AxiosPromise<CategoryWithMaterialsVO> => {
  return request({
    url: '/erp/bd/category/allChildrenIds/' + categoryIds,
    method: 'get'
  });
};
