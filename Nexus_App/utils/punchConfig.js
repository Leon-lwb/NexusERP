/**
 * 打卡配置
 * 用于存储默认的打卡规则和位置信息
 */

// 默认打卡规则（开发阶段使用）
export const DEFAULT_PUNCH_RULE = {
  rule_id: 1,
  rule_name: '默认打卡规则',
  work_start_time: '09:00:00',
  work_end_time: '18:00:00',
  allow_clock_in_start: '08:00:00',
  allow_clock_in_end: '10:00:00',
  allow_clock_out_start: '17:00:00',
  allow_clock_out_end: '20:00:00',
  location_lat: 31.230416, // 上海中心坐标
  location_lng: 121.473701,
  location_radius: 500, // 500米半径
  location_address: '上海市黄浦区',
  allow_outdoor_punch: true,
  outdoor_require_photo: true,
  allow_supplement: true,
  supplement_deadline: 3,
  late_threshold: 5, // 5分钟
  early_leave_threshold: 5, // 5分钟
  absent_threshold: 60, // 60分钟
  is_enabled: true
};

// 班次配置
export const SHIFT_CONFIG = {
  1: {
    shift_id: 1,
    shift_name: '早班',
    work_start_time: '08:00:00',
    work_end_time: '17:00:00'
  },
  2: {
    shift_id: 2,
    shift_name: '中班',
    work_start_time: '12:00:00',
    work_end_time: '21:00:00'
  },
  3: {
    shift_id: 3,
    shift_name: '晚班',
    work_start_time: '16:00:00',
    work_end_time: '01:00:00'
  },
  4: {
    shift_id: 4,
    shift_name: '弹性班',
    work_start_time: '09:00:00',
    work_end_time: '18:00:00',
    allow_clock_in_start: '08:00:00',
    allow_clock_in_end: '11:00:00',
    allow_clock_out_start: '17:00:00',
    allow_clock_out_end: '21:00:00'
  }
};

// 打卡类型
export const PUNCH_TYPES = {
  NORMAL: 'normal', // 正常打卡
  OUTDOOR: 'outdoor', // 外勤打卡
  SUPPLEMENT: 'supplement', // 补卡
  REMOTE: 'remote' // 远程打卡
};

// 打卡状态
export const PUNCH_STATUS = {
  SUCCESS: 'success', // 成功
  FAILED: 'failed', // 失败
  PENDING: 'pending' // 待审核
};

// 考勤状态
export const ATTENDANCE_STATUS = {
  NORMAL: 'normal', // 正常
  LATE: 'late', // 迟到
  EARLY_LEAVE: 'early_leave', // 早退
  ABSENT: 'absent', // 旷工
  LEAVE: 'leave', // 请假
  OVERTIME: 'overtime' // 加班
};

// 补卡申请类型
export const SUPPLEMENT_TYPES = {
  CLOCK_IN: 'clock_in', // 上班补卡
  CLOCK_OUT: 'clock_out', // 下班补卡
  BOTH: 'both' // 上下班补卡
};

// 补卡申请状态
export const SUPPLEMENT_STATUS = {
  PENDING: 'pending', // 待审核
  APPROVED: 'approved', // 通过
  REJECTED: 'rejected' // 驳回
};

// 外勤打卡配置
export const OUTDOOR_CONFIG = {
  MIN_DISTANCE: 1000, // 最小外勤距离（米）
  REQUIRE_PHOTO: true, // 是否要求拍照
  MAX_PHOTO_SIZE: 5 * 1024 * 1024, // 最大照片大小（5MB）
  ALLOWED_FILE_TYPES: ['image/jpeg', 'image/png', 'image/jpg']
};

// 位置验证配置
export const LOCATION_CONFIG = {
  DEFAULT_RADIUS: 100, // 默认打卡半径（米）
  MAX_ACCURACY: 100, // 最大允许定位精度（米）
  UPDATE_INTERVAL: 30000, // 位置更新间隔（毫秒）
  RETRY_COUNT: 3 // 重试次数
};

// 时间配置
export const TIME_CONFIG = {
  WORKDAY_START: '09:00', // 工作日开始时间
  WORKDAY_END: '18:00', // 工作日结束时间
  LUNCH_START: '12:00', // 午餐开始时间
  LUNCH_END: '13:00', // 午餐结束时间
  WEEKEND_DAYS: [0, 6] // 周末（0=周日，6=周六）
};

// 获取当前班次配置
export function getCurrentShiftConfig(shiftId) {
  return SHIFT_CONFIG[shiftId] || DEFAULT_PUNCH_RULE;
}

// 获取默认打卡规则
export function getDefaultPunchRule() {
  return DEFAULT_PUNCH_RULE;
}

// 检查是否为工作日
export function isWorkday(date = new Date()) {
  const day = date.getDay();
  return !TIME_CONFIG.WEEKEND_DAYS.includes(day);
}

// 检查是否为工作时间
export function isWorkTime(date = new Date()) {
  const now = date;
  const hours = now.getHours();
  const minutes = now.getMinutes();
  const currentMinutes = hours * 60 + minutes;
  
  const workStartMinutes = timeToMinutes(TIME_CONFIG.WORKDAY_START);
  const workEndMinutes = timeToMinutes(TIME_CONFIG.WORKDAY_END);
  
  return currentMinutes >= workStartMinutes && currentMinutes <= workEndMinutes;
}

// 将时间字符串转换为分钟数
function timeToMinutes(timeStr) {
  if (!timeStr) return 0;
  const [hours, minutes] = timeStr.split(':').map(Number);
  return hours * 60 + minutes;
}

export default {
  DEFAULT_PUNCH_RULE,
  SHIFT_CONFIG,
  PUNCH_TYPES,
  PUNCH_STATUS,
  ATTENDANCE_STATUS,
  SUPPLEMENT_TYPES,
  SUPPLEMENT_STATUS,
  OUTDOOR_CONFIG,
  LOCATION_CONFIG,
  TIME_CONFIG,
  getCurrentShiftConfig,
  getDefaultPunchRule,
  isWorkday,
  isWorkTime
};