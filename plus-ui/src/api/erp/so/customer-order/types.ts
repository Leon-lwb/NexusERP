export interface CustomerOrderQuery extends PageQuery {
  coNo?: string;
  clientId?: number;
  clientName?: string;
  status?: string;
  orderDate?: string;
}

export interface CustomerOrderVO {
  coId: number;
  coNo: string;
  clientId: number;
  clientName: string;
  contactName: string;
  contactPhone: string;
  orderDate: string;
  requestedDate: string;
  currency: string;
  totalAmount: number;
  status: string;
  priority: number;
  customerPoNo: string;
  sourceChannel: string;
  clientCode: string;
  attr2: string;
  salesRepId: number;
  salesRepName: string;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
  deptId?: number | string;
  deptName?: string;
}

export interface CustomerOrderForm {
  coId?: number | string;
  coNo?: string;
  clientId?: number | string;
  clientName?: string;
  contactName?: string;
  contactPhone?: string;
  orderDate?: string;
  requestedDate?: string;
  currency?: string;
  totalAmount?: number | string;
  status?: string;
  priority?: number | string;
  customerPoNo?: string;
  sourceChannel?: string;
  clientCode?: string;
  attr2?: string;
  salesRepId?: number | string;
  salesRepName?: string;
  deptId?: number | string;
  deptName?: string;
  remark?: string;
  createBy?: number | string;
  createTime?: string;
  updateBy?: number | string;
  updateTime?: string;
}
