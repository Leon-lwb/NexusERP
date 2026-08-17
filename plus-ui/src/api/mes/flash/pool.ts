import request from '@/utils/request';

/** 资源池VO */
export interface MesResourcePoolVO {
  poolId: number | string;
  poolCode: string;
  poolName: string;
  resourceType: string;
  source: string;
  externalConfig: string;
  allocStrategy: string;
  totalCount: number;
  usedCount: number;
  status: string;
  remark: string;
  createTime: string;
}

export interface MesResourcePoolForm {
  poolId?: number | string;
  poolName: string;
  resourceType: string;
  source?: string;
  externalConfig?: string;
  allocStrategy?: string;
  status?: string;
  remark?: string;
}

export interface MesResourcePoolQuery {
  pageNum: number;
  pageSize: number;
  poolName?: string;
  resourceType?: string;
  status?: string;
}

/** 资源池分页 */
export function listPool(query: MesResourcePoolQuery) {
  return request({ url: '/mes/flash/pool/list', method: 'get', params: query });
}
export function getPool(poolId: number | string) {
  return request({ url: `/mes/flash/pool/${poolId}`, method: 'get' });
}
export function addPool(data: MesResourcePoolForm) {
  return request({ url: '/mes/flash/pool', method: 'post', data });
}
export function updatePool(data: MesResourcePoolForm) {
  return request({ url: '/mes/flash/pool', method: 'put', data });
}
export function delPool(ids: number | string | Array<number | string>) {
  return request({ url: `/mes/flash/pool/${ids}`, method: 'delete' });
}
/** 批量导入资源项(text 换行/逗号分隔) */
export function importPoolItems(poolId: number | string, text: string) {
  return request({
    url: `/mes/flash/pool/import/${poolId}`,
    method: 'post',
    data: text,
    headers: { 'Content-Type': 'text/plain' }
  });
}
