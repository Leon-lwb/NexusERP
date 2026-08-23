import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 行政区划视图对象 */
export interface AreaVO {
  pkid?: string | number;
  parentAreaId?: string | number;
  ancestors?: string;
  areaId?: string | number;
  areaName?: string;
  layered?: number;
  sort?: number;
  lat?: string;
  lng?: string;
  address?: string;
  remark?: string;
  children?: AreaVO[];
}

/** 行政区划查询条件 */
export interface AreaQuery extends PageQuery {
  areaName?: string;
  layered?: number;
  parentAreaId?: string | number;
}

/** 行政区划表单 */
export interface AreaForm {
  pkid?: string | number;
  parentAreaId?: string | number;
  areaId?: string | number;
  areaName?: string;
  layered?: number;
  sort?: number;
  lat?: string;
  lng?: string;
  address?: string;
  remark?: string;
}

// 查询行政区划列表
export const listArea = (query?: AreaQuery): AxiosPromise<AreaVO[]> => {
  return request({
    url: '/system/area/list',
    method: 'get',
    params: query
  });
};

// 查询行政区划列表（排除节点）
export const listAreaExcludeChild = (areaId: string | number): AxiosPromise<AreaVO[]> => {
  return request({
    url: '/system/area/list/exclude/' + areaId,
    method: 'get'
  });
};

// 查询行政区划详细
export const getArea = (pkid: string | number): AxiosPromise<AreaVO> => {
  return request({
    url: '/system/area/' + pkid,
    method: 'get'
  });
};

// 新增行政区划
export const addArea = (data: AreaForm): AxiosPromise<void> => {
  return request({
    url: '/system/area',
    method: 'post',
    data: data
  });
};

// 修改行政区划
export const updateArea = (data: AreaForm): AxiosPromise<void> => {
  return request({
    url: '/system/area',
    method: 'put',
    data: data
  });
};

// 删除行政区划
export const delArea = (pkid: string | number | Array<string | number>): AxiosPromise<void> => {
  return request({
    url: '/system/area/' + pkid,
    method: 'delete'
  });
};

// 查询行政区划子节点列表
export const listChildren = (query?: AreaQuery): AxiosPromise<AreaVO[]> => {
  return request({
    url: '/system/area/listChildren',
    method: 'get',
    params: query
  });
};

// 获取所有省份
export const getProvinces = (): AxiosPromise<AreaVO[]> => {
  return request({
    url: '/system/area/provinces',
    method: 'get'
  });
};

// 获取下一级
export const getNextLevel = (parentAreaId: string | number): AxiosPromise<AreaVO[]> => {
  return request({
    url: '/system/area/nextLevel/' + parentAreaId,
    method: 'get'
  });
};
