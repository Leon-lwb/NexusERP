import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { PriceComparisonVO, PriceComparisonQuery, PriceTrendVO, PriceRankingVO } from './types';

/**
 * 采购价格对比模块API
 * 提供物料供应商价格对比、价格趋势、价格排名等查询接口
 */
const BASE = '/erp/bd/materialVendor/report';

/**
 * 查询物料供应商价格对比
 * @param query 价格对比查询条件
 * @returns 供应商价格对比列表
 */
export const listPriceComparison = (query: PriceComparisonQuery): AxiosPromise<PriceComparisonVO[]> =>
  request({
    url: BASE + '/priceComparison',
    method: 'get',
    params: query
  });

/**
 * 查询物料价格趋势
 * @param materialId 物料ID
 * @param months 查询月数（默认12个月）
 * @returns 价格趋势数据列表
 */
export const getPriceTrend = (materialId: number, months?: number): AxiosPromise<PriceTrendVO[]> =>
  request({
    url: BASE + '/priceTrend',
    method: 'get',
    params: { materialId, months }
  });

/**
 * 查询物料价格排名
 * @param materialId 物料ID
 * @returns 价格排名数据列表
 */
export const getPriceRanking = (materialId: number): AxiosPromise<PriceRankingVO[]> =>
  request({
    url: BASE + '/priceRanking',
    method: 'get',
    params: { materialId }
  });
