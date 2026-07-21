import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { CategoryAttributeVO, CategoryAttributeForm } from './types';

/** 按分类查询属性模板列表 */
export const listCategoryAttr = (categoryId?: string | number): AxiosPromise<CategoryAttributeVO[]> => {
  return request({
    url: '/erp/bd/category-attr/list',
    method: 'get',
    params: { categoryId }
  });
};

export const getCategoryAttr = (attrId: string | number): AxiosPromise<CategoryAttributeVO> => {
  return request({
    url: '/erp/bd/category-attr/' + attrId,
    method: 'get'
  });
};

export const addCategoryAttr = (data: CategoryAttributeForm) => {
  return request({
    url: '/erp/bd/category-attr',
    method: 'post',
    data
  });
};

export const updateCategoryAttr = (data: CategoryAttributeForm) => {
  return request({
    url: '/erp/bd/category-attr',
    method: 'put',
    data
  });
};

export const delCategoryAttr = (attrIds: string | number | (string | number)[]) => {
  const id = Array.isArray(attrIds) ? attrIds.join(',') : attrIds;
  return request({
    url: '/erp/bd/category-attr/' + id,
    method: 'delete'
  });
};
