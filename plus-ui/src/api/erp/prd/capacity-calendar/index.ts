import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { CapacityCalendarVO, CapacityCalendarQuery, CapacityCalendarForm } from './types';

export function listCapacityCalendar(query: CapacityCalendarQuery): AxiosPromise<CapacityCalendarVO[]> {
  return request({ url: '/erp/prd/capacityCalendar/list', method: 'get', params: query });
}

export function getCapacityCalendar(calendarId: string | number): AxiosPromise<CapacityCalendarVO> {
  return request({ url: '/erp/prd/capacityCalendar/' + calendarId, method: 'get' });
}

export function addCapacityCalendar(data: CapacityCalendarForm) {
  return request({ url: '/erp/prd/capacityCalendar', method: 'post', data });
}

export function updateCapacityCalendar(data: CapacityCalendarForm) {
  return request({ url: '/erp/prd/capacityCalendar', method: 'put', data });
}

export function delCapacityCalendar(calendarIds: string | number | (string | number)[]) {
  return request({ url: '/erp/prd/capacityCalendar/' + calendarIds, method: 'delete' });
}

export function generateFromSchedule(planId: number, startDate: string, endDate: string, objectType: string, objectId: number, objectCode: string, objectName: string) {
  return request({ url: '/erp/prd/capacityCalendar/generate', method: 'post', params: { planId, startDate, endDate, objectType, objectId, objectCode, objectName } });
}

export function queryAvailableCapacity(objectType: string, objectId: number, startDate: string, endDate: string): AxiosPromise<CapacityCalendarVO[]> {
  return request({ url: '/erp/prd/capacityCalendar/available', method: 'get', params: { objectType, objectId, startDate, endDate } });
}

export function occupyCapacity(calendarId: number, hours: number) {
  return request({ url: '/erp/prd/capacityCalendar/occupy', method: 'put', params: { calendarId, hours } });
}

export function releaseCapacity(calendarId: number, hours: number) {
  return request({ url: '/erp/prd/capacityCalendar/release', method: 'put', params: { calendarId, hours } });
}
