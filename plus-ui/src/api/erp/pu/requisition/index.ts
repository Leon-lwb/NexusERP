import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { PurchaseRequisitionQuery, PurchaseRequisitionVO, PurchaseRequisitionForm } from './types';

/**
 * 采购请购模块API
 * 提供采购请购单的增删改查及相关业务操作接口
 */
const BASE = '/erp/pu/requisition';

/**
 * 查询采购请购单列表
 * @param query 采购请购单查询条件
 * @returns 采购请购单列表及总数
 */
export const listPurchaseRequisition = (query: PurchaseRequisitionQuery): AxiosPromise<{ rows: PurchaseRequisitionVO[]; total: number }> =>
  request({ 
    url: BASE + '/list', 
    method: 'get', 
    params: query 
  });

/**
 * 获取指定ID的采购请购单详情
 * @param id 采购请购单ID
 * @returns 采购请购单详细信息
 */
export const getPurchaseRequisition = (id: string | number): AxiosPromise<PurchaseRequisitionVO> =>
  request({ 
    url: BASE + '/' + id, 
    method: 'get' 
  });

/**
 * 新增采购请购单
 * @param data 采购请购单表单数据
 * @returns 请求结果
 */
export const addPurchaseRequisition = (data: PurchaseRequisitionForm) =>
  request({ 
    url: BASE, 
    method: 'post', 
    data 
  });

/**
 * 修改采购请购单
 * @param data 采购请购单表单数据
 * @returns 请求结果
 */
export const updatePurchaseRequisition = (data: PurchaseRequisitionForm) =>
  request({ 
    url: BASE, 
    method: 'put', 
    data 
  });

/**
 * 删除采购请购单
 * @param id 采购请购单ID或ID数组
 * @returns 请求结果
 */
export const delPurchaseRequisition = (id: string | number | (string | number)[]) =>
  request({ 
    url: BASE + '/' + id, 
    method: 'delete' 
  });

/**
 * 导出采购请购单数据
 * @param query 采购请购单查询条件
 * @returns 下载链接
 */
export const exportPurchaseRequisition = (query: PurchaseRequisitionQuery) =>
  request({ 
    url: BASE + '/export', 
    method: 'post', 
    params: query 
  });

/**
 * 提交采购请购单
 * @param id 采购请购单ID
 * @returns 请求结果
 */
export const submitPurchaseRequisition = (id: number) =>
  request({ 
    url: BASE + '/submit/' + id, 
    method: 'put' 
  });

/**
 * 审核采购请购单
 * @param id 采购请购单ID
 * @returns 请求结果
 */
export const approvePurchaseRequisition = (id: number) =>
  request({ 
    url: BASE + '/approve/' + id, 
    method: 'put' 
  });

/**
 * 将请购单转换为采购订单
 * @param id 请购单ID
 * @returns 新创建的采购订单ID
 */
export const convertPurchaseRequisition = (id: number): AxiosPromise<number> =>
  request({ 
    url: BASE + '/convert/' + id, 
    method: 'put' 
  });