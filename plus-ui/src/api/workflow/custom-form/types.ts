/**
 * 流程业务表单返回对象
 */
export interface WfCustomFormVO {
  id: string | number;
  businessName: string;
  businessService: string;
  flowName: string;
  deployId: string;
  routeName: string;
  component: string;
  tableId: string | number;
  /** 流程定义ID */
  definitionId?: string;
}

export interface WfCustomForm {
  id: string | number;
  businessName: string;
  businessService: string;
  flowName: string;
  deployId: string;
  routeName: string;
  component: string;
  tableId: string | number;
}

/**
 * 流程业务表单查询对象类型
 */
export interface WfCustomFormQuery extends PageQuery {
  businessName?: string;
  businessService?: string;
  flowName?: string;
  deployId?: string;
  routeName?: string;
  component?: string;
  tableId?: string | number;
}

export interface WfCustomForm {
  id: string | number;
  businessName: string;
  businessService: string;
  flowName: string;
  deployId: string;
  routeName: string;
  component: string;
  tableId: string | number;
}

export interface CustomFormVO {
  id: string | number;
  deployId: string;
  flowName: string;
}
