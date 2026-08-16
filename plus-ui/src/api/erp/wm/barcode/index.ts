import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import {
  BarcodeConfigForm,
  BarcodeConfigQuery,
  BarcodeConfigVO,
  BarcodeForm,
  BarcodeQuery,
  BarcodeVO
} from './types';

/**
 * 条形码配置基础路径
 */
const CONFIG_BASE = '/erp/wm/barcode/config';

/**
 * 条形码实例基础路径
 */
const BARCODE_BASE = '/erp/wm/barcode';

/**
 * 查询条形码配置列表
 * @param query 条形码配置查询条件
 * @returns 条形码配置列表及总数
 */
export const listBarcodeConfig = (query: BarcodeConfigQuery): AxiosPromise<{ rows: BarcodeConfigVO[]; total: number }> =>
  request({ url: CONFIG_BASE + '/list', method: 'get', params: query });

/**
 * 获取指定ID的条形码配置详情
 * @param id 条形码配置ID
 * @returns 条形码配置详细信息
 */
export const getBarcodeConfig = (id: string | number): AxiosPromise<BarcodeConfigVO> =>
  request({ url: CONFIG_BASE + '/' + id, method: 'get' });

/**
 * 新增条形码配置
 * @param data 条形码配置表单数据
 * @returns 请求结果
 */
export const addBarcodeConfig = (data: BarcodeConfigForm) =>
  request({ url: CONFIG_BASE, method: 'post', data });

/**
 * 修改条形码配置
 * @param data 条形码配置表单数据
 * @returns 请求结果
 */
export const updateBarcodeConfig = (data: BarcodeConfigForm) =>
  request({ url: CONFIG_BASE, method: 'put', data });

/**
 * 删除条形码配置
 * @param id 条形码配置ID或ID数组
 * @returns 请求结果
 */
export const delBarcodeConfig = (id: string | number | (string | number)[]) =>
  request({ url: CONFIG_BASE + '/' + id, method: 'delete' });

/**
 * 查询条形码实例列表
 * @param query 条形码实例查询条件
 * @returns 条形码实例列表及总数
 */
export const listBarcode = (query: BarcodeQuery): AxiosPromise<{ rows: BarcodeVO[]; total: number }> =>
  request({ url: BARCODE_BASE + '/list', method: 'get', params: query });

/**
 * 获取指定ID的条形码实例详情
 * @param id 条形码实例ID
 * @returns 条形码实例详细信息
 */
export const getBarcode = (id: string | number): AxiosPromise<BarcodeVO> =>
  request({ url: BARCODE_BASE + '/' + id, method: 'get' });

/**
 * 新增条形码实例
 * @param data 条形码实例表单数据
 * @returns 请求结果
 */
export const addBarcode = (data: BarcodeForm) =>
  request({ url: BARCODE_BASE, method: 'post', data });

/**
 * 修改条形码实例
 * @param data 条形码实例表单数据
 * @returns 请求结果
 */
export const updateBarcode = (data: BarcodeForm) =>
  request({ url: BARCODE_BASE, method: 'put', data });

/**
 * 删除条形码实例
 * @param id 条形码实例ID或ID数组
 * @returns 请求结果
 */
export const delBarcode = (id: string | number | (string | number)[]) =>
  request({ url: BARCODE_BASE + '/' + id, method: 'delete' });

/**
 * 根据配置生成条形码
 * @param params 生成条形码所需的参数
 * @returns 生成的条形码字符串
 */
export const generateBarcode = (params: {
  configId?: number;
  configCode?: string;
  bizType?: string;
  bizId?: number;
  bizLineId?: number;
  materialId?: number;
  batchId?: number;
  stockId?: number;
  snId?: number;
}) =>
  request({ url: BARCODE_BASE + '/generateByCode', method: 'post', params });