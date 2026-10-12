import request from '@/utils/request';
import type { AxiosPromise } from 'axios';
import type { DetectionItem } from './detect';

export type DefectScenario = 'defect_metal' | 'defect_pcb';

export const defectScenarios: { value: DefectScenario; label: string; description: string }[] = [
  { value: 'defect_metal', label: '金属表面缺陷', description: '适用于金属表面异物、压痕、折痕等缺陷图片。' },
  { value: 'defect_pcb', label: 'PCB缺陷', description: '适用于 PCB 开路、短路、针孔等缺陷图片。' }
];

/** bbox 为原始图片像素坐标 [x1, y1, x2, y2]；耗时为本次模型推理总耗时。 */
export interface DefectDetectResult {
  success: boolean;
  scenario: DefectScenario;
  count: number;
  detections: DetectionItem[];
  processTimeMs: number;
}

/** 单张工业图片推理，服务端固定关闭 OCR，场景不得降级到通用检测。 */
export const detectDefect = (file: File, scenario: DefectScenario): AxiosPromise<DefectDetectResult> => {
  if (!defectScenarios.some((item) => item.value === scenario)) {
    throw new Error('请选择有效的工业缺陷检测场景');
  }
  const data = new FormData();
  data.append('file', file);
  data.append('scenario', scenario);
  return request({
    url: '/cv/detect/defect',
    method: 'post',
    data,
    // 推理不生成业务单据；不同 FormData 不能按 JSON 字符串去重，页面加载态负责防连点。
    headers: { ignoreErrorMsg: 'true', repeatSubmit: false }
  });
};
