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

/**
 * 提交审批流程
 *
 * @param contractIdStr 合同ID字符串（雪花ID序列化为字符串）
 * @param selectFlowId  流程定义ID
 * @returns 是否启动成功
 */
export const submitContract = (contractIdStr: string, selectFlowId: string) => {
  return request({
    url: '/erp/so/contract/submit',
    method: 'post',
    params: { contractIdStr, selectFlowId }
  });
};

/**
 * 获取流程实例ID
 *
 * @param contractIdStr 合同ID字符串
 * @returns 流程实例ID，无则返回 null
 */
export const getProcInsId = (contractIdStr: string): AxiosPromise<string> => {
  return request({
    url: '/erp/so/contract/procInsId/' + contractIdStr,
    method: 'get'
  });
};
