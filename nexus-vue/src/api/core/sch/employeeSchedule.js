import request from '@/utils/request'

// 查询员工排班明细列表
export function listEmployeeSchedule(query) {
  return request({
    url: '/sch/employeeSchedule/list',
    method: 'get',
    params: query
  })
}

// 查询员工排班明细详细
export function getEmployeeSchedule(scheduleId) {
  return request({
    url: '/sch/employeeSchedule/' + scheduleId,
    method: 'get'
  })
}

// 新增员工排班明细
export function addEmployeeSchedule(data) {
  return request({
    url: '/sch/employeeSchedule',
    method: 'post',
    data: data
  })
}

// 修改员工排班明细
export function updateEmployeeSchedule(data) {
  return request({
    url: '/sch/employeeSchedule',
    method: 'put',
    data: data
  })
}

// 删除员工排班明细
export function delEmployeeSchedule(scheduleId) {
  return request({
    url: '/sch/employeeSchedule/' + scheduleId,
    method: 'delete'
  })
}
