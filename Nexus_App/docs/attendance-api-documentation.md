# 考勤打卡模块 - App端API接口文档

## 文档说明
- **API前缀**: `/app`
- **认证方式**: Bearer Token (JWT)
- **数据格式**: JSON
- **字符编码**: UTF-8
- **时区**: Asia/Shanghai (UTC+8)

## 接口概览

| 序号 | 接口名称 | 请求方法 | 接口路径 | 功能说明 |
|------|----------|----------|----------|----------|
| 1 | 获取今日排班 | GET | `/app/sch/employee-schedule/today` | 获取当前用户的今日排班信息 |
| 2 | 获取今日打卡状态 | GET | `/app/sch/attendance/today-status` | 获取今日上下班打卡状态 |
| 3 | 上班打卡 | POST | `/app/sch/attendance/clock-in` | 上班打卡（带位置信息） |
| 4 | 下班打卡 | POST | `/app/sch/attendance/clock-out` | 下班打卡（带位置信息） |
| 5 | 获取月度考勤统计 | GET | `/app/sch/attendance/monthly/{month}` | 获取指定月份的考勤统计 |
| 6 | 获取我的排班列表 | GET | `/app/sch/employee-schedule/my-schedule` | 获取当前用户的排班列表 |
| 7 | 验证位置 | POST | `/app/sch/attendance/validate-location` | 验证位置是否在打卡范围内 |
| 8 | 获取打卡规则 | GET | `/app/sch/punch-rule/list` | 获取打卡规则配置 |
| 9 | 获取最近打卡记录 | GET | `/app/sch/attendance/recent` | 获取最近的打卡记录 |
| 10 | 提交补卡申请 | POST | `/app/sch/supplement-apply` | 提交补卡申请 |
| 11 | 获取补卡申请列表 | GET | `/app/sch/supplement-apply/list` | 获取补卡申请列表 |
| 12 | 获取外勤打卡记录 | GET | `/app/sch/outdoor-punch/list` | 获取外勤打卡记录 |
| 13 | 获取我的考勤记录 | GET | `/app/sch/attendance/my` | 获取当前用户的考勤记录 |

---

## 接口详情

### 1. 获取今日排班
- **接口路径**: `/app/sch/employee-schedule/today`
- **请求方法**: GET
- **认证要求**: 需要Token
- **请求参数**: 无
- **响应示例**:
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "scheduleId": 1001,
    "planId": 1,
    "workerId": 10001,
    "workerName": "张三",
    "teamId": 10,
    "teamName": "研发一组",
    "shiftId": 1,
    "shiftName": "早班",
    "scheduledDate": "2026-01-07",
    "startTime": "2026-01-07 09:00:00",
    "endTime": "2026-01-07 18:00:00",
    "scheduleStatus": "normal",
    "isWorkingDay": true,
    "remark": null
  }
}
```

### 2. 获取今日打卡状态
- **接口路径**: `/app/sch/attendance/today-status`
- **请求方法**: GET
- **认证要求**: 需要Token
- **请求参数**: 无
- **响应示例**:
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "date": "2026-01-07",
    "clockInTime": "2026-01-07 08:58:32",
    "clockOutTime": null,
    "clockInStatus": "success",
    "clockOutStatus": "pending",
    "attendanceStatus": "normal",
    "lateMinutes": 0,
    "leaveEarlyMinutes": 0,
    "overtimeHours": 0.0
  }
}
```

### 3. 上班打卡
- **接口路径**: `/app/sch/attendance/clock-in`
- **请求方法**: POST
- **认证要求**: 需要Token
- **请求参数**:
```json
{
  "latitude": 31.230416,
  "longitude": 121.473701,
  "accuracy": 15.5,
  "address": "上海市黄浦区人民广场",
  "deviceId": "device-001",
  "deviceName": "iPhone 15 Pro",
  "networkType": "wifi",
  "photoUrl": "https://example.com/photo1.jpg",
  "remark": "正常打卡"
}
```
- **响应示例**:
```json
{
  "code": 200,
  "msg": "打卡成功",
  "data": {
    "attendanceId": 2001,
    "clockInTime": "2026-01-07 08:58:32",
    "locationValid": true,
    "distance": 45,
    "isLate": false,
    "lateMinutes": 0,
    "nextClockOutTime": "2026-01-07 18:00:00"
  }
}
```

