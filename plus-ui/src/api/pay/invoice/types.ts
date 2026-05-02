export interface PayInvoiceQuery extends PageQuery {
  orderNumber?: string;
  invoiceType?: string;
  invoiceHeader?: string;
}

export interface PayInvoiceVO {
  invoiceId: number;
  orderNumber: string;
  invoiceType: string;
  invoiceHeader: string;
  invoiceNumber: string;
  invoicePhone: string;
  invoiceEmail: string;
  invoiceRemark: string;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface PayInvoiceForm {
  invoiceId?: number;
  orderNumber?: string;
  invoiceType?: string;
  invoiceHeader?: string;
  invoiceNumber?: string;
  invoicePhone?: string;
  invoiceEmail?: string;
  invoiceRemark?: string;
  remark?: string;
}
