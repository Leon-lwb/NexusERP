import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ContractQuery, ContractVO, ContractForm } from './types';

/**
 * 分页查询销售合同列表
 *
 * @param query 查询参数
 * @returns 销售合同分页列表
 */
export const listContract = (query: ContractQuery): AxiosPromise<ContractVO[]> => {
  return request({
    url: '/erp/so/contract/list',
    method: 'get',
    params: query
  });
};

/**
 * 根据ID查询销售合同详情
 *
 * @param contractId 合同ID
 * @returns 销售合同详情
 */
export const getContract = (contractId: string | number): AxiosPromise<ContractVO> => {
  return request({
    url: '/erp/so/contract/' + contractId,
    method: 'get'
  });
};

/**
 * 新增销售合同
 *
 * @param data 销售合同表单数据
 * @returns 操作结果
 */
export const addContract = (data: ContractForm) => {
  return request({
    url: '/erp/so/contract',
    method: 'post',
    data
  });
};

/**
 * 修改销售合同
 *
 * @param data 销售合同表单数据
 * @returns 操作结果
 */
export const updateContract = (data: ContractForm) => {
  return request({
    url: '/erp/so/contract',
    method: 'put',
    data
  });
};

/**
 * 删除销售合同
 *
 * @param contractId 合同ID（支持批量删除）
 * @returns 操作结果
 */
export const delContract = (contractId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/so/contract/' + contractId,
    method: 'delete'
  });
};
