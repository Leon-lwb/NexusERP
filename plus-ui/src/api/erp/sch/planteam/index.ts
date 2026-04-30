import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { PlanteamQuery, PlanteamVO, PlanteamForm } from './types';

export const listPlanteam = (query: PlanteamQuery): AxiosPromise<PlanteamVO[]> => {
  return request({
    url: '/erp/sch/planteam/list',
    method: 'get',
    params: query
  });
};

export const getPlanteam = (recordId: string | number): AxiosPromise<PlanteamVO> => {
  return request({
    url: '/erp/sch/planteam/' + recordId,
    method: 'get'
  });
};

export const addPlanteam = (data: PlanteamForm) => {
  return request({
    url: '/erp/sch/planteam',
    method: 'post',
    data
  });
};

export const updatePlanteam = (data: PlanteamForm) => {
  return request({
    url: '/erp/sch/planteam',
    method: 'put',
    data
  });
};

export const delPlanteam = (recordId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/sch/planteam/' + recordId,
    method: 'delete'
  });
};
