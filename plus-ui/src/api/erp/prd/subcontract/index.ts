import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { PrdSubcontractVO, PrdSubcontractQuery, PrdSubcontractForm } from './types';

export function listSubcontract(query: PrdSubcontractQuery): AxiosPromise<PrdSubcontractVO[]> {
  return request({ url: '/erp/prd/subcontract/list', method: 'get', params: query });
}

export function getSubcontract(subcontractId: number): AxiosPromise<PrdSubcontractVO> {
  return request({ url: '/erp/prd/subcontract/' + subcontractId, method: 'get' });
}

export function addSubcontract(data: PrdSubcontractForm) {
  return request({ url: '/erp/prd/subcontract', method: 'post', data });
}

export function updateSubcontract(data: PrdSubcontractForm) {
  return request({ url: '/erp/prd/subcontract', method: 'put', data });
}

export function delSubcontract(subcontractIds: number | number[]) {
  return request({ url: '/erp/prd/subcontract/' + subcontractIds, method: 'delete' });
}

export function releaseSubcontract(subcontractId: number) {
  return request({ url: '/erp/prd/subcontract/release/' + subcontractId, method: 'put' });
}

export function startSubcontract(subcontractId: number) {
  return request({ url: '/erp/prd/subcontract/start/' + subcontractId, method: 'put' });
}

export function completeSubcontract(subcontractId: number) {
  return request({ url: '/erp/prd/subcontract/complete/' + subcontractId, method: 'put' });
}

export function closeSubcontract(subcontractId: number) {
  return request({ url: '/erp/prd/subcontract/close/' + subcontractId, method: 'put' });
}

const BASE = '/erp/prd/subcontract';

export const issueSubcontractMaterial = (subcontractId: number) =>
  request({ url: BASE + '/issue/' + subcontractId, method: 'put' });

export const receiveSubcontractMaterial = (subcontractId: number) =>
  request({ url: BASE + '/receive/' + subcontractId, method: 'put' });

export function submitApproval(id: string | number) {
  return request({ url: '/erp/prd/subcontract/submit/' + id, method: 'post' });
}

export function getOperationLogs(id: string | number) {
  return request({ url: '/erp/prd/subcontract/logs/' + id, method: 'get' });
}
