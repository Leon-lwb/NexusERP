import { AppTypeVo } from "@/api/workflow/category/types";
export interface ModelVO extends BaseEntity {
  modelId: string;
  modelKey: string;
  modelName: string;
  category: string;
  version: number;
  formType: number;
  formId: number | string;
  description: string;
  createTime: string;
  bpmnXml: string;
  content: string;
}

export interface ModelForm {
  modelId: string | undefined;
  modelKey: string;
  modelName: string;
  category: string;
  description: string;
  formType: number | undefined;
  formId: number | string | undefined;
  bpmnXml: string;
  newVersion: boolean;
}

export interface ModelQuery extends PageQuery {
  modelKey?: string;
  modelName?: string;
}

export interface DesignerForm {
  modelId: string;
  form: {
    appType: AppTypeVo;
    processName: string;
    processKey: string;
  };
}
