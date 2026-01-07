import request from '@/utils/request'

// 查询考勤记录列表
export function listAttendance(query) {
  return request({
    url: '/sch/attendance/list',
    method: 'get',
    params: query
  })
}

// 查询考勤记录详细
export function getAttendance(attendanceId) {
  return request({
    url: '/sch/attendance/' + attendanceId,
    method: 'get'
  })
}

// 新增考勤记录
export function addAttendance(data) {
  return request({
    url: '/sch/attendance',
    method: 'post',
    data: data
  })
}

// 修改考勤记录
export function updateAttendance(data) {
  return request({
    url: '/sch/attendance',
    method: 'put',
    data: data
  })
}

// 删除考勤记录
export function delAttendance(attendanceId) {
  return request({
    url: '/sch/attendance/' + attendanceId,
    method: 'delete'
  })
}
