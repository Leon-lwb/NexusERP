import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { PurchaseOrderQuery, PurchaseOrderVO, PurchaseOrderForm, PurchaseOrderLineVO } from './types';

/**
 * 采购订单模块API
 * 提供采购订单的增删改查及相关业务操作接口
 */
const BASE = '/erp/pu/order';

/**
 * 查询采购订单列表
 * @param query 采购订单查询条件
 * @returns 采购订单列表及总数
 */
export const listPurchaseOrder = (query: PurchaseOrderQuery): AxiosPromise<{ rows: PurchaseOrderVO[]; total: number }> =>
  request({ 
    url: BASE + '/list', 
    method: 'get', 
    params: query 
  });

/**
 * 获取指定ID的采购订单详情
 * @param id 采购订单ID
 * @returns 采购订单详细信息
 */
export const getPurchaseOrder = (id: string | number): AxiosPromise<PurchaseOrderVO> =>
  request({ 
    url: BASE + '/' + id, 
    method: 'get' 
  });

/**
 * 新增采购订单
 * @param data 采购订单表单数据
 * @returns 请求结果
 */
export const addPurchaseOrder = (data: PurchaseOrderForm) =>
  request({ 
    url: BASE, 
    method: 'post', 
    data 
  });

/**
 * 修改采购订单
 * @param data 采购订单表单数据
 * @returns 请求结果
 */
export const updatePurchaseOrder = (data: PurchaseOrderForm) =>
  request({ 
    url: BASE, 
    method: 'put', 
    data 
  });

/**
 * 删除采购订单
 * @param id 采购订单ID或ID数组
 * @returns 请求结果
 */
export const delPurchaseOrder = (id: string | number | (string | number)[]) =>
  request({ 
    url: BASE + '/' + id, 
    method: 'delete' 
  });

/**
 * 导出采购订单数据
 * @param query 采购订单查询条件
 * @returns 下载链接
 */
export const exportPurchaseOrder = (query: PurchaseOrderQuery) =>
  request({ 
    url: BASE + '/export', 
    method: 'post', 
    params: query 
  });

/**
 * 提交采购订单
 * @param id 采购订单ID
 * @returns 请求结果
 */
export const submitPurchaseOrder = (id: number) => 
  request({ 
    url: BASE + '/submit/' + id, 
    method: 'put' 
  });

/**
 * 审核采购订单
 * @param id 采购订单ID
 * @returns 请求结果
 */
export const approvePurchaseOrder = (id: number) => 
  request({ 
    url: BASE + '/approve/' + id, 
    method: 'put' 
  });

/**
 * 关闭采购订单
 * @param id 采购订单ID
 * @returns 请求结果
 */
export const closePurchaseOrder = (id: number) => 
  request({ 
    url: BASE + '/close/' + id, 
    method: 'put' 
  });

/**
 * 取消采购订单
 * @param id 采购订单ID
 * @returns 请求结果
 */
export const cancelPurchaseOrder = (id: number) => 
  request({ 
    url: BASE + '/cancel/' + id, 
    method: 'put' 
  });

/**
 * 确认收货采购订单
 * @param id 采购订单ID
 * @returns 请求结果
 */
export const receivePurchaseOrder = (id: number) => 
  request({ 
    url: BASE + '/receive/' + id, 
    method: 'post' 
  });

/**
 * 根据请购单生成采购订单
 * @param requisitionId 请购单ID
 * @returns 请求结果
 */
export const generateFromRequisition = (requisitionId: number): AxiosPromise<any> =>
  request({
    url: BASE + '/generateFromRequisition/' + requisitionId,
    method: 'post'
  });

/**
 * 提交流程审批（Flowable 审批流）
 * @param id 订单ID
 * @param selectFlowId 审批流ID
 * @returns 请求结果
 */
export const submitOrderFlow = (id: string, selectFlowId?: string) =>
  request({
    url: BASE + '/' + id + '/submit',
    method: 'post',
    params: selectFlowId ? { selectFlowId } : {}
  });

/**
 * 获取流程实例ID
 * @param id 订单ID
 * @returns 流程实例ID
 */
export const getOrderProcId = (id: string) =>
  request({
    url: BASE + '/' + id + '/proc',
    method: 'get'
  });

/**
 * 查询收货差异报表
 * 返回所有存在收货差异（部分到货/超收/短收）的采购订单明细行
 * @returns 存在差异的订单行列表
 */
export const getDiffReport = (): AxiosPromise<any[]> =>
  request({
    url: BASE + '/diff-report',
    method: 'get'
  });

export const listOrderLines = (orderId: number): AxiosPromise<PurchaseOrderLineVO[]> =>
  request({
    url: BASE + '/' + orderId + '/lines',
    method: 'get'
  });