import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { WfCustomForm, WfCustomFormQuery, WfCustomFormVO, CustomFormVO } from '@/api/workflow/customForm/types';

// 查询流程业务单列表
export function listCustomForm(query: WfCustomFormQuery): AxiosPromise<WfCustomFormVO[]> {
  return request({
    url: '/workflow/customForm/list',
    method: 'get',
    params: query
  })
}

// 查询流程业务单详细
export function getCustomForm(id: string | number) {
  return request({
    url: '/workflow/customForm/' + id,
    method: 'get'
  })
}

// 新增流程业务单
export function addCustomForm(data: WfCustomForm) {
  return request({
    url: '/workflow/customForm',
    method: 'post',
    data: data
  })
}

// 修改流程业务单
export function updateCustomForm(data: WfCustomForm) {
  return request({
    url: '/workflow/customForm',
    method: 'put',
    data: data
  })
}

// 根据选择关联的流程定义，更新自定义流程表单列表
export function updateCustom(data: CustomFormVO) {
  return request({
    url: '/workflow/customForm/updateCustom',
    method: 'post',
    data: data
  })
}

// 删除流程业务单
export function delCustomForm(id: string | number) {
  return request({
    url: '/workflow/customForm/' + id,
    method: 'delete'
  })
}
