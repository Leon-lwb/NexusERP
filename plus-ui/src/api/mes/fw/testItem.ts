import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 测试项标准视图对象 */
export interface FwTestItemVO {
  testItemId: number;
  itemCode: string;
  itemName: string;
  materialId: number;
  firmwareId?: number;
  dataType: string;
  unit?: string;
  upperLimit?: number;
  lowerLimit?: number;
  targetValue?: number;
  passEnum?: string;
  isRequired: string;
  sortOrder?: number;
  remark?: string;
  createTime?: string;
}

/** 测试项查询条件 */
export interface FwTestItemQuery extends PageQuery {
  itemCode?: string;
  itemName?: string;
  materialId?: number;
  firmwareId?: number;
  dataType?: string;
  isRequired?: string;
}

/** 测试项表单 */
export interface FwTestItemForm {
  testItemId?: number;
  itemCode: string;
  itemName: string;
  materialId: number;
  firmwareId?: number;
  dataType: string;
  unit?: string;
  upperLimit?: number;
  lowerLimit?: number;
  targetValue?: number;
  passEnum?: string;
  isRequired?: string;
  sortOrder?: number;
  remark?: string;
}

/** 分页查询测试项 */
export const listTestItem = (query?: FwTestItemQuery): AxiosPromise<FwTestItemVO[]> =>
  request({ url: '/mes/fw/test-item/list', method: 'get', params: query });

/** 获取测试项详情 */
export const getTestItem = (testItemId: number | string): AxiosPromise<FwTestItemVO> =>
  request({ url: `/mes/fw/test-item/${testItemId}`, method: 'get' });

/** 新增测试项 */
export const addTestItem = (data: FwTestItemForm) => request({ url: '/mes/fw/test-item', method: 'post', data });

/** 修改测试项 */
export const updateTestItem = (data: FwTestItemForm) => request({ url: '/mes/fw/test-item', method: 'put', data });

/** 批量删除测试项(被测试记录引用时禁止) */
export const delTestItem = (ids: (number | string)[]) =>
  request({ url: `/mes/fw/test-item/${ids.join(',')}`, method: 'delete' });

/** 按物料+固件范围查询测试项(开单/下拉) */
export const listTestItemByMaterial = (materialId: number, firmwareId?: number): AxiosPromise<FwTestItemVO[]> =>
  request({ url: '/mes/fw/test-item/by-material', method: 'get', params: { materialId, firmwareId } });