import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { OpenItemVO } from './settlement/types';

/** 应收应付报表:未清项明细 */
export const listArapOpenItems = (direction: string, partnerId?: string | number): AxiosPromise<OpenItemVO[]> => {
  return request({ url: '/erp/fin/arap/report/openItems', method: 'get', params: { direction, partnerId } });
};
