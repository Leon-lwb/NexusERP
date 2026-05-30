import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { WorkOrderResourceQuery, WorkOrderResourceVO, WorkOrderResourceForm } from './types';

// 资源类型常量
export const RESOURCE_TYPE = {
  WORKSHOP: 'WORKSHOP',
  WORKSTATION: 'WORKSTATION',
  EQUIPMENT: 'EQUIPMENT',
  TEAM: 'TEAM',
  PRODUCTION_LINE: 'PRODUCTION_LINE'
};

// 资源类型标签
export const RESOURCE_TYPE_LABEL = {
  WORKSHOP: '车间',
  WORKSTATION: '工作站',
  EQUIPMENT: '设备',
  TEAM: '班组',
  PRODUCTION_LINE: '生产线'
};

// 状态常量
export const RESOURCE_STATUS = {
  PENDING: 'PENDING',
  PROCESSING: 'PROCESSING',
  COMPLETED: 'COMPLETED'
};

// 状态标签
export const RESOURCE_STATUS_LABEL = {
  PENDING: '待执行',
  PROCESSING: '执行中',
  COMPLETED: '已完成'
};

export const listWorkOrderResource = (query: WorkOrderResourceQuery): AxiosPromise<WorkOrderResourceVO[]> => {
  return request({
    url: '/erp/prd/workOrderResource/list',
    method: 'get',
    params: query
  });
};

export const getWorkOrderResource = (id: string | number): AxiosPromise<WorkOrderResourceVO> => {
  return request({
    url: '/erp/prd/workOrderResource/' + id,
    method: 'get'
  });
};

export const addWorkOrderResource = (data: WorkOrderResourceForm) => {
  return request({
    url: '/erp/prd/workOrderResource',
    method: 'post',
    data
  });
};

export const updateWorkOrderResource = (data: WorkOrderResourceForm) => {
  return request({
    url: '/erp/prd/workOrderResource',
    method: 'put',
    data
  });
};

export const delWorkOrderResource = (id: string | number | (string | number)[]) => {
  return request({
    url: '/erp/prd/workOrderResource/' + id,
    method: 'delete'
  });
};

export const getWorkOrderResourceByWoId = (woId: string | number): AxiosPromise<WorkOrderResourceVO[]> => {
  return request({
    url: '/erp/prd/workOrderResource/wo/' + woId,
    method: 'get'
  });
};

export const getWorkOrderResourceByWoIdAndType = (woId: string | number, resourceType: string): AxiosPromise<WorkOrderResourceVO[]> => {
  return request({
    url: '/erp/prd/workOrderResource/wo/' + woId + '/type/' + resourceType,
    method: 'get'
  });
};

export const saveWorkOrderResourceBatch = (woId: string | number, resourceList: WorkOrderResourceForm[]) => {
  return request({
    url: '/erp/prd/workOrderResource/batch/' + woId,
    method: 'post',
    data: resourceList
  });
};
