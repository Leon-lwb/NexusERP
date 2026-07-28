import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 返工工单视图对象 */
export interface NcrReworkOrderVO {
  reworkId: number;
  reworkCode?: string;
  ncrId?: number;
  originalWoId?: number;
  originalWoNo?: string;
  reworkWoId?: number;
  reworkWoNo?: string;
  reworkQty?: number;
  status?: string;
  remark?: string;
  createTime?: string;
}

/** 返工工单查询条件 */
export interface NcrReworkOrderQuery extends PageQuery {
  reworkCode?: string;
  ncrId?: number;
  status?: string;
}

/** 分页查询返工工单列表 */
export const listRework = (query?: NcrReworkOrderQuery): AxiosPromise<NcrReworkOrderVO[]> =>
  request({ url: '/mes/ncr/rework/list', method: 'get', params: query });

/** 获取返工工单详情 */
export const getRework = (reworkId: number | string): AxiosPromise<NcrReworkOrderVO> =>
  request({ url: `/mes/ncr/rework/${reworkId}`, method: 'get' });
