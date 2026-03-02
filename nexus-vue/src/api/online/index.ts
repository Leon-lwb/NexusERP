import request from '@/utils/request';

export const list = (query: TableQuery) => {
  const useOnline = (import.meta as any)?.env?.VITE_FEATURE_ONLINE_API === 'true';
  const url = useOnline ? '/online/list' : '/tool/gen/list';
  return request({
    url,
    method: 'get',
    params: query
  });
};

export const getOnlineForm = (tableId: string | number) => {
  return request({
    url: '/online/' + tableId,
    method: 'get'
  });
};

export const getDataNames = () => {
  return request({
    url: '/online/getDataNames',
    method: 'get'
  });
};

export const listOnlineData = (tableId: string | number, tableName: string, query: PageQuery) => {
  return request({
    url: '/online/getData/' + tableId + '/' + tableName,
    method: 'get',
    params: query
  });
};

export const listPopupData = (tableId: string | number, dictTableName: string, query: PageQuery) => {
  return request({
    url: '/online/getPopupData/' + tableId + '/' + dictTableName,
    method: 'get',
    params: query
  });
};

export const listTableData = (tableName: string, query: PageQuery) => {
  return request({
    url: '/online/getTableData/' + tableName,
    method: 'get',
    params: query
  });
};

export const addOrUpdateOnlineData = (tableId: string | number, tableName: string, data: any) => {
  return request({
    url: '/online/addOrUpdateData/' + tableId + '/' + tableName,
    method: 'post',
    data: data
  });
};

export const addOrUpdateOnlineDataByName = (tableName: string, data: any) => {
  return request({
    url: '/online/addOrUpdateDataByName/' + tableName,
    method: 'post',
    data: data
  });
};

export function delOnlineData(tableId: number, tableName: string, ids: (string | number)) {
  return request({
    url: `/online/delOnlineData/${tableId}/${tableName}`,
    method: 'delete',
    data: ids
  });
}

export function getOnlineData(tableId: number, tableName: string, id: (string | number)) {
  return request({
    url: `/online/getOnlineData/${tableId}/${tableName}`,
    method: 'post',
    data: id
  });
}

export const getPopupDetail = (tableId: string | number, tableName: string, id: string | number) => {
  return request.get(`/online/detail/${tableId}/${tableName}/${id}`);
};
