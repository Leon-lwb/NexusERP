import request, { download } from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 巡检路线 */
export interface EqPatrolRouteVO {
  routeId: number;
  routeName: string;
  description?: string;
  equipmentIds?: string;
  checkItems?: string;
  frequency?: string;
  status?: string;
  remark?: string;
}
export interface EqPatrolRouteQuery extends PageQuery {
  routeName?: string;
  status?: string;
}
export interface EqPatrolRouteForm {
  routeId?: number;
  routeName: string;
  description?: string;
  equipmentIds?: string;
  checkItems?: string;
  frequency?: string;
  status?: string;
  remark?: string;
}

/** 巡检任务 */
export interface EqPatrolTaskVO {
  taskId: number;
  routeId: number;
  routeName?: string;
  planDate: string;
  actualDate?: string;
  assignedToName?: string;
  status: string;
  remark?: string;
}
export interface EqPatrolTaskQuery extends PageQuery {
  routeId?: number;
  status?: string;
}
export interface EqPatrolTaskForm {
  taskId?: number;
  routeId: number;
  planDate: string;
  assignedToId?: number;
  assignedToName?: string;
  status?: string;
  remark?: string;
}

/** 巡检记录 */
export interface EqPatrolRecordVO {
  recordId: number;
  taskId?: number;
  routeId?: number;
  equipmentId: number;
  equipmentCode?: string;
  equipmentName?: string;
  checkItem?: string;
  actualValue?: string;
  result: string;
  photoOssId?: string;
  executorName?: string;
  executeTime?: string;
  remark?: string;
}
export interface EqPatrolRecordQuery extends PageQuery {
  taskId?: number;
  equipmentId?: number;
  result?: string;
}
export interface EqPatrolRecordForm {
  taskId?: number;
  routeId?: number;
  equipmentId: number;
  checkItem?: string;
  actualValue?: string;
  result: string;
  photoOssId?: string;
  remark?: string;
}

// ===== 路线 =====
export const listPatrolRoute = (q?: EqPatrolRouteQuery): AxiosPromise<EqPatrolRouteVO[]> =>
  request({ url: '/mes/eq/patrol/route/list', method: 'get', params: q });
export const getPatrolRoute = (id: number | string): AxiosPromise<EqPatrolRouteVO> => request({ url: `/mes/eq/patrol/route/${id}`, method: 'get' });
export const addPatrolRoute = (data: EqPatrolRouteForm) => request({ url: '/mes/eq/patrol/route', method: 'post', data });
export const updatePatrolRoute = (data: EqPatrolRouteForm) => request({ url: '/mes/eq/patrol/route', method: 'put', data });
export const delPatrolRoute = (ids: number | number[]) => request({ url: `/mes/eq/patrol/route/${ids}`, method: 'delete' });

// ===== 任务 =====
export const listPatrolTask = (q?: EqPatrolTaskQuery): AxiosPromise<EqPatrolTaskVO[]> =>
  request({ url: '/mes/eq/patrol/task/list', method: 'get', params: q });
export const getPatrolTask = (id: number | string): AxiosPromise<EqPatrolTaskVO> => request({ url: `/mes/eq/patrol/task/${id}`, method: 'get' });
export const addPatrolTask = (data: EqPatrolTaskForm) => request({ url: '/mes/eq/patrol/task', method: 'post', data });
export const updatePatrolTask = (data: EqPatrolTaskForm) => request({ url: '/mes/eq/patrol/task', method: 'put', data });
export const delPatrolTask = (ids: number | number[]) => request({ url: `/mes/eq/patrol/task/${ids}`, method: 'delete' });
export const completePatrolTask = (taskId: number | string) => request({ url: `/mes/eq/patrol/task/complete/${taskId}`, method: 'put' });

// ===== 记录 =====
export const listPatrolRecord = (q?: EqPatrolRecordQuery): AxiosPromise<EqPatrolRecordVO[]> =>
  request({ url: '/mes/eq/patrol/record/list', method: 'get', params: q });
export const getPatrolRecord = (id: number | string): AxiosPromise<EqPatrolRecordVO> =>
  request({ url: `/mes/eq/patrol/record/${id}`, method: 'get' });
export const addPatrolRecord = (data: EqPatrolRecordForm) => request({ url: '/mes/eq/patrol/record', method: 'post', data });
export const batchSubmitPatrolRecord = (data: EqPatrolRecordForm[]) => request({ url: '/mes/eq/patrol/record/batch', method: 'post', data });
export const delPatrolRecord = (ids: number | number[]) => request({ url: `/mes/eq/patrol/record/${ids}`, method: 'delete' });
export const exportPatrolRecord = (q?: EqPatrolRecordQuery) =>
  download('/mes/eq/patrol/record/export', { ...q }, `patrol_record_${new Date().getTime()}.xlsx`);
