import { traceSave, executeTraceDocument } from '@/api/erp/wm/identity/document';
import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import {
  ProductionIssueQuery,
  ProductionIssueVO,
  ProductionIssueForm,
  ProductionIssueLineForm,
  AvailableStockVO,
  ProductionIssueDecimal
} from './types';

export const listProductionIssue = (query: ProductionIssueQuery): AxiosPromise<ProductionIssueVO[]> => {
  return request({
    url: '/erp/wm/productionissue/list',
    method: 'get',
    params: query
  });
};

export const getProductionIssue = (issueId: string | number): AxiosPromise<ProductionIssueVO> => {
  return request({
    url: '/erp/wm/productionissue/' + issueId,
    method: 'get'
  });
};

export const addProductionIssue = (data: ProductionIssueForm) => {
  return request({
    url: '/erp/wm/productionissue',
    method: 'post',
    data: traceSave(data)
  });
};

export const updateProductionIssue = (data: ProductionIssueForm) => {
  return request({
    url: '/erp/wm/productionissue',
    method: 'put',
    data: traceSave(data)
  });
};

export const delProductionIssue = (issueIds: string | number | (string | number)[]) => {
  return request({
    url: '/erp/wm/productionissue/' + issueIds,
    method: 'delete'
  });
};

/** 执行生产领料过账（扣减库存），支持多次部分领料 */
export const executeProductionIssue = (issueId: string | number, data?: Record<string, any>) =>
  executeTraceDocument({
    url: '/erp/wm/productionissue/execute/' + issueId,
    detailUrl: '/erp/wm/productionissue/' + issueId,
    linesKey: 'wmProductionIssueLineList',
    quantityKey: 'currentQty',
    data
  });

/** 查询物料可用库存（ATP），按 FEFO 排序 */
export const getAvailableStock = (materialId: number, warehouseId?: number): AxiosPromise<AvailableStockVO[]> => {
  return request({
    url: '/erp/wm/productionissue/availableStock',
    method: 'get',
    params: { materialId, warehouseId }
  });
};

/** 批量查询多个物料的总可用库存（汇总所有仓库/批次/库位） */
export const getAvailableStockBatch = (materialIds: (string | number)[]): AxiosPromise<Record<string, ProductionIssueDecimal | null>> => {
  return request({
    url: '/erp/wm/productionissue/availableStockBatch',
    method: 'get',
    params: { materialIds: materialIds.join(',') }
  });
};

/** 根据工单 BOM 物料需求构建领料单行（排除倒冲物料） */
export const getWorkOrderLines = (woId: string | number): AxiosPromise<ProductionIssueLineForm[]> => {
  return request({
    url: '/erp/wm/productionissue/workOrderLines/' + woId,
    method: 'get'
  });
};
