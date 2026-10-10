import request from '@/utils/request';
import { readCertifiedBusiness, postCertifiedBusiness, normalizeBusinessId, normalizeBusinessVersion } from '@/utils/certified-business';
import type { CertifiedBusinessSession } from '@/utils/certified-business';
import type { CertifiedOrderContext, CertifiedOrderDetail } from './types';
import type { CertifiedOrderOperation } from '@/views/erp/pu/order/certifiedOrderWorkflow';
import { AxiosPromise } from 'axios';
import { PurchaseOrderQuery, PurchaseOrderVO, PurchaseOrderForm, PurchaseOrderLineVO } from './types';

/**
 * 采购订单模块API
 * 提供采购订单的增删改查及相关业务操作接口
 */
const BASE = '/erp/pu/order';

/** 旧普通单也固定已核验会话；不允许确认期间账号变化后由全局拦截器换凭证。 */
function legacySessionConfig(session?: CertifiedBusinessSession) {
  if (!session) return {};
  session.assertCurrent();
  const transforms = request.defaults.transformRequest;
  return {
    headers: { isToken: false, Authorization: 'Bearer ' + session.token, repeatSubmit: false, certifiedErrorBoundary: 'true' },
    transformRequest: [
      ...(Array.isArray(transforms) ? transforms : transforms ? [transforms] : []),
      (body: unknown) => {
        session.assertCurrent();
        return body;
      }
    ]
  };
}

/**
 * 查询采购订单列表
 * @param query 采购订单查询条件
 * @returns 采购订单列表及总数
 */
export const listPurchaseOrder = (query: PurchaseOrderQuery): AxiosPromise<PurchaseOrderVO[]> =>
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
export const getPurchaseOrder = (id: string | number, session?: CertifiedBusinessSession): AxiosPromise<PurchaseOrderVO> =>
  request({
    url: BASE + '/' + id,
    method: 'get',
    ...legacySessionConfig(session)
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
export const updatePurchaseOrder = (data: PurchaseOrderForm, session?: CertifiedBusinessSession) =>
  request({
    url: BASE,
    method: 'put',
    data,
    ...legacySessionConfig(session)
  });

/**
 * 删除采购订单
 * @param id 采购订单ID或ID数组
 * @returns 请求结果
 */
export const delPurchaseOrder = (id: string | number | (string | number)[], session?: CertifiedBusinessSession) =>
  request({
    url: BASE + '/' + id,
    method: 'delete',
    ...legacySessionConfig(session)
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

// /**
//  * 提交采购订单（已废弃，请使用 submitOrderFlow 走 Flowable 工作流）
//  * @param id 采购订单ID
//  * @returns 请求结果
//  */
// export const submitPurchaseOrder = (id: number) =>
//   request({
//     url: BASE + '/submit/' + id,
//     method: 'put'
//   });

// /**
//  * 审核采购订单（已废弃，请使用 Flowable 工作流审批）
//  * @param id 采购订单ID
//  * @returns 请求结果
//  */
// export const approvePurchaseOrder = (id: number) =>
//   request({
//     url: BASE + '/approve/' + id,
//     method: 'put'
//   });

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
export const receivePurchaseOrder = (id: string | number, session?: CertifiedBusinessSession) =>
  request({
    url: BASE + '/receive/' + id,
    method: 'post',
    ...legacySessionConfig(session)
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
export const submitOrderFlow = (id: string, selectFlowId?: string, session?: CertifiedBusinessSession) =>
  request({
    url: BASE + '/' + id + '/submit',
    method: 'post',
    params: selectFlowId ? { selectFlowId } : {},
    ...legacySessionConfig(session)
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

/** 当前订单主体独立query，不借用报价或审批权限。 */
export const getCertifiedOrderContext = (session: CertifiedBusinessSession) =>
  readCertifiedBusiness<CertifiedOrderContext>(BASE + '/certified/workflow-context', session);

/** 原生订单、NULL版本、按权限可见报价与逐占用身份；不调用写重放。 */
export const getCertifiedOrderDetail = (id: string | number, session: CertifiedBusinessSession) =>
  readCertifiedBusiness<CertifiedOrderDetail>(BASE + '/certified/' + normalizeBusinessId(id) + '/workflow-detail', session);

/**
 * 认证操作仅发送实际控制器白名单字段，query、编辑及旧Flowable均不代替写权限。
 * @param operation 明确单步操作
 * @param id 原订单精确身份
 * @param body 已核对原生版本的本次意图
 * @param session 固定内存凭证；派发前再复核
 */
export function submitCertifiedOrderOperation(
  operation: CertifiedOrderOperation,
  id: string,
  body: Record<string, unknown>,
  session: CertifiedBusinessSession
) {
  const base = BASE + '/certified/' + normalizeBusinessId(id);
  const keys = {
    terms: [
      'requestKey',
      'expectedOrderVersion',
      'unitPriceExcludingTax',
      'taxRate',
      'currency',
      'exchangeRate',
      'promisedArrivalDate',
      'validFrom',
      'validUntil',
      'supplierQuoteReference',
      'supplierContact',
      'evidenceReference',
      'evidenceSha256',
      'paymentTerms',
      'deliveryTerms'
    ],
    confirm: ['quoteId', 'quoteHash', 'requestKey', 'reviewNote'],
    approve: ['expectedVersion', 'quoteId', 'requestKey'],
    close: ['expectedVersion', 'requestKey'],
    cancel: ['expectedVersion', 'requestKey']
  };
  const allowed = keys[operation];
  if (!allowed || Object.keys(body).length !== allowed.length || !allowed.every((k) => Object.hasOwn(body, k)))
    throw new Error('认证操作字段不完整或夹带外部身份');
  if (typeof body.requestKey !== 'string' || !/^[A-Za-z0-9:_-]{1,128}$/.test(body.requestKey)) throw new Error('本次永久意图键无效');
  const command: Record<string, unknown> = { ...body };
  let url = base + '/' + operation;
  if (operation === 'terms') command.expectedOrderVersion = normalizeBusinessVersion(body.expectedOrderVersion);
  else if (operation !== 'confirm') command.expectedVersion = normalizeBusinessVersion(body.expectedVersion);
  if (operation === 'approve') command.quoteId = normalizeBusinessId(body.quoteId);
  if (operation === 'confirm') {
    url = base + '/terms/' + normalizeBusinessId(body.quoteId) + '/confirm';
    delete command.quoteId;
  }
  return postCertifiedBusiness<unknown>(url, command, session);
}
