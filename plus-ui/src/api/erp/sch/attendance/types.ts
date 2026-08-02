export interface AttendanceQuery extends PageQuery {
  workerId?: number;
  workerName?: string;
  recordDate?: string;
  attendanceStatus?: string;
  /** 人脸验证状态过滤(FAIL 复查页用) */
  faceVerifyStatus?: string;
}

export interface AttendanceVO {
  attendanceId: number;
  workerId: number;
  workerName: string;
  recordDate: string;
  scheduleId: number;
  clockInTime: string;
  clockOutTime: string;
  lateMinutes: number;
  leaveEarlyMinutes: number;
  overtimeHours: number;
  attendanceStatus: string;
  sourceType: string;
  deviceId: string;
  deviceName: string;
  /** 人脸验证状态: NONE未验证, PASS通过, FAIL失败 */
  faceVerifyStatus: string;
  /** 人脸验证时间 */
  faceVerifyTime: string;
  /** 人脸相似度 */
  faceSimilarity: number;
  /** 打卡人脸照片ossId(MinIO存证) */
  faceImageOssId: number;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface AttendanceForm {
  attendanceId?: number;
  workerId?: number;
  workerName?: string;
  recordDate?: string;
  scheduleId?: number;
  clockInTime?: string;
  clockOutTime?: string;
  lateMinutes?: number;
  leaveEarlyMinutes?: number;
  overtimeHours?: number;
  attendanceStatus?: string;
  sourceType?: string;
  deviceId?: string;
  deviceName?: string;
  remark?: string;
}
