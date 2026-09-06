import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { CacheVO } from './types';

// 查询缓存详细
export function getCache(): AxiosPromise<CacheVO> {
  return request({
    url: '/monitor/cache',
    method: 'get'
  });
}
