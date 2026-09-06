import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { DashboardStatsVO } from './types';

// 首页工作台统计(登录即可访问)
export function getDashboardStats(): AxiosPromise<DashboardStatsVO> {
  return request({
    url: '/erp/dashboard/stats',
    method: 'get'
  });
}
