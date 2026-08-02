import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** YOLO 检测项 */
export interface DetectionItem {
  className: string;
  confidence: number;
  bbox: number[]; // [x1, y1, x2, y2]
}

/** OCR 文本项 */
export interface OcrItem {
  text: string;
  confidence: number;
  bbox?: any;
}

/** 物料成品检测 + OCR 结果 */
export interface ProductDetectResult {
  success: boolean;
  count: number;
  detections: DetectionItem[];
  ocrResults: OcrItem[];
  processTimeMs: number;
  label?: LabelParseResult;
}

/** 标签规则解析结果 */
export interface LabelParseResult {
  matched: boolean;
  materialCode?: string;
  batchCode?: string;
  snCode?: string;
  rawTexts: string[];
  matchedMaterial?: {
    materialId: string;
    materialCode: string;
    materialName: string;
    materialSpec?: string;
  };
}

/** YOLO 通用检测结果 */
export interface MaterialDetectResult {
  success: boolean;
  count: number;
  detections: DetectionItem[];
  processTimeMs: number;
}

/** 操作台 1:N 候选 */
export interface OpsCandidate {
  subject: string;
  similarity: number;
  matched: boolean;
  workerId?: string | null;
  employeeId?: string;
  userName?: string;
  workshopName?: string;
  workStatus?: string;
}

/**
 * 物料成品检测 + OCR(主路径)
 * @param file 标签照片
 * @param enableOcr 是否启用 OCR(默认 true)
 * @param enableLabelParse 是否追加标签规则解析(料号/批次/SN + 主数据校验)
 */
export const detectProduct = (file: File, enableOcr = true, enableLabelParse = false): AxiosPromise<ProductDetectResult> => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('enableOcr', String(enableOcr));
  formData.append('enableLabelParse', String(enableLabelParse));
  return request({
    url: '/cv/detect/product',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  });
};

/** YOLO 通用检测(兜底) */
export const detectMaterial = (file: File): AxiosPromise<MaterialDetectResult> => {
  const formData = new FormData();
  formData.append('file', file);
  return request({
    url: '/cv/detect/material',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  });
};

/** 操作台 1:N 刷脸识别(top-3 候选,不签发身份) */
export const opsIdentify = (file: File, limit = 3): AxiosPromise<OpsCandidate[]> => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('limit', String(limit));
  return request({
    url: '/cv/ops/identify',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  });
};
