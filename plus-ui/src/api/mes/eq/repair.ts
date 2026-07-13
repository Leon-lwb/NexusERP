import request, { download } from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 维修记录视图对象 */
export interface EqRepairVO {
  repairId: number;
  repairNo: string;
  equipmentId: number;
  equipmentCode?: string;
  equipmentName?: string;
  faultType?: string;
  faultDesc?: string;
  reporterId?: number;
  reporterName?: string;
  repairerId?: number;
  repairerName?: string;
  repairStart?: string;
  repairEnd?: string;
  downtimeHours?: number;
  repairCost?: number;
  status: string;
  remark?: string;
}

/** 维修记录查询条件 */
export interface EqRepairQuery extends PageQuery {
  repairNo?: string;
  equipmentId?: number;
  status?: string;
  faultType?: string;
}

/** 维修报修表单 */
export interface EqRepairForm {
  equipmentId: number;
  faultType?: string;
  faultDesc?: string;
  remark?: string;
}

/** 分页查询维修记录 */
export const listRepair = (query?: EqRepairQuery): AxiosPromise<EqRepairVO[]> =>
  request({ url: '/mes/eq/repair/list', method: 'get', params: query });

/** 获取维修详情 */
export const getRepair = (repairId: string | number): AxiosPromise<EqRepairVO> => request({ url: `/mes/eq/repair/${repairId}`, method: 'get' });

/** 报修(新增维修单) */
export const addRepair = (data: EqRepairForm): AxiosPromise<EqRepairVO> => request({ url: '/mes/eq/repair', method: 'post', data });

/** 维修开工 */
export const startRepair = (repairId: string | number) => request({ url: `/mes/eq/repair/start/${repairId}`, method: 'put' });

/** 维修完成 */
export const completeRepair = (repairId: string | number, downtimeHours?: number, repairCost?: number, remark?: string) =>
  request({ url: `/mes/eq/repair/complete/${repairId}`, method: 'put', params: { downtimeHours, repairCost, remark } });

/** 导出维修记录 */
export const exportRepair = (query?: EqRepairQuery) => {
  download('/mes/eq/repair/export', { ...query }, `repair_${new Date().getTime()}.xlsx`);
};
