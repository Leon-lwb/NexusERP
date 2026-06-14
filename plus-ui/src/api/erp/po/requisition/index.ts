import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { RequisitionQuery, RequisitionVO, RequisitionForm } from './types';

/**
 * 分页查询部门请购单列表
 *
 * @param query 查询参数
 * @returns 部门请购单分页列表
 */
export const listRequisition = (query: RequisitionQuery): AxiosPromise<RequisitionVO[]> => {
  return request({
    url: '/erp/po/requisition/list',
    method: 'get',
    params: query
  });
};

/**
 * 根据ID查询部门请购单详情
 *
 * @param requisitionId 请购单ID
 * @returns 部门请购单详情（含明细行）
 */
export const getRequisition = (requisitionId: string | number): AxiosPromise<RequisitionVO> => {
  return request({
    url: '/erp/po/requisition/' + requisitionId,
    method: 'get'
  });
};

/**
 * 新增部门请购单
 *
 * @param data 部门请购单表单数据
 * @returns 操作结果
 */
export const addRequisition = (data: RequisitionForm) => {
  return request({
    url: '/erp/po/requisition',
    method: 'post',
    data
  });
};

/**
 * 修改部门请购单
 *
 * @param data 部门请购单表单数据
 * @returns 操作结果
 */
export const updateRequisition = (data: RequisitionForm) => {
  return request({
    url: '/erp/po/requisition',
    method: 'put',
    data
  });
};

/**
 * 删除部门请购单
 *
 * @param requisitionId 请购单ID（支持批量删除）
 * @returns 操作结果
 */
export const delRequisition = (requisitionId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/po/requisition/' + requisitionId,
    method: 'delete'
  });
};
