import request from '@/utils/request';
import { InventorySummaryQuery, InventoryAgingQuery, InventoryTurnoverQuery } from './types';

// 库存汇总报表
export function getInventorySummary(query: InventorySummaryQuery) {
  return request({
    url: '/erp/wm/report/summary',
    method: 'get',
    params: query
  });
}

// 库龄分析报表
export function getInventoryAging(query: InventoryAgingQuery) {
  return request({
    url: '/erp/wm/report/aging',
    method: 'get',
    params: query
  });
}

// 库存周转率报表
export function getInventoryTurnover(query: InventoryTurnoverQuery) {
  return request({
    url: '/erp/wm/report/turnover',
    method: 'get',
    params: query
  });
}
