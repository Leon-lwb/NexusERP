import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 标签打印模板视图对象 */
export interface MesLabelTemplateVO {
  templateId: number | string;
  templateCode: string;
  templateName: string;
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

/** 测试打印: 样例数据渲染并直发打印机 */
export const testPrintLabelTemplate = (templateId: number | string): AxiosPromise<void> =>
  request({ url: `/mes/label/template/${templateId}/test-print`, method: 'post' });