### 4. 下班打卡
- **接口路径**: `/app/sch/attendance/clock-out`
- **请求方法**: POST
- **认证要求**: 需要Token
- **请求参数**:
```json
{
  "latitude": 31.230416,
  "longitude": 121.473701,
  "accuracy": 12.3,
  "address": "上海市黄浦区人民广场",
  "deviceId": "device-001",
  "deviceName": "iPhone 15 Pro",
  "networkType": "wifi",
  "photoUrl": "https://example.com/photo2.jpg",
  "remark": "正常下班",
  "overtimeHours": 1.5
}
```
- **响应示例**:
```json
{
  "code": 200,
  "msg": "打卡成功",
  "data": {
    "attendanceId": 2001,
    "clockOutTime": "2026-01-07 18:30:15",
    "locationValid": true,
    "distance": 52,
    "isEarlyLeave": false,
    "leaveEarlyMinutes": 0,
    "overtimeHours": 1.5,
    "attendanceStatus": "normal"
  }
}
```

### 5. 获取月度考勤统计
- **接口路径**: `/app/sch/attendance/monthly/{month}`
- **请求方法**: GET
- **认证要求**: 需要Token
- **路径参数**:
  - `month`: 月份，格式：yyyy-MM，如：2026-01
- **响应示例**:
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "month": "2026-01",
    "totalDays": 22,
    "workDays": 20,
    "attendanceDays": 18,
    "leaveDays": 2,
    "lateCount": 1,
    "earlyLeaveCount": 0,
    "absentCount": 0,
    "overtimeHours": 12.5,
    "attendanceRate": "90.0%",
    "details": [
      {
        "date": "2026-01-07",
        "weekday": "星期三",
        "attendanceStatus": "normal",
        "clockInTime": "08:58",
        "clockOutTime": "18:30",
        "isWorkDay": true
      }
    ]
  }
}
```

### 6. 获取我的排班列表
- **接口路径**: `/app/sch/employee-schedule/my-schedule`
- **请求方法**: GET
- **认证要求**: 需要Token
- **查询参数**:
  - `startDate`: 开始日期，格式：yyyy-MM-dd（可选）
  - `endDate`: 结束日期，格式：yyyy-MM-dd（可选）
  - `pageNum`: 页码（可选，默认1）
  - `pageSize`: 每页数量（可选，默认10）
- **响应示例**:
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "total": 30,
    "rows": [
      {
        "scheduleId": 1001,
        "shiftName": "早班",
        "scheduledDate": "2026-01-07",
        "startTime": "09:00:00",
        "endTime": "18:00:00",
        "scheduleStatus": "normal"
      }
    ]
  }
}
```

