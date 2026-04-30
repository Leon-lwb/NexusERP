import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { TeamQuery, TeamVO, TeamForm } from './types';

export const listTeam = (query: TeamQuery): AxiosPromise<TeamVO[]> => {
  return request({
    url: '/erp/sch/team/list',
    method: 'get',
    params: query
  });
};

export const getTeam = (teamId: string | number): AxiosPromise<TeamVO> => {
  return request({
    url: '/erp/sch/team/' + teamId,
    method: 'get'
  });
};

export const addTeam = (data: TeamForm) => {
  return request({
    url: '/erp/sch/team',
    method: 'post',
    data
  });
};

export const updateTeam = (data: TeamForm) => {
  return request({
    url: '/erp/sch/team',
    method: 'put',
    data
  });
};

export const delTeam = (teamId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/sch/team/' + teamId,
    method: 'delete'
  });
};
