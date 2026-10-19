import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 标签打印模板视图对象 */
export interface MesLabelTemplateVO {
  templateId: number | string;
  templateCode: string;
  templateName: string;
  /** 服务端模板修订号，保存时携带用于检测并发修改；旧调用可不传。 */
  templateRevision?: number | string;
  /** 标签类型: SN */
  labelType?: string;
  /** 打印机语言: TSPL / ZPL */
  labelLang: string;
  widthMm?: number;
  heightMm?: number;
  printerHost?: string;
  printerPort?: number;
  /** 字段映射布局 JSON: {items:[{type,field,x,y,size,height}]} */
  content?: string;
  enabled?: string;
  remark?: string;
}

/** 标签模板表单类型 */
export type MesLabelTemplateForm = MesLabelTemplateVO;

/** 渲染预览结果 */
export interface LabelPreviewVO {
  payload: string;
  charset: string;
  lang: string;
  /** 本次样例预览使用的模板修订号。 */
  templateRevision?: number | string;
}

/** 分页查询标签模板 */
export const listLabelTemplate = (query?: any): AxiosPromise<MesLabelTemplateVO[]> =>
  request({ url: '/mes/label/template/list', method: 'get', params: query });

/** 标签模板详情 */
export const getLabelTemplate = (templateId: number | string): AxiosPromise<MesLabelTemplateVO> =>
  request({ url: `/mes/label/template/${templateId}`, method: 'get' });

/** 新增标签模板 */
export const addLabelTemplate = (data: MesLabelTemplateForm): AxiosPromise<void> => request({ url: '/mes/label/template', method: 'post', data });

/** 修改标签模板 */
export const updateLabelTemplate = (data: MesLabelTemplateForm): AxiosPromise<void> => request({ url: '/mes/label/template', method: 'put', data });

/** 删除标签模板 */
export const delLabelTemplate = (templateIds: Array<number | string> | number | string): AxiosPromise<void> =>
  request({ url: `/mes/label/template/${templateIds}`, method: 'delete' });

/** 渲染预览: 样例数据渲染指令串(不发送打印机) */
export const previewLabelTemplate = (templateId: number | string): AxiosPromise<LabelPreviewVO> =>
  request({ url: `/mes/label/template/${templateId}/preview`, method: 'get' });

/** 持久化打印任务回执；SENT仅表示指令发送，不代表打印机确认。 */
export interface LabelPrintTaskVO {
  taskId: number | string;
  status: 'PENDING' | 'SENDING' | 'SENT' | 'FAIL' | 'UNKNOWN' | 'LEGACY_SENT';
  snId?: number | string;
  templateId?: number | string;
  templateRevision?: number | string;
  printSource?: string;
  originalPrintLogId?: number | string;
  reprintReason?: string;
  attemptCount?: number;
  detail?: string;
}

/** 测试打印先保存任务；同一requestId重复提交不会重复发送。 */
export const testPrintLabelTemplate = (templateId: number | string, requestId?: string): AxiosPromise<LabelPrintTaskVO> =>
  request({ url: `/mes/label/template/${templateId}/test-print`, method: 'post', params: { requestId } });

/** 查询本租户打印任务状态，不自动触发发送或重发。 */
export const getLabelPrintTask = (taskId: number | string): AxiosPromise<LabelPrintTaskVO> =>
  request({ url: `/mes/label/template/print-task/${taskId}`, method: 'get' });

/** 人工补打必须关联原任务和非空原因；每次明确动作携带稳定请求ID。 */
export const reprintLabelTemplate = (data: {
  snId: number | string;
  templateId: number | string;
  originalPrintLogId: number | string;
  reprintReason: string;
  requestId: string;
}): AxiosPromise<LabelPrintTaskVO> => request({ url: '/mes/label/template/reprint', method: 'post', params: { ...data, printSource: 'REPRINT' } });
