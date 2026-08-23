import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 自动编码组成视图对象 */
export interface AutoCodePartVO {
  partId?: string | number;
  ruleId?: string | number;
  partIndex?: number;
  partCode?: string;
  partName?: string;
  partType?: string;
  partLength?: number;
  inputCharacter?: string;
  fixCharacter?: string;
  dateFormat?: string;
  seriaStartNo?: number;
  seriaStep?: number;
}

/** 自动编码组成查询条件 */
export interface AutoCodePartQuery extends PageQuery {
  partCode?: string;
  partName?: string;
  ruleId?: string | number;
}

/** 自动编码组成表单 */
export interface AutoCodePartForm {
  partId?: string | number;
  ruleId?: string | number;
  partIndex?: number;
  partCode?: string;
  partName?: string;
  partType?: string;
  partLength?: number;
  inputCharacter?: string;
  fixCharacter?: string;
  dateFormat?: string;
  seriaStartNo?: number;
  seriaStep?: number;
}

export const listPart = (query?: AutoCodePartQuery): AxiosPromise<AutoCodePartVO[]> => {
  return request({
    url: '/system/autocode/part/list',
    method: 'get',
    params: query
  });
};

export const getPart = (partId: string | number): AxiosPromise<AutoCodePartVO> => {
  return request({
    url: '/system/autocode/part/' + partId,
    method: 'get'
  });
};

export const addPart = (data: AutoCodePartForm): AxiosPromise<void> => {
  return request({
    url: '/system/autocode/part',
    method: 'post',
    data: data
  });
};

export const updatePart = (data: AutoCodePartForm): AxiosPromise<void> => {
  return request({
    url: '/system/autocode/part',
    method: 'put',
    data: data
  });
};

export const delPart = (partIds: string | number | Array<string | number>): AxiosPromise<void> => {
  return request({
    url: '/system/autocode/part/' + partIds,
    method: 'delete'
  });
};
