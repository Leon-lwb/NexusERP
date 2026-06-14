import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { InvoiceQuery, InvoiceVO, InvoiceForm } from './types';

/**
 * 分页查询销售发票列表
 *
 * @param query 查询参数
 * @returns 销售发票分页列表
 */
export const listInvoice = (query: InvoiceQuery): AxiosPromise<InvoiceVO[]> => {
  return request({
    url: '/erp/so/invoice/list',
    method: 'get',
    params: query
  });
};

/**
 * 根据ID查询销售发票详情
 *
 * @param invoiceId 发票ID
 * @returns 销售发票详情
 */
export const getInvoice = (invoiceId: string | number): AxiosPromise<InvoiceVO> => {
  return request({
    url: '/erp/so/invoice/' + invoiceId,
    method: 'get'
  });
};

/**
 * 新增销售发票
 *
 * @param data 销售发票表单数据
 * @returns 操作结果
 */
export const addInvoice = (data: InvoiceForm) => {
  return request({
    url: '/erp/so/invoice',
    method: 'post',
    data
  });
};

/**
 * 修改销售发票
 *
 * @param data 销售发票表单数据
 * @returns 操作结果
 */
export const updateInvoice = (data: InvoiceForm) => {
  return request({
    url: '/erp/so/invoice',
    method: 'put',
    data
  });
};

/**
 * 删除销售发票
 *
 * @param invoiceId 发票ID（支持批量删除）
 * @returns 操作结果
 */
export const delInvoice = (invoiceId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/so/invoice/' + invoiceId,
    method: 'delete'
  });
};
