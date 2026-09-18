import request from '@/utils/request';
import { PuContractBo, PuContractQuery, PuContractVo } from './types';
import { AxiosPromise } from 'axios';

// 查询采购合同列表
export function listContract(query: PuContractQuery): AxiosPromise<any> {
  return request({
    url: '/erp/pu/contract/list',
    method: 'get',
    params: query
  });
}

// 查询采购合同详细（ID 为雪花ID，后端序列化为字符串，故同时接受 string | number）
export function getContract(id: string | number): AxiosPromise<PuContractVo> {
  return request({
    url: '/erp/pu/contract/' + id,
    method: 'get'
  });
}

// 新增采购合同
export function addContract(data: PuContractBo) {
  return request({
    url: '/erp/pu/contract',
    method: 'post',
    data: data
  });
}

// 修改采购合同
export function updateContract(data: PuContractBo) {
  return request({
    url: '/erp/pu/contract',
    method: 'put',
    data: data
  });
}

// 删除采购合同
export function delContract(ids: Array<number | string>) {
  return request({
    url: '/erp/pu/contract/' + ids,
    method: 'delete'
  });
}

// 提交审批流程
export function submitContract(contractIdStr: string, selectFlowId: string) {
  return request({
    url: '/erp/pu/contract/submit',
    method: 'post',
    params: { contractIdStr, selectFlowId }
  });
}

// 获取流程实例ID
export function getProcInsId(contractIdStr: string) {
  return request({
    url: '/erp/pu/contract/procInsId/' + contractIdStr,
    method: 'get'
  });
}

// 查询已批准的合同列表（供采购订单选择）
export function listApprovedContract(query: PuContractQuery): AxiosPromise<any> {
  query.status = 'APPROVED';
  return request({
    url: '/erp/pu/contract/list',
    method: 'get',
    params: query
  });
}
