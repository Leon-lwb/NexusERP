import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { RoutingQuery, RoutingVO, RoutingForm, RoutingStepVO, RoutingStepForm } from './types';

/**
 * 查询工艺路线列表
 */
export const listRouting = (query: RoutingQuery): AxiosPromise<RoutingVO[]> => {
  return request({
    url: '/erp/bd/routing/list',
    method: 'get',
    params: query
  });
};

/**
 * 查询工艺路线详细
 */
export const getRouting = (routingId: string | number): AxiosPromise<RoutingVO> => {
  return request({
    url: '/erp/bd/routing/' + routingId,
    method: 'get'
  });
};

/**
 * 新增工艺路线
 */
export const addRouting = (data: RoutingForm) => {
  return request({
    url: '/erp/bd/routing',
    method: 'post',
    data
  });
};

/**
 * 修改工艺路线
 */
export const updateRouting = (data: RoutingForm) => {
  return request({
    url: '/erp/bd/routing',
    method: 'put',
    data
  });
};

/**
 * 删除工艺路线
 */
export const delRouting = (routingId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/bd/routing/' + routingId,
    method: 'delete'
  });
};

/**
 * 激活工艺路线版本
 */
export const activateRoutingVersion = (routingId: number) => {
  return request({
    url: '/erp/bd/routing/activateVersion/' + routingId,
    method: 'put'
  });
};

/**
 * 查询物料的当前工艺路线
 */
export const getCurrentRouting = (materialId: number): AxiosPromise<RoutingVO> => {
  return request({
    url: '/erp/bd/routing/current/' + materialId,
    method: 'get'
  });
};

/**
 * 获取工艺路线的工序步骤
 */
export const getRoutingSteps = (routingId: number): AxiosPromise<RoutingStepVO[]> => {
  return request({
    url: '/erp/bd/routing/steps/' + routingId,
    method: 'get'
  });
};

/**
 * 校验工艺路线
 */
export const verifyRouting = (routingId: number): AxiosPromise<string> => {
  return request({
    url: '/erp/bd/routing/verify/' + routingId,
    method: 'get'
  });
};
