import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { SchedulePlanVO, SchedulePlanQuery, ScheduleRunForm, ScheduleGanttVO, ScheduleTaskVO } from './types';

/**
 * 分页查询排程方案
 */
export function listSchedule(query: SchedulePlanQuery): AxiosPromise<SchedulePlanVO[]> {
  return request({ url: '/erp/prd/schedule/list', method: 'get', params: query });
}

/**
 * 查询方案下全部工序任务
 */
export function listScheduleTasks(planId: string | number): AxiosPromise<ScheduleTaskVO[]> {
  return request({ url: `/erp/prd/schedule/task/${planId}`, method: 'get' });
}

/**
 * 查询方案甘特图聚合数据
 */
export function getScheduleGantt(planId: string | number): AxiosPromise<ScheduleGanttVO> {
  return request({ url: `/erp/prd/schedule/gantt/${planId}`, method: 'get' });
}

/**
 * 执行排程
 */
export function runSchedule(data: ScheduleRunForm): AxiosPromise<SchedulePlanVO> {
  return request({ url: '/erp/prd/schedule/run', method: 'post', data });
}

/**
 * 发布排程方案(回写工单计划起止日期)
 */
export function publishSchedule(planId: string | number) {
  return request({ url: `/erp/prd/schedule/publish/${planId}`, method: 'put' });
}

/**
 * 批量删除排程方案
 */
export function delSchedule(planIds: string | number | (string | number)[]) {
  return request({ url: '/erp/prd/schedule/' + planIds, method: 'delete' });
}
