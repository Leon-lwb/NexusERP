import request, { download } from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 备件主数据视图对象 */
export interface EqSparePartVO {
  spareId: number;
  spareCode: string;
  spareName: string;
  spec?: string;
  model?: string;
  unit?: string;
  category?: string;
  safetyStock?: number;
  currentStock?: number;
  lowStock?: boolean;
  remark?: string;
}

/** 备件查询条件 */
export interface EqSparePartQuery extends PageQuery {
  spareCode?: string;
  spareName?: string;
  category?: string;
}

/** 备件表单 */
export interface EqSparePartForm {
  spareId?: number;
  spareCode: string;
  spareName: string;
  spec?: string;
  model?: string;
  unit?: string;
  category?: string;
  safetyStock?: number;
  remark?: string;
}

/** 出入库参数 */
export interface EqSpareStockBo {
  spareId: number;
  transactionType: 'IN' | 'OUT' | 'REPAIR_USE';
  quantity: number;
  refType?: string;
  refId?: number;
  remark?: string;
}

/** 库存流水视图对象 */
export interface EqSpareStockLogVO {
  logId: number;
  spareId: number;
  spareCode?: string;
  spareName?: string;
  transactionType: string;
  quantity?: number;
  beforeQty?: number;
  afterQty?: number;
  refType?: string;
  refId?: number;
  operatorName?: string;
  operateTime?: string;
  remark?: string;
}

/** 分页查询备件列表 */
export const listSparePart = (query?: EqSparePartQuery): AxiosPromise<EqSparePartVO[]> =>
  request({ url: '/mes/eq/spare-part/list', method: 'get', params: query });

/** 获取备件详情 */
export const getSparePart = (spareId: number | string): AxiosPromise<EqSparePartVO> =>
  request({ url: `/mes/eq/spare-part/${spareId}`, method: 'get' });

/** 新增备件 */
export const addSparePart = (data: EqSparePartForm): AxiosPromise<EqSparePartVO> => request({ url: '/mes/eq/spare-part', method: 'post', data });

/** 修改备件 */
export const updateSparePart = (data: EqSparePartForm): AxiosPromise<void> => request({ url: '/mes/eq/spare-part', method: 'put', data });

/** 删除备件 */
export const delSparePart = (spareIds: number | number[]) => request({ url: `/mes/eq/spare-part/${spareIds}`, method: 'delete' });

/** 导出备件 */
export const exportSparePart = (query?: EqSparePartQuery) =>
  download('/mes/eq/spare-part/export', { ...query }, `spare_part_${new Date().getTime()}.xlsx`);

/** 出入库操作 */
export const stockTransaction = (data: EqSpareStockBo): AxiosPromise<number> => request({ url: '/mes/eq/spare-part/stock', method: 'post', data });

/** 库存流水分页查询 */
export const listStockLog = (params?: any): AxiosPromise<EqSpareStockLogVO[]> =>
  request({ url: '/mes/eq/spare-part/stock-log', method: 'get', params });

/** 导出库存流水 */
export const exportStockLog = (params?: any) =>
  download('/mes/eq/spare-part/stock-log/export', { ...params }, `spare_stock_log_${new Date().getTime()}.xlsx`);

/** 低于安全库存的备件 */
export const listLowStock = (): AxiosPromise<EqSparePartVO[]> => request({ url: '/mes/eq/spare-part/lowStock', method: 'get' });
