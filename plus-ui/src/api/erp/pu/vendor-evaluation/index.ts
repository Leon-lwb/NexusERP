import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { VendorEvaluationVO, VendorEvaluationQuery, VendorEvaluationForm } from './types';

/**
 * 供应商评估模块API
 * 提供供应商评估的增删改查及相关业务操作接口
 */
const BASE = '/erp/pu/vendor-evaluation';

/**
 * 查询供应商评估列表
 * @param query 供应商评估查询条件
 * @returns 供应商评估列表及总数
 */
export const listVendorEvaluation = (query: VendorEvaluationQuery): AxiosPromise<VendorEvaluationVO[]> =>
  request({
    url: BASE + '/list',
    method: 'get',
    params: query
  });

/**
 * 获取指定ID的供应商评估详情
 * @param id 供应商评估ID
 * @returns 供应商评估详细信息
 */
export const getVendorEvaluation = (id: string | number): AxiosPromise<VendorEvaluationVO> =>
  request({
    url: BASE + '/' + id,
    method: 'get'
  });

/**
 * 新增供应商评估
 * @param data 供应商评估表单数据
 * @returns 请求结果
 */
export const addVendorEvaluation = (data: VendorEvaluationForm) =>
  request({
    url: BASE,
    method: 'post',
    data
  });

/**
 * 修改供应商评估
 * @param data 供应商评估表单数据
 * @returns 请求结果
 */
export const updateVendorEvaluation = (data: VendorEvaluationForm) =>
  request({
    url: BASE,
    method: 'put',
    data
  });

/**
 * 删除供应商评估
 * @param id 供应商评估ID或ID数组
 * @returns 请求结果
 */
export const delVendorEvaluation = (id: string | number | (string | number)[]) =>
  request({
    url: BASE + '/' + id,
    method: 'delete'
  });

/**
 * 计算指定供应商的评估
 * @param vendorId 供应商ID
 * @returns 请求结果
 */
export const calculateVendorEvaluation = (vendorId: string | number) =>
  request({
    url: BASE + '/calculate/' + vendorId,
    method: 'post'
  });

/**
 * 批量计算所有活跃供应商的评估
 * @returns 请求结果
 */
export const batchCalculateAll = () =>
  request({
    url: BASE + '/calculate-all',
    method: 'post'
  });

export const getVendorEvaluationTrend = (vendorId: number): AxiosPromise<VendorEvaluationVO[]> =>
  request({
    url: BASE + '/trend/' + vendorId,
    method: 'get'
  });

export const getVendorEvaluationDistribution = (): AxiosPromise<Record<string, number>> =>
  request({
    url: BASE + '/distribution',
    method: 'get'
  });
