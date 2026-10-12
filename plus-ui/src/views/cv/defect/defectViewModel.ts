import type { CSSProperties } from 'vue';
import type { DetectionItem } from '@/api/cv/detect';
import type { DefectDetectResult, DefectScenario } from '@/api/cv/defect';

export interface ImageSize {
  width: number;
  height: number;
}

const classLabels: Record<DefectScenario, Record<string, string>> = {
  defect_metal: {
    crazing: '裂纹',
    inclusion: '夹杂',
    patches: '斑块',
    pitted_surface: '麻点',
    rolled_in_scale: '轧入氧化皮',
    scratches: '划痕'
  },
  defect_pcb: {
    missing_hole: '缺孔',
    mouse_bite: '鼠咬缺口',
    open_circuit: '断路',
    short: '短路',
    spur: '毛刺',
    spurious_copper: '多余铜'
  }
};

export function defectClassLabel(className: string, scenario: DefectScenario): string {
  const key = className.toLowerCase().replace(/[ -]+/g, '_');
  return classLabels[scenario]?.[key] || className;
}

function validBox(bbox: number[]): boolean {
  return Array.isArray(bbox) && bbox.length === 4 && bbox.every(Number.isFinite) && bbox[2] > bbox[0] && bbox[3] > bbox[1];
}

/** 图片层没有留白或 object-fit；百分比跟随原图等比缩放，不修改模型坐标。 */
export function defectBoxStyle(item: DetectionItem, natural: ImageSize): CSSProperties {
  const { width, height } = natural;
  if (!Number.isFinite(width) || !Number.isFinite(height) || width <= 0 || height <= 0 || !validBox(item.bbox)) {
    return { display: 'none' };
  }
  const [x1, y1, x2, y2] = item.bbox;
  const left = Math.max(0, Math.min(width, x1));
  const top = Math.max(0, Math.min(height, y1));
  const right = Math.max(0, Math.min(width, x2));
  const bottom = Math.max(0, Math.min(height, y2));
  if (right <= left || bottom <= top) return { display: 'none' };
  return {
    left: `${(left / width) * 100}%`,
    top: `${(top / height) * 100}%`,
    width: `${((right - left) / width) * 100}%`,
    height: `${((bottom - top) / height) * 100}%`
  };
}

export interface DefectSummaryRow {
  className: string;
  classLabel: string;
  confidence: number;
  count: number;
  processTimeMs: number;
}

/** 数量来自真实检测项；置信度展示同类最高值；耗时为同一次完整推理。 */
export function summarizeDefects(result?: DefectDetectResult): DefectSummaryRow[] {
  if (!result) return [];
  const groups = new Map<string, DefectSummaryRow>();
  for (const item of result.detections) {
    const previous = groups.get(item.className);
    if (previous) {
      previous.count++;
      previous.confidence = Math.max(previous.confidence, item.confidence);
    } else {
      groups.set(item.className, {
        className: item.className,
        classLabel: defectClassLabel(item.className, result.scenario),
        confidence: item.confidence,
        count: 1,
        processTimeMs: result.processTimeMs
      });
    }
  }
  return [...groups.values()];
}

/** 失败、错配或不完整响应不能作为「未检出缺陷」展示。 */
export function validateDefectResult(result: DefectDetectResult, scenario: DefectScenario): void {
  if (!result || result.success !== true) throw new Error('检测未成功，请检查所选场景模型是否可用');
  if (result.scenario !== scenario) throw new Error('检测结果场景与所选场景不一致，请重新检测');
  if (
    !Array.isArray(result.detections) ||
    !Number.isInteger(result.count) ||
    result.count !== result.detections.length ||
    !Number.isFinite(result.processTimeMs) ||
    result.processTimeMs < 0 ||
    result.detections.some(
      (item) =>
        !item ||
        typeof item.className !== 'string' ||
        !item.className.trim() ||
        !Number.isFinite(item.confidence) ||
        item.confidence < 0 ||
        item.confidence > 1 ||
        !validBox(item.bbox)
    )
  ) {
    throw new Error('检测结果格式异常，请检查视觉服务返回的类别、坐标、数量与耗时');
  }
}

export function detectionErrorMessage(error: unknown): string {
  const message = error instanceof Error ? error.message : typeof error === 'string' ? error : '';
  if (/network|fetch|连接/i.test(message)) return '网络连接失败，请检查连接后重试';
  if (/timeout|timed out|超时/i.test(message)) return '检测请求超时，请稍后重试或检查所选场景模型';
  return message || '工业缺陷检测失败，请稍后重试';
}
