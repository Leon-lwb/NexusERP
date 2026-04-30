import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { HolidayQuery, HolidayVO, HolidayForm } from './types';

export const listHoliday = (query: HolidayQuery): AxiosPromise<HolidayVO[]> => {
  return request({
    url: '/erp/sch/holiday/list',
    method: 'get',
    params: query
  });
};

export const getHoliday = (holidayId: string | number): AxiosPromise<HolidayVO> => {
  return request({
    url: '/erp/sch/holiday/' + holidayId,
    method: 'get'
  });
};

export const addHoliday = (data: HolidayForm) => {
  return request({
    url: '/erp/sch/holiday',
    method: 'post',
    data
  });
};

export const updateHoliday = (data: HolidayForm) => {
  return request({
    url: '/erp/sch/holiday',
    method: 'put',
    data
  });
};

export const delHoliday = (holidayId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/sch/holiday/' + holidayId,
    method: 'delete'
  });
};
