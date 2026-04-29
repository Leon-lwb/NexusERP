import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { NformVO, NformForm, NformQuery } from '@/api/workflow/nform/types';

/**
 * 查询设计器单列表
 * @param query
 * @returns {*}
 */

export const listNform = (query?: NformQuery): AxiosPromise<NformVO[]> => {
  return request({
    url: '/workflow/nform/list',
    method: 'get',
    params: query
  });
};

/**
 * 查询设计器单详细
 * @param formId
 */
export const getNform = (formId: string | number): AxiosPromise<NformVO> => {
  return request({
    url: '/workflow/nform/' + formId,
    method: 'get'
  });
};

/**
 * 新增设计器单
 * @param data
 */
export const addNform = (data: NformForm) => {
  return request({
    url: '/workflow/nform',
    method: 'post',
    data: data
  });
};

/**
 * 修改设计器单
 * @param data
 */
export const updateNform = (data: NformForm) => {
  return request({
    url: '/workflow/nform',
    method: 'put',
    data: data
  });
};

/**
 * 删除设计器单
 * @param formId
 */
export const delNform = (formId: string | number | Array<string | number>) => {
  return request({
    url: '/workflow/nform/' + formId,
    method: 'delete'
  });
};
