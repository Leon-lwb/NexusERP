export interface PayOrderQuery extends PageQuery {
  orderNumber?: string;
  thirdNumber?: string;
  orderStatus?: string;
  payType?: string;
  orderContent?: string;
}

export interface PayOrderVO {
  orderId: number;
  orderNumber: string;
  thirdNumber: string;
  orderStatus: string;
  totalAmount: string;
  actualAmount: string;
  orderContent: string;
  orderMessage: string;
  payType: string;
  payTime: string;
  payBy: string;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface PayOrderForm {
  orderId?: number;
  orderNumber?: string;
  orderContent?: string;
  totalAmount?: string;
  actualAmount?: string;
  orderMessage?: string;
  payType?: string;
  orderStatus?: string;
  remark?: string;
}
