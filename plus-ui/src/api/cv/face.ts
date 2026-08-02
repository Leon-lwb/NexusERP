import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 人脸 1:1 验证结果 */
export interface FaceVerifyResult {
  success: boolean;
  subject: string;
  similarity: number;
  matched: boolean;
  reason: string;
  processTimeMs: number;
}

/** 人脸 1:N 匹配项 */
export interface FaceMatchItem {
  subject: string;
  similarity: number;
  matched: boolean;
  box?: {
    x: number;
    y: number;
    width: number;
    height: number;
  };
}

/** 人脸 1:N 识别结果 */
export interface FaceRecognizeResult {
  success: boolean;
  result: FaceMatchItem[];
  faceCount: number;
  processTimeMs: number;
}

/** 注册结果 */
export interface FaceRegisterResult {
  success: boolean;
  subject: string;
  faceCount: number;
  processTimeMs: number;
}

/** 底库条目 */
export interface FaceSubjectItem {
  subject: string;
  displayName: string;
  faceCount: number;
  remark: string;
  photo?: string;
  createTime: string;
  updateTime: string;
}

/** 底库分页列表 */
export interface FaceSubjectList {
  success: boolean;
  total: number;
  items: FaceSubjectItem[];
}

/** 视觉服务探活 */
export interface CvHealthResult {
  available: boolean;
  status?: string;
  models?: string[];
  error?: string;
}

// ===== 底库管理 =====

/** 注册人脸(subject = EMP-工号) */
export const registerFace = (file: File, subject: string, displayName?: string, remark?: string): AxiosPromise<FaceRegisterResult> => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('subject', subject);
  if (displayName) formData.append('displayName', displayName);
  if (remark) formData.append('remark', remark);
  return request({
    url: '/cv/face/register',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  });
};

/** 1:1 人脸验证 */
export const verifyFace = (file: File, subject: string): AxiosPromise<FaceVerifyResult> => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('subject', subject);
  return request({
    url: '/cv/face/verify',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  });
};

/** 1:N 人脸识别 */
export const recognizeFace = (file: File, limit = 3): AxiosPromise<FaceRecognizeResult> => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('limit', String(limit));
  return request({
    url: '/cv/face/recognize',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  });
};

/** 底库分页列表 */
export const listFaces = (query: { page?: number; pageSize?: number }): AxiosPromise<FaceSubjectList> => {
  return request({
    url: '/cv/face/list',
    method: 'get',
    params: query
  });
};

/** 底库详情 */
export const getFace = (subject: string): AxiosPromise<FaceSubjectItem> => {
  return request({
    url: '/cv/face/' + subject,
    method: 'get'
  });
};

/** 删除底库条目 */
export const delFace = (subject: string): AxiosPromise<boolean> => {
  return request({
    url: '/cv/face/' + subject,
    method: 'delete'
  });
};

/** 按员工重新注册(先删旧再注册,avatar 变更后手动触发) */
export const reRegisterFace = (file: File, subject: string, displayName?: string, remark?: string): AxiosPromise<FaceRegisterResult> => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('subject', subject);
  if (displayName) formData.append('displayName', displayName);
  if (remark) formData.append('remark', remark);
  return request({
    url: '/cv/face/re-register',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  });
};

/** 批量注册(测试期:从 SysUser.avatar 按 EMP-工号注册) */
export const batchRegisterFaces = (limit = 100): AxiosPromise<FaceRegisterResult[]> => {
  return request({
    url: '/cv/face/batch-register',
    method: 'post',
    params: { limit }
  });
};

/** 视觉服务探活 */
export const cvHealth = (): AxiosPromise<CvHealthResult> => {
  return request({
    url: '/cv/face/health',
    method: 'get'
  });
};
