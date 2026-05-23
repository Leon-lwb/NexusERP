import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { PurchaseReturnQuery, PurchaseReturnVO, PurchaseReturnForm } from './types';

/**
 * 采购退货模块API
 * 提供采购退货单的增删改查及相关业务操作接口
 */
const BASE = '/erp/pu/return';

/**
 * 查询采购退货单列表
 * @param query 采购退货单查询条件
 * @returns 采购退货单列表及总数
 */
export const listPurchaseReturn = (query: PurchaseReturnQuery): AxiosPromise<{ rows: PurchaseReturnVO[]; total: number }> =>
  request({ 
    url: BASE + '/list', 
    method: 'get', 
    params: query 
  });

/**
 * 获取指定ID的采购退货单详情
 * @param id 采购退货单ID
 * @returns 采购退货单详细信息
 */
export const getPurchaseReturn = (id: string | number): AxiosPromise<PurchaseReturnVO> =>
  request({ 
    url: BASE + '/' + id, 
    method: 'get' 
  });

/**
 * 新增采购退货单
 * @param data 采购退货单表单数据
 * @returns 请求结果
 */
export const addPurchaseReturn = (data: PurchaseReturnForm) =>
  request({ 
    url: BASE, 
    method: 'post', 
    data 
  });

/**
 * 修改采购退货单
 * @param data 采购退货单表单数据
 * @returns 请求结果
 */
export const updatePurchaseReturn = (data: PurchaseReturnForm) =>
  request({ 
    url: BASE, 
    method: 'put', 
    data 
  });

/**
 * 删除采购退货单
 * @param id 采购退货单ID或ID数组
 * @returns 请求结果
 */
export const delPurchaseReturn = (id: string | number | (string | number)[]) =>
  request({ 
    url: BASE + '/' + id, 
    method: 'delete' 
  });

/**
 * 导出采购退货单数据
 * @param query 采购退货单查询条件
 * @returns 下载链接
 */
export const exportPurchaseReturn = (query: PurchaseReturnQuery) =>
  request({ 
    url: BASE + '/export', 
    method: 'post', 
    params: query 
  });

/**
 * 确认采购退货单
 * @param id 采购退货单ID
 * @returns 请求结果
 */
export const confirmPurchaseReturn = (id: number) => 
  request({ 
    url: BASE + '/confirm/' + id, 
    method: 'put' 
  });

/**
 * 执行采购退货单
 * @param id 采购退货单ID
 * @returns 请求结果
 */
export const executePurchaseReturn = (id: number) => 
  request({ 
    url: BASE + '/execute/' + id, 
    method: 'put' 
  });