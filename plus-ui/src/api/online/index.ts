import request from '@/utils/request';
import { TableQuery } from './types';

// 查询生成表数据 - 适配到新的统一接口
export const list = (query: TableQuery) => {
  return request({
    url: '/tool/gen/list',
    method: 'get',
    params: query
  });
};

// 查询online表详细信息
export const getOnlineForm = (tableId: string | number) => {
  return request({
    url: '/tool/gen/online/form/' + tableId,
    method: 'get'
  });
};

// 获取数据源名称
export const getDataNames = () => {
  return request({
    url: '/tool/gen/getDataNames',
    method: 'get'
  });
};

// 查询online表列表数据
export const listOnlineData = (tableId: string | number, tableName: string, query: PageQuery) => {
  return request({
    url: '/tool/gen/online/data/' + tableId,
    method: 'get',
    params: query
  });
};

// 查询 popup（字典表/关联表）数据：专用于弹窗选择
export const listPopupData = (tableId: string | number, dictTableName: string, query: PageQuery) => {
  return request({
    url: '/tool/gen/online/popupData/' + tableId + '/' + dictTableName,
    method: 'get',
    params: query
  });
};

// 查询online字典表列表数据
export const listTableData = (tableName: string, query: PageQuery) => {
  return request({
    url: '/tool/gen/online/tableData/' + tableName,
    method: 'get',
    params: query
  });
};

// 新增或更新online表单数据
export const addOrUpdateOnlineData = (tableId: string | number, tableName: string, data: any) => {
  return request({
    url: '/tool/gen/online/data/' + tableId,
    method: 'post',
    data: data
  });
};

// 新增或更新online表单数据
export const addOrUpdateOnlineDataByName = (tableName: string, data: any) => {
  return request({
    url: '/tool/gen/online/dataByName/' + tableName,
    method: 'post',
    data: data
  });
};

// 删除online表单数据
export function delOnlineData(tableId: number, tableName: string, ids: (string | number)[]) {
  return request({
    url: `/tool/gen/online/data/${tableId}`,
    method: 'delete',
    params: { ids: ids.join(',') }
  });
}

// 获取online表单详细数据
export function getOnlineData(tableId: number, tableName: string, id: string | number) {
  return request({
    url: `/tool/gen/online/data/${tableId}/${id}`,
    method: 'get'
  });
}

// 弹窗详情数据
export const getPopupDetail = (tableId: string | number, tableName: string, id: string | number) => {
  return request.get(`/tool/gen/online/popupDetail/${tableId}/${tableName}/${id}`);
};
