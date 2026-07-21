import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { CategoryQuery, CategoryVO, CategoryForm, CategoryWithMaterialsVO } from './types';

export const listCategory = (query?: CategoryQuery): AxiosPromise<CategoryVO[]> => {
  return request({
    url: '/erp/bd/category/list',
    method: 'get',
    params: query
  });
};

/** 查询分类列表，排除指定分类及其所有子孙（用于父级下拉防循环） */
export const listCategoryExcludeChild = (categoryId: string | number | (string | number)[]): AxiosPromise<CategoryVO[]> => {
  const id = Array.isArray(categoryId) ? categoryId.join(',') : categoryId;
  return request({
    url: '/erp/bd/category/list/exclude/' + id,
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

/** 修改分类状态（内联开关，轻量更新） */
export const changeCategoryStatus = (categoryId: string | number, status: number) => {
  return request({
    url: '/erp/bd/category/' + categoryId + '/status',
    method: 'put',
    params: { status }
  });
};

/** 移动分类到新父级（拖拽改父级，sortOrder 自动追加到新父级子级末尾） */
export const moveCategory = (categoryId: string | number, parentId: string | number) => {
  return request({
    url: '/erp/bd/category/' + categoryId + '/move',
    method: 'put',
    params: { parentId }
  });
};

export const delCategory = (categoryId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/bd/category/' + categoryId,
    method: 'delete'
  });
};

export const getAllChildrenIds = (categoryIds: string | number | (string | number)[]): AxiosPromise<CategoryWithMaterialsVO> => {
  const id = Array.isArray(categoryIds) ? categoryIds.join(',') : categoryIds;
  return request({
    url: '/erp/bd/category/allChildrenIds/' + id,
    method: 'get'
  });
};
