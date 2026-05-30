import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { TeamScheduleQuery, TeamScheduleVO, TeamScheduleForm } from './types';

export const listTeamSchedule = (query: TeamScheduleQuery): AxiosPromise<TeamScheduleVO[]> => {
  return request({
    url: '/erp/sch/teamSchedule/list',
    method: 'get',
    params: query
  });
};

export const getTeamSchedule = (teamScheduleId: string | number): AxiosPromise<TeamScheduleVO> => {
  return request({
    url: '/erp/sch/teamSchedule/' + teamScheduleId,
    method: 'get'
  });
};

export const addTeamSchedule = (data: TeamScheduleForm) => {
  return request({
    url: '/erp/sch/teamSchedule',
    method: 'post',
    data
  });
};

export const updateTeamSchedule = (data: TeamScheduleForm) => {
  return request({
    url: '/erp/sch/teamSchedule',
    method: 'put',
    data
  });
};

export const delTeamSchedule = (teamScheduleId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/sch/teamSchedule/' + teamScheduleId,
    method: 'delete'
  });
};
