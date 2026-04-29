import request from '@/utils/request';

// 设计相关接口
export const listDesigns = (keyword?: string) => {
  return request({
    url: '/tool/gen/design/list',
    method: 'get',
    params: { keyword }
  });
};

export const getDesign = (designId: number | string) => {
  return request({
    url: `/tool/gen/design/${designId}`,
    method: 'get'
  });
};

export const saveDesign = (data: any) => {
  return request({
    url: '/tool/gen/design',
    method: 'post',
    data
  });
};

export const updateDesign = (data: any) => {
  return request({
    url: '/tool/gen/design',
    method: 'put',
    data
  });
};

export const deleteDesign = (designId: number | string) => {
  return request({
    url: `/tool/gen/design/${designId}`,
    method: 'delete'
  });
};

export const generateSql = (designJson: string) => {
  return request({
    url: '/tool/gen/design/generateSql',
    method: 'post',
    data: { designJson }
  });
};

export const createTable = (sql: string, overwriteIfExists: boolean = false) => {
  return request({
    url: '/tool/gen/design/createTable',
    method: 'post',
    data: { sql, overwriteIfExists }
  });
};

export const importFromDesign = (designId: number | string, dataName: string = 'master', overwriteIfExists: boolean = false) => {
  return request({
    url: '/tool/gen/design/importToGen',
    method: 'post',
    data: { designId, dataName, overwriteIfExists }
  });
};

// Online表单数据接口
export const getOnlineForm = (tableId: number | string) => {
  return request({
    url: `/tool/gen/online/form/${tableId}`,
    method: 'get'
  });
};

export const listOnlineData = (tableId: number | string, params: any) => {
  return request({
    url: `/tool/gen/online/data/${tableId}`,
    method: 'get',
    params
  });
};

export const getOnlineData = (tableId: number | string, dataId: number | string) => {
  return request({
    url: `/tool/gen/online/data/${tableId}/${dataId}`,
    method: 'get'
  });
};

export const addOrUpdateOnlineData = (tableId: number | string, data: any) => {
  return request({
    url: `/tool/gen/online/data/${tableId}`,
    method: 'post',
    data
  });
};

export const deleteOnlineData = (tableId: number | string, ids: (number | string)[]) => {
  return request({
    url: `/tool/gen/online/data/${tableId}`,
    method: 'delete',
    params: { ids: ids.join(',') }
  });
};