### 7. 验证位置
- **接口路径**: `/app/sch/attendance/validate-location`
- **请求方法**: POST
- **认证要求**: 需要Token
- **请求参数**:
```json
{
  "latitude": 31.230416,
  "longitude": 121.473701,
  "accuracy": 15.5
}
```
- **响应示例**:
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "isValid": true,
    "distance": 45,
    "radius": 100,
    "message": "在打卡范围内（距离45米）",
    "punchRule": {
      "locationLat": 31.230416,
      "locationLng": 121.473701,
      "locationRadius": 100,
      "locationAddress": "上海市黄浦区人民广场"
    }
  }
}
```

### 8. 获取打卡规则
- **接口路径**: `/app/sch/punch-rule/list`
- **请求方法**: GET
- **认证要求**: 需要Token
- **响应示例**:
```json
{
  "code": 200,
  "msg": "success",
  "data": [
    {
      "ruleId": 1,
      "ruleName": "默认打卡规则",
      "workStartTime": "09:00:00",
      "workEndTime": "18:00:00",
      "allowClockInStart": "08:00:00",
      "allowClockInEnd": "10:00:00",
      "allowClockOutStart": "17:00:00",
      "allowClockOutEnd": "20:00:00",
      "locationLat": 31.230416,
      "locationLng": 121.473701,
      "locationRadius": 100,
      "locationAddress": "上海市黄浦区人民广场",
      "allowOutdoorPunch": true,
      "outdoorRequirePhoto": true,
      "allowSupplement": true,
      "supplementDeadline": 3,
      "lateThreshold": 5,
      "earlyLeaveThreshold": 5,
      "absentThreshold": 60,
      "isEnabled": true
    }
  ]
}
```

### 9. 获取最近打卡记录
- **接口路径**: `/app/sch/attendance/recent`
- **请求方法**: GET
- **认证要求**: 需要Token
- **查询参数**:
  - `limit`: 记录数量（可选，默认10）
- **响应示例**:
```json
{
  "code": 200,
  "msg": "success",
  "data": [
    {
      "attendanceId": 2001,
      "recordDate": "2026-01-07",
      "clockInTime": "08:58:32",
      "clockOutTime": "18:30:15",
      "attendanceStatus": "normal",
      "lateMinutes": 0,
      "leaveEarlyMinutes": 0,
      "overtimeHours": 1.5
    }
  ]
}
```

### 10. 提交补卡申请
- **接口路径**: `/app/sch/supplement-apply`
- **请求方法**: POST
- **认证要求**: 需要Token
- **请求参数**:
```json
{
  "applyDate": "2026-01-06",
  "applyType": "clock_in", // clock_in, clock_out, both
  "reason": "忘记打卡",
  "proofPhotos": ["https://example.com/proof1.jpg"],
  "remark": "申请补上班打卡"
}
```
- **响应示例**:
```json
{
  "code": 200,
  "msg": "补卡申请提交成功",
  "data": {
    "applyId": 3001,
    "applyNo": "BC20260107001",
    "applyDate": "2026-01-06",
    "applyType": "clock_in",
    "applyStatus": "pending",
    "createTime": "2026-01-07 10:30:00"
  }
}
```

### 11. 获取补卡申请列表
- **接口路径**: `/app/sch/supplement-apply/list`
- **请求方法**: GET
- **认证要求**: 需要Token
- **查询参数**:
  - `startDate`: 开始日期（可选）
  - `endDate`: 结束日期（可选）
  - `applyStatus`: 申请状态（可选）
  - `pageNum`: 页码（可选）
  - `pageSize`: 每页数量（可选）
- **响应示例**:
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "total": 5,
    "rows": [
      {
        "applyId": 3001,
        "applyNo": "BC20260107001",
        "applyDate": "2026-01-06",
        "applyType": "clock_in",
        "applyStatus": "pending",
        "reason": "忘记打卡",
        "createTime": "2026-01-07 10:30:00",
        "reviewTime": null,
        "reviewRemark": null
      }
    ]
  }
}
```

### 12. 获取外勤打卡记录
- **接口路径**: `/app/sch/outdoor-punch/list`
- **请求方法**: GET
- **认证要求**: 需要Token
- **查询参数**:
  - `startDate`: 开始日期（可选）
  - `endDate`: 结束日期（可选）
  - `pageNum`: 页码（可选）
  - `pageSize`: 每页数量（可选）
