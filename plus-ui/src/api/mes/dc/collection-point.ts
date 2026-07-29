import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 采集点视图对象 */
export interface DcCollectionPointVO {
  pointId: number;
  pointCode: string;
  pointName: string;
  pointType: string;
  equipmentId?: number;
  iotDeviceId?: number | string;
  iotPropertyCode?: string;
  processId?: number;
  dataType?: string;
  unit?: string;
  frequency?: number;
  status: string;
  remark?: string;
}

/** 采集点查询条件 */
export interface DcCollectionPointQuery extends PageQuery {
  pointCode?: string;
  pointName?: string;
  pointType?: string;
  dataType?: string;
  status?: string;
  iotDeviceId?: number | string;
}

/** 采集点表单 */
export interface DcCollectionPointForm {
  pointId?: number;
  pointCode: string;
  pointName: string;
  pointType: string;
  equipmentId?: number;
  iotDeviceId?: number | string;
  iotPropertyCode?: string;
  processId?: number;
  dataType?: string;
  unit?: string;
  frequency?: number;
  status?: string;
  remark?: string;
}

/** 分页查询采集点 */
export const listCollectionPoint = (query?: DcCollectionPointQuery): AxiosPromise<DcCollectionPointVO[]> =>
  request({ url: '/mes/dc/collection-point/list', method: 'get', params: query });

/** 获取采集点详情 */
export const getCollectionPoint = (pointId: string | number): AxiosPromise<DcCollectionPointVO> =>
  request({ url: `/mes/dc/collection-point/${pointId}`, method: 'get' });

/** 新增采集点 */
export const addCollectionPoint = (data: DcCollectionPointForm): AxiosPromise<DcCollectionPointVO> =>
  request({ url: '/mes/dc/collection-point', method: 'post', data });

/** 编辑采集点 */
export const updateCollectionPoint = (data: DcCollectionPointForm): AxiosPromise<DcCollectionPointVO> =>
  request({ url: '/mes/dc/collection-point', method: 'put', data });

/** 批量删除采集点 */
export const delCollectionPoint = (ids: (string | number)[]) => request({ url: `/mes/dc/collection-point/${ids.join(',')}`, method: 'delete' });
