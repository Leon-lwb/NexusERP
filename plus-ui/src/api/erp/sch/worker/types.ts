export interface WorkerQuery extends PageQuery {
  userName?: string;
  employeeId?: string;
  workCategory?: string;
  workStatus?: string;
}

export interface WorkerVO {
  workerId: number;
  userId: number;
  userName: string;
  employeeId: string;
  phonenumber: string;
  sex: string;
  workCategory: string;
  skillLevel: string;
  workStatus: string;
  entryDate: string;
  regularizationDate: string;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface WorkerForm {
  workerId?: number;
  userId?: number;
  userName?: string;
  employeeId?: string;
  phonenumber?: string;
  sex?: string;
  workCategory?: string;
  skillLevel?: string;
  workStatus?: string;
  entryDate?: string;
  regularizationDate?: string;
  remark?: string;
}