- **响应示例**:
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "total": 3,
    "rows": [
      {
        "outdoorId": 4001,
        "punchDate": "2026-01-06",
        "punchType": "clock_in",
        "latitude": 31.235416,
        "longitude": 121.478701,
        "address": "上海市浦东新区客户现场",
        "photoUrl": "https://example.com/outdoor1.jpg",
        "remark": "客户拜访",
        "createTime": "2026-01-06 09:15:00"
      }
    ]
  }
}
```

### 13. 获取我的考勤记录
- **接口路径**: `/app/sch/attendance/my`
- **请求方法**: GET
- **认证要求**: 需要Token
- **查询参数**:
  - `startDate`: 开始日期，格式：yyyy-MM-dd（可选）
  - `endDate`: 结束日期，格式：yyyy-MM-dd（可选）
  - `attendanceStatus`: 考勤状态（可选）
  - `pageNum`: 页码（可选，默认1）
  - `pageSize`: 每页数量（可选，默认20）
- **响应示例**:
```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "total": 45,
    "rows": [
      {
        "attendanceId": 2001,
        "recordDate": "2026-01-07",
        "weekday": "星期三",
        "clockInTime": "08:58:32",
        "clockOutTime": "18:30:15",
        "attendanceStatus": "normal",
        "lateMinutes": 0,
        "leaveEarlyMinutes": 0,
        "overtimeHours": 1.5,
        "locationValid": true
      }
    ]
  }
}
```

---

## 错误码说明

| 错误码 | 说明 | 处理建议 |
|--------|------|----------|
| 200 | 成功 | 正常处理 |
| 400 | 请求参数错误 | 检查请求参数格式 |
| 401 | 未授权/Token过期 | 重新登录获取Token |
| 403 | 权限不足 | 检查用户权限 |
| 404 | 接口不存在 | 检查接口路径 |
| 500 | 服务器内部错误 | 联系管理员 |
| 1001 | 位置验证失败 | 检查是否在打卡范围内 |
| 1002 | 不在打卡时间内 | 检查当前时间是否允许打卡 |
| 1003 | 重复打卡 | 今日已打卡，无需重复操作 |
| 1004 | 补卡申请已存在 | 该日期已提交过补卡申请 |
| 1005 | 超过补卡期限 | 补卡申请已超过允许期限 |

---

## 数据库表参考

### 1. 员工排班表 (sch_employee_schedule)
```sql
CREATE TABLE `sch_employee_schedule` (
  `schedule_id` bigint NOT NULL AUTO_INCREMENT COMMENT '员工排班ID',
  `plan_id` bigint NOT NULL COMMENT '排班计划ID',
  `worker_id` bigint NOT NULL COMMENT '人员ID',
  `worker_name` varchar(255) DEFAULT NULL COMMENT '人员姓名',
  `team_id` bigint DEFAULT NULL COMMENT '班组ID',
  `team_name` varchar(255) DEFAULT NULL COMMENT '班组名称',
  `shift_id` bigint NOT NULL COMMENT '班次ID',
  `shift_name` varchar(255) DEFAULT NULL COMMENT '班次名称',
  `scheduled_date` date NOT NULL COMMENT '排班日期',
  `start_time` datetime DEFAULT NULL COMMENT '排班开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '排班结束时间',
  `actual_shift_id` bigint DEFAULT NULL COMMENT '实际班次ID（用于调班覆盖）',
  `schedule_status` varchar(64) DEFAULT 'normal' COMMENT '排班状态',
  `is_working_day` tinyint(1) DEFAULT '1' COMMENT '是否工作日',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`schedule_id`),
  UNIQUE KEY `uk_worker_date` (`worker_id`,`scheduled_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工排班明细表';
```

### 2. 考勤记录表 (sch_attendance)
```sql
CREATE TABLE `sch_attendance` (
  `attendance_id` bigint NOT NULL AUTO_INCREMENT COMMENT '考勤记录ID',
  `worker_id` bigint NOT NULL COMMENT '人员ID',
  `worker_name` varchar(255) DEFAULT NULL COMMENT '人员姓名',
  `record_date` date NOT NULL COMMENT '考勤日期',
  `schedule_id` bigint DEFAULT NULL COMMENT '关联排班ID',
  `clock_in_time` datetime DEFAULT NULL COMMENT '上班打卡时间',
  `clock_out_time` datetime DEFAULT NULL COMMENT '下班打卡时间',
  `late_minutes` int DEFAULT '0' COMMENT '迟到分钟数',
  `leave_early_minutes` int DEFAULT '0' COMMENT '早退分钟数',
  `overtime_hours` decimal(5,2) DEFAULT '0.00' COMMENT '加班时长',
  `attendance_status` varchar(64) DEFAULT NULL COMMENT '考勤结果',
  `source_type` varchar(64) DEFAULT 'manual' COMMENT '数据来源',
  `device_id` varchar(128) DEFAULT NULL COMMENT '打卡设备ID',
  `device_name` varchar(255) DEFAULT NULL COMMENT '设备名称',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`attendance_id`),
  UNIQUE KEY `uk_worker_date` (`worker_id`,`record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤记录表';
```

---

## 前端调用示例

```javascript
// 导入API
import { 
  getTodaySchedule, 
  clockIn, 
  clockOut,
  validateLocation,
  getMonthlyAttendance
} from '@/api/sch/attendance'

// 获取今日排班
async function loadTodaySchedule() {
  try {
    const res = await getTodaySchedule()
    console.log('今日排班:', res.data)
  } catch (error) {
    console.error('获取排班失败:', error)
  }
}

// 上班打卡
async function handleClockIn() {
  const location = {
    latitude: 31.230416,
    longitude: 121.473701,
    accuracy: 15.5,
    address: '上海市黄浦区人民广场'
  }
  
  try {
    const res = await clockIn(location)
    console.log('打卡成功:', res.data)
  } catch (error) {
    console.error('打卡失败:', error)
  }
}
```

---

**文档版本**: v1.0.0  
**最后更新**: 2026-01-07  
**维护人员**: 考勤模块开发团队