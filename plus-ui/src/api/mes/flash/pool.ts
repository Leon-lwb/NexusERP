import request from '@/utils/request';
import { AxiosPromise } from 'axios';

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

export interface MesResourcePoolQuery extends PageQuery {
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
/** 资源项VO(mes_resource_item) */
export interface MesResourceItemVO {
  itemId: number | string;
  poolId: number | string;
  /** 资源值(如MAC 00:1A:2B:3C:4D:5E) */
  resourceValue: string;
  /** AVAILABLE/ALLOCATED/USED/SCRAPPED */
  status: string;
  /** 已绑定SN ID */
  boundSnId?: number | string;
  boundTime?: string;
  remark?: string;
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

/**
 * 手工分配资源项(池自动分配取错号 / 需指定某号时人工补分配)
 * <p>后端端点: {@code POST /mes/flash/pool/allocate?poolId=&snId=&snCode=}, 权限 {@code mes:flash:pool:edit}</p>
 */
export function allocatePoolResource(poolId: number | string, snId: number | string, snCode?: string): AxiosPromise<MesResourceItemVO> {
  return request({ url: '/mes/flash/pool/allocate', method: 'post', params: { poolId, snId, snCode } });
}
