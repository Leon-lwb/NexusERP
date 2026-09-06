import request from '@/utils/request';
import { AxiosPromise } from 'axios';

export interface DeadLetterQuery extends PageQuery {
  messageId?: string;
  routingKey?: string;
  status?: string;
}

export interface DeadLetterVO {
  id: string | number;
  messageId?: string;
  exchange?: string;
  routingKey?: string;
  messageBody?: string;
  messageType?: string;
  errorMessage?: string;
  retryCount?: number;
  status?: string;
  createTime?: string;
}

/** 分页查询死信记录 */
export const listDeadLetter = (query: DeadLetterQuery): AxiosPromise<DeadLetterVO[]> =>
  request({ url: '/monitor/dead-letter/list', method: 'get', params: query });

/** 死信记录详情(含消息体) */
export const getDeadLetter = (id: string | number): AxiosPromise<DeadLetterVO> =>
  request({ url: '/monitor/dead-letter/' + id, method: 'get' });

/** 人工重投死信消息 */
export const redeliverDeadLetter = (id: string | number) =>
  request({ url: '/monitor/dead-letter/redeliver/' + id, method: 'post' });

/** 删除死信记录 */
export const delDeadLetter = (ids: (string | number)[]) =>
  request({ url: '/monitor/dead-letter/' + ids.join(','), method: 'delete' });
