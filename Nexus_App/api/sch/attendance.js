import request from '@/utils/request'

// 获取今日排班
export function getTodaySchedule() {
  return request({
    url: '/app/sch/employee-schedule/today',
    method: 'get'
  })
}

// 获取今日打卡状态
export function getTodayAttendanceStatus() {
  return request({
    url: '/app/sch/attendance/today-status',
    method: 'get'
  })
}

// 上班打卡（带位置信息）
export function clockIn(data) {
  return request({
    url: '/app/sch/attendance/clock-in',
    method: 'post',
    data: data
  })
}

// 下班打卡（带位置信息）
export function clockOut(data) {
  return request({
    url: '/app/sch/attendance/clock-out',
    method: 'post',
    data: data
  })
}

// 获取月度考勤统计
export function getMonthlyAttendance(month) {
  return request({
    url: `/app/sch/attendance/monthly/${month}`,
    method: 'get'
  })
}

// 获取我的排班列表（支持分页）
export function getMySchedule(query = {}) {
  return request({
    url: '/app/sch/employee-schedule/my-schedule',
    method: 'get',
    params: query
  })
}

// 验证位置是否在打卡范围内
export function validateLocation(data) {
  return request({
    url: '/app/sch/attendance/validate-location',
    method: 'post',
    data: data
  })
}

// 获取打卡规则
export function getPunchRules() {
  return request({
    url: '/app/sch/punch-rule/list',
    method: 'get'
  })
}

// 获取最近的打卡记录
export function getRecentPunchRecords(limit = 10) {
  return request({
    url: '/app/sch/attendance/recent',
    method: 'get',
    params: { limit }
  })
}

// 提交补卡申请
export function applySupplement(data) {
  return request({
    url: '/app/sch/supplement-apply',
    method: 'post',
    data: data
  })
}

// 获取补卡申请列表
export function getSupplementApplies(query) {
  return request({
    url: '/app/sch/supplement-apply/list',
    method: 'get',
    params: query
  })
}

// 获取外勤打卡记录
export function getOutdoorPunches(query) {
  return request({
    url: '/app/sch/outdoor-punch/list',
    method: 'get',
    params: query
  })
}

// 获取我的考勤记录（简化版，用于移动端）
export function getMyAttendance(query) {
  return request({
    url: '/app/sch/attendance/my',
    method: 'get',
    params: query
  })
}