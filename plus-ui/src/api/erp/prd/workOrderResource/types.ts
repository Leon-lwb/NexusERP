export interface WorkOrderResourceQuery extends PageQuery {
  woId?: number;
  resourceType?: string;
  resourceName?: string;
  status?: string;
}

export interface WorkOrderResourceVO {
  id: number;
  woId: number;
  resourceType: string;
  resourceId: number;
  resourceCode: string;
  resourceName: string;
  seq: number;
  parentId: number;
  parentType: string;
  planStartTime: string;
  planEndTime: string;
  actualStartTime: string;
  actualEndTime: string;
  status: string;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface WorkOrderResourceForm {
  id?: number;
  woId?: number;
  resourceType?: string;
  resourceId?: number;
  resourceCode?: string;
  resourceName?: string;
  seq?: number;
  parentId?: number;
  parentType?: string;
  planStartTime?: string;
  planEndTime?: string;
  actualStartTime?: string;
  actualEndTime?: string;
  status?: string;
  remark?: string;
}
