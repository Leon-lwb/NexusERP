import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 检验项目视图对象 */
export interface QcInspectItemVO {
  itemId: number;
  templateId: number;
  itemCode: string;
  itemName: string;
  dataType: string;
  unit?: string;
  standardType?: string;
  nominalValue?: number;
  lslValue?: number;
  uslValue?: number;
  tolUpper?: number;
  tolLower?: number;
  aqlLevel?: string;
  samplePlanCode?: string;
  criticality?: string;
  seqNum?: number;
  enabled?: string;
  remark?: string;
}

/** 检验项目表单 */
export interface QcInspectItemForm {
  itemId?: number;
  itemCode: string;
  itemName: string;
  dataType: string;
  unit?: string;
  standardType?: string;
  nominalValue?: number;
  lslValue?: number;
  uslValue?: number;
  tolUpper?: number;
  tolLower?: number;
  aqlLevel?: string;
  samplePlanCode?: string;
  criticality?: string;
  seqNum?: number;
  enabled?: string;
  remark?: string;
}

/** 检验模板视图对象 */
export interface QcInspectTemplateVO {
  templateId: number;
  templateCode: string;
  templateName: string;
  templateType: string;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  versionNo?: number;
  status: string;
  remark?: string;
  items?: QcInspectItemVO[];
}

/** 检验模板查询条件 */
export interface QcInspectTemplateQuery extends PageQuery {
  templateCode?: string;
  templateName?: string;
  templateType?: string;
  materialId?: number;
  status?: string;
}

/** 检验模板表单(项目明细随模板整体提交) */
export interface QcInspectTemplateForm {
  templateId?: number;
  templateCode: string;
  templateName: string;
  templateType: string;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  versionNo?: number;
  status?: string;
  remark?: string;
  items?: QcInspectItemForm[];
}

/** 分页查询检验模板列表 */
export const listTemplate = (query?: QcInspectTemplateQuery): AxiosPromise<QcInspectTemplateVO[]> => {
  return request({ url: '/mes/qc/template/list', method: 'get', params: query });
};

/** 获取检验模板详情(含项目明细) */
export const getTemplate = (templateId: string | number): AxiosPromise<QcInspectTemplateVO> => {
  return request({ url: `/mes/qc/template/${templateId}`, method: 'get' });
};

/** 新增检验模板 */
export const addTemplate = (data: QcInspectTemplateForm) => {
  return request({ url: '/mes/qc/template', method: 'post', data });
};

/** 修改检验模板(项目明细先删后插) */
export const updateTemplate = (data: QcInspectTemplateForm) => {
  return request({ url: '/mes/qc/template', method: 'put', data });
};

/** 删除检验模板 */
export const delTemplate = (templateId: number | number[]) => {
  return request({ url: '/mes/qc/template/' + templateId, method: 'delete' });
};

/** 启用模板 */
export const activateTemplate = (templateId: number) => {
  return request({ url: '/mes/qc/template/activate/' + templateId, method: 'put' });
};

/** 停用模板 */
export const deactivateTemplate = (templateId: number) => {
  return request({ url: '/mes/qc/template/deactivate/' + templateId, method: 'put' });
};

/** 版本复制(生成新版本草稿) */
export const copyTemplate = (templateId: number): AxiosPromise<number> => {
  return request({ url: '/mes/qc/template/copy/' + templateId, method: 'post' });
};
