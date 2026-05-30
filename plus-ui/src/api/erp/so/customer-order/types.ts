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
}

export interface CustomerOrderForm {
  coId?: number;
  coNo?: string;
  clientId?: number;
  clientName?: string;
  contactName?: string;
  contactPhone?: string;
  orderDate?: string;
  requestedDate?: string;
  currency?: string;
  totalAmount?: number;
  status?: string;
  priority?: number;
  customerPoNo?: string;
  sourceChannel?: string;
  clientCode?: string;
  attr2?: string;
  salesRepId?: number;
  salesRepName?: string;
  remark?: string;
}
