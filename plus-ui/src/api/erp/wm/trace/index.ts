import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { WmTraceResultVO, WmTraceQuery } from './types';

/** 正向追溯（原材料 → 成品） */
export function traceForward(query: WmTraceQuery): AxiosPromise<WmTraceResultVO> {
  return request({
    url: '/erp/wm/trace/forward',
    method: 'get',
    params: query
  });
}

/** 反向追溯（成品 → 原材料） */
export function traceBackward(query: WmTraceQuery): AxiosPromise<WmTraceResultVO> {
  return request({
    url: '/erp/wm/trace/backward',
    method: 'get',
    params: query
  });
}
