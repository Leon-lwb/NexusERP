export interface ArrivalnoticeQuery extends PageQuery {
  noticeCode?: string;
  noticeName?: string;
  purOrderCode?: string;
  vendorId?: number;
  vendorCode?: string;
  vendorName?: string;
  vendorNick?: string;
  arrivalDate?: string;
  contact?: string;
  phone?: string;
  status?: string;
}

export interface ArrivalnoticeVO {
  noticeId: number;
  noticeCode: string;
  noticeName: string;
  purOrderCode: string;
  vendorId: number;
  vendorCode: string;
  vendorName: string;
  vendorNick: string;
  arrivalDate: string;
  contact: string;
  phone: string;
  status: string;
  remark: string;
  attr1: string;
  attr2: string;
  attr3: number;
  attr4: number;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
  wmArrivalNoticeLineList?: ArrivalNoticeLineVO[];
}

export interface ArrivalNoticeLineVO {
  lineId: number;
  noticeId: number;
  materialId: number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  baseUomId: string;
  baseUomName: string;
  quantityArrival: number;
  quantityQuanlified: number;
  iqcCheck: string;
  iqcId: number;
  iqcCode: string;
  remark: string;
}

export interface ArrivalnoticeForm {
  noticeId?: number;
  noticeCode: string;
  noticeName: string;
  purOrderCode?: string;
  vendorId?: number;
  vendorCode?: string;
  vendorName?: string;
  vendorNick?: string;
  arrivalDate?: string;
  contact?: string;
  phone?: string;
  status?: string;
  remark?: string;
  attr1?: string;
  attr2?: string;
  attr3?: number;
  attr4?: number;
  wmArrivalNoticeLineList?: any[];
}
