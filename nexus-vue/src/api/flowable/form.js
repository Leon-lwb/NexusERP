import { getAction, postAction, putAction, deleteAction } from '@/utils/request';

// 查询表单列表
export function listForm(query) {
  return getAction('/form/template/list', query)
}

// 查询所有表单列表（用于流程设计中选择表单）
export function listAllForm(query) {
  return getAction('/form/template/list', query).then(res => res.data)
}

// 查询表单详细
export function getForm(formId) {
  return getAction('/form/template/' + formId).then(res => res.data)
}

// 新增表单
export function addForm(data) {
  return postAction('/flowable/form', data).then(res => res.data)
}

// 修改表单
export function updateForm(data) {
  return putAction('/flowable/form', data).then(res => res.data)
}

// 挂载表单
export function addDeployForm(data) {
  return postAction('/flowable/definition/addDeployForm', data).then(res => res.data)
}

// 删除表单
export function delForm(formId) {
  return deleteAction('/form/template/' + formId).then(res => res.data)
}

// 导出流程表单
export function exportForm(query) {
  return getAction('/form/template/export', query).then(res => res.data)
}