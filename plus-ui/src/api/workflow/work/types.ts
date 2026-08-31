export interface ProcessVO extends BaseEntity {
  category: string;
  definitionId: string;
  deploymentId: string;
  deploymentTime: string;
  formId: string | number;
  formName: string;
  processKey: string;
  processName: string;
  taskId: string;
  suspended: boolean;
  version: number;
  procInsId?: string;
  /** 业务数据ID */
  dataId?: string;
  /** 抄送记录ID */
  copyId?: string;
  /** 流程实例ID(抄送列表) */
  instanceId?: string;
}

export interface ProcessQuery extends PageQuery {
  processKey?: string;
  processName?: string;
  category?: string;
  state?: string;
  processId?: string;
  categoryId?: string;
  taskId?: string;
  userId?: string;
  originatorName?: string;
}

export interface TaskForm {
  comment: string;
  procInsId: string;
  taskId: string;
  userId: string;
  copyUserIds: string;
  nextUserIds: string;
  vars: string;
  targetKey: string;
  /** 加签人员ID(逗号分隔) */
  addSignUsers?: string;
}
