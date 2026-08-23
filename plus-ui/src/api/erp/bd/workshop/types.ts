export interface WorkshopQuery extends PageQuery {
  workshopCode?: string;
  workshopName?: string;
  status?: number;
}

export interface WorkshopVO {
  workshopId: number;
  workshopCode: string;
  workshopName: string;
  workshopDesc: string;
  managerId: number;
  managerName: string;
  deptId?: number | string;
  deptName?: string;
  capacity: number;
  status: number;
  sortOrder: number;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface WorkshopForm {
  workshopId?: number;
  workshopCode: string;
  workshopName: string;
  workshopDesc?: string;
  managerId: number;
  managerName: string;
  deptId?: number | string;
  deptName?: string;
  capacity?: number;
  status?: number;
  sortOrder?: number;
  remark?: string;
}
