import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { BarcodeConfigForm, BarcodeConfigQuery, BarcodeConfigVO, BarcodeForm, BarcodeQuery, BarcodeVO } from './types';

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
export const listBarcodeConfig = (query: BarcodeConfigQuery): AxiosPromise<BarcodeConfigVO[]> =>
  request({ url: CONFIG_BASE + '/list', method: 'get', params: query });

/**
 * 获取指定ID的条形码配置详情
 * @param id 条形码配置ID
 * @returns 条形码配置详细信息
 */
export const getBarcodeConfig = (id: string | number): AxiosPromise<BarcodeConfigVO> => request({ url: CONFIG_BASE + '/' + id, method: 'get' });

/**
 * 新增条形码配置
 * @param data 条形码配置表单数据
 * @returns 请求结果
 */
export const addBarcodeConfig = (data: BarcodeConfigForm) => request({ url: CONFIG_BASE, method: 'post', data });

/**
 * 修改条形码配置
 * @param data 条形码配置表单数据
 * @returns 请求结果
 */
export const updateBarcodeConfig = (data: BarcodeConfigForm) => request({ url: CONFIG_BASE, method: 'put', data });

/**
 * 删除条形码配置
 * @param id 条形码配置ID或ID数组
 * @returns 请求结果
 */
export const delBarcodeConfig = (id: string | number | (string | number)[]) => request({ url: CONFIG_BASE + '/' + id, method: 'delete' });

/**
 * 查询条形码实例列表
 * @param query 条形码实例查询条件
 * @returns 条形码实例列表及总数
 */
export const listBarcode = (query: BarcodeQuery): AxiosPromise<BarcodeVO[]> => request({ url: BARCODE_BASE + '/list', method: 'get', params: query });

/**
 * 获取指定ID的条形码实例详情
 * @param id 条形码实例ID
 * @returns 条形码实例详细信息
 */
export const getBarcode = (id: string | number): AxiosPromise<BarcodeVO> => request({ url: BARCODE_BASE + '/' + id, method: 'get' });

/**
 * 新增条形码实例
 * @param data 条形码实例表单数据
 * @returns 请求结果
 */
/** 单据主对象仅允许已核验的六类单据头，不接受未核验行或实物上下文。 */
export const BARCODE_DOCUMENT_TYPES = [
  'WM_MATERIAL_RECEIVE',
  'WM_PRODUCTION_RECPT',
  'WM_PRODUCTION_ISSUE',
  'WM_SALES_ISSUE',
  'WM_TRANSFER',
  'WM_MATERIAL_RETURN'
] as const;

/** 新手工登记必须选定主对象，对应ID和其它上下文均保持原字符串。 */
export function prepareBarcodeCreate(data: BarcodeForm): BarcodeForm {
  const value = { ...data };
  delete value.generateTime;
  delete value.bindTime;
  if (!['SN', 'BATCH', 'MATERIAL', 'DOCUMENT'].includes(value.primaryObjectType || '')) throw new Error('请选择条码主对象类型');
  const idFields = ['configId', 'bizId', 'bizLineId', 'materialId', 'batchId', 'stockId', 'snId'] as const;
  for (const key of idFields) {
    if (value[key] === '') delete value[key];
    if (value[key] != null && (typeof value[key] !== 'string' || !/^[1-9]\d*$/.test(value[key]!)))
      throw new Error(`${key}必须使用已有身份的完整字符串ID`);
  }
  if (value.primaryObjectType === 'SN' && !value.snId) throw new Error('SN主对象必须提供已有正式SN身份ID');
  if (value.primaryObjectType === 'BATCH' && !value.batchId) throw new Error('BATCH主对象必须提供已有批次ID');
  if (value.primaryObjectType === 'MATERIAL' && !value.materialId) throw new Error('MATERIAL主对象必须提供已有物料ID');
  if (value.primaryObjectType === 'DOCUMENT' && (!value.bizType?.trim() || !value.bizId))
    throw new Error('DOCUMENT主对象必须提供业务类型和已有单据ID');
  if (value.primaryObjectType === 'DOCUMENT') {
    if (!(BARCODE_DOCUMENT_TYPES as readonly string[]).includes(value.bizType!)) throw new Error('单据类型仅支持六种canonical业务类型');
    if (['bizLineId', 'snId', 'batchId', 'materialId', 'stockId'].some((key) => value[key as keyof BarcodeForm] != null))
      throw new Error('单据主对象不能携带未核验的行ID或实物上下文');
  }
  return value;
}
export const addBarcode = (data: BarcodeForm) => request({ url: BARCODE_BASE, method: 'post', data: prepareBarcodeCreate(data) });

/**
 * 修改条形码实例
 * @param data 条形码实例表单数据
 * @returns 请求结果
 */
/** 原编码、主体和上下文不可重绑；历史NULL类型原样保留，仅开放三个维护字段。 */
export function prepareBarcodeEdit(original: BarcodeVO, data: BarcodeForm): BarcodeForm {
  const immutable = [
    'barcodeId',
    'barcodeCode',
    'primaryObjectType',
    'configId',
    'configCode',
    'bizType',
    'bizId',
    'bizLineId',
    'materialId',
    'batchId',
    'batchCode',
    'stockId',
    'snId',
    'generateTime',
    'bindTime'
  ] as const;
  for (const key of immutable) {
    if (Object.prototype.hasOwnProperty.call(data, key) && data[key] !== original[key])
      throw new Error('已创建条码的主码、主体和上下文不可修改或重绑');
  }
  const value = { ...original };
  for (const key of ['status', 'expireTime', 'remark'] as const) {
    if (Object.prototype.hasOwnProperty.call(data, key)) value[key] = data[key];
  }
  return value;
}
export async function updateBarcode(data: BarcodeForm, original?: BarcodeVO) {
  if (!data.barcodeId) throw new Error('缺少原条码身份，不能修改');
  const snapshot = original || (await getBarcode(data.barcodeId)).data;
  if (snapshot.barcodeId !== data.barcodeId) throw new Error('原条码身份不一致，不能修改');
  return request({ url: BARCODE_BASE, method: 'put', data: prepareBarcodeEdit(snapshot, data) });
}

/**
 * 停用条形码实例，旧URL保留原号码和主体映射
 * @param id 条形码实例ID或ID数组
 * @returns 请求结果
 */
export const delBarcode = (id: string | number | (string | number)[]) => request({ url: BARCODE_BASE + '/' + id, method: 'delete' });

/**
 * 根据配置生成条形码
 * @param params 生成条形码所需的参数
 * @returns 生成的条形码字符串
 */
export const generateBarcode = (params: {
  configId?: string;
  configCode?: string;
  bizType?: string;
  bizId?: string;
  bizLineId?: string;
  materialId?: string;
  batchId?: string;
  stockId?: string;
  snId?: string;
}) => {
  const primaryObjectType = params.snId ? 'SN' : params.batchId ? 'BATCH' : params.materialId ? 'MATERIAL' : 'DOCUMENT';
  const { barcodeCode: _code, primaryObjectType: _type, ...validated } = prepareBarcodeCreate({ barcodeCode: '', primaryObjectType, ...params });
  return request({ url: BARCODE_BASE + '/generateByCode', method: 'post', params: validated });
};
