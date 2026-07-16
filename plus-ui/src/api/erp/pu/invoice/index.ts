import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { InvoiceQuery, InvoiceVO, InvoiceForm, MatchResult } from './types';

/**
 * 采购进项发票模块API
 * 提供采购进项发票的登记/收票/三单匹配/认证/抵扣/核销/红冲/作废全生命周期接口
 */
const BASE = '/erp/pu/invoice';

/**
 * 查询采购进项发票列表
 * @param query 查询条件
 * @returns 发票列表及总数
 */
export const listInvoice = (query: InvoiceQuery): AxiosPromise<{ rows: InvoiceVO[]; total: number }> =>
  request({
    url: BASE + '/list',
    method: 'get',
    params: query
  });

/**
 * 获取指定ID的采购进项发票详情(含明细行)
 * @param id 发票ID
 * @returns 发票详细信息
 */
export const getInvoice = (id: string | number): AxiosPromise<InvoiceVO> =>
  request({
    url: BASE + '/' + id,
    method: 'get'
  });

/**
 * 新增采购进项发票
 * @param data 发票表单数据
 */
export const addInvoice = (data: InvoiceForm) =>
  request({
    url: BASE,
    method: 'post',
    data
  });

/**
 * 从采购订单生成发票草稿(按"已收货未开票"自动汇总)
 * @param poId 采购订单ID
 * @returns 新建的发票ID(无可开票行时为 null)
 */
export const generateFromPo = (poId: number): AxiosPromise<number> =>
  request({
    url: BASE + '/generateFromPo/' + poId,
    method: 'post'
  });

/**
 * 修改采购进项发票(差量更新明细行 + 头乐观锁)
 * @param data 发票表单数据
 */
export const updateInvoice = (data: InvoiceForm) =>
  request({
    url: BASE,
    method: 'put',
    data
  });

/**
 * 收票确认(草稿 -> 已收)
 * @param id 发票ID
 */
export const receiveInvoice = (id: number) =>
  request({
    url: BASE + '/receive/' + id,
    method: 'put'
  });

/**
 * 三单匹配校验(PO + 收货 + 发票)
 * @param id 发票ID
 * @returns 匹配结果(含差异明细)
 */
export const matchInvoice = (id: number): AxiosPromise<MatchResult> =>
  request({
    url: BASE + '/match/' + id,
    method: 'get'
  });

/**
 * 核销发票(累加已核销金额,达价税合计则转已核销;前置三单匹配)
 * @param id 发票ID
 * @param verifyAmount 本次核销金额
 */
export const verifyInvoice = (id: number, verifyAmount: number) =>
  request({
    url: BASE + '/verify/' + id,
    method: 'put',
    params: { verifyAmount }
  });

/**
 * 进项认证(未认证 -> 已认证)
 * @param id 发票ID
 */
export const certifyInvoice = (id: number) =>
  request({
    url: BASE + '/certify/' + id,
    method: 'put'
  });

/**
 * 进项抵扣(已认证 -> 已抵扣)
 * @param id 发票ID
 */
export const deductInvoice = (id: number) =>
  request({
    url: BASE + '/deduct/' + id,
    method: 'put'
  });

/**
 * 作废发票(回退采购订单行已开票数量)
 * @param id 发票ID
 * @param reason 作废原因
 */
export const cancelInvoice = (id: number, reason?: string) =>
  request({
    url: BASE + '/cancel/' + id,
    method: 'put',
    params: reason ? { reason } : {}
  });

/**
 * 红冲发票(生成红字负数发票;原已抵扣则进项转出)
 * @param id 原蓝字发票ID
 * @param reason 红冲原因
 * @returns 红字发票ID
 */
export const redFlushInvoice = (id: number, reason?: string): AxiosPromise<number> =>
  request({
    url: BASE + '/redFlush/' + id,
    method: 'put',
    params: reason ? { reason } : {}
  });

/**
 * 删除采购进项发票(仅草稿可删)
 * @param id 发票ID或ID数组
 */
export const delInvoice = (id: string | number | (string | number)[]) =>
  request({
    url: BASE + '/' + id,
    method: 'delete'
  });

/**
 * 导出采购进项发票数据
 * @param query 查询条件
 */
export const exportInvoice = (query: InvoiceQuery) =>
  request({
    url: BASE + '/export',
    method: 'post',
    params: query
  });
