import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ValuationQuery, ValuationVO } from './types';

export const listValuation = (query: ValuationQuery): AxiosPromise<ValuationVO[]> => {
  return request({
    url: '/erp/wm/valuation/list',
    method: 'get',
    params: query
  });
};
// 导出走 useCrud handleExport → download(exportUrl, tansParams), 此处不再提供 export 函数
