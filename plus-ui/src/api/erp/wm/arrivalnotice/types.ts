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
  selectFlowId?: number | string;
  remark?: string;
  attr1?: string;
  attr2?: string;
  attr3?: number;
  attr4?: number;
  wmArrivalNoticeLineList?: any[];
  createBy?: number;
  createTime?: string;
  updateBy?: number;
  updateTime?: string;
  [key: string]: any;
}

/** 仓储只读域；两个 query 权限不可相互借用。 */
export type WarehouseDomain = 'arrivalnotice' | 'receive';
export type WarehouseTarget = 'ORDER' | 'NOTICE' | 'RECEIVE';
export type WarehouseProtection = 'CERTIFIED' | 'LEGACY' | 'UNKNOWN';
/** 实际当前主体；操作标志只是提示，不能替代生产 Service 的授权。 */
export interface CertifiedWarehouseContext {
  domain: WarehouseDomain;
  tenantId: string;
  userId: string;
  username: string | null;
  deptId: string | null;
  databaseDate: string;
  createAllowed: boolean;
  submitAllowed: boolean;
  shelfAllowed: boolean;
  executeAllowed: boolean;
}
/** 原生事实不进入可编辑表单；数量由后端精确字符串返回。 */
export interface WarehouseNativeRow {
  tenantId: string;
  [key: string]: unknown;
}
export interface CertifiedWarehouseDetail {
  domain: WarehouseDomain;
  kind: WarehouseTarget;
  documentId: string;
  protection: WarehouseProtection;
  sourceComplete: boolean;
  flowState: string;
  order: WarehouseNativeRow | null;
  orderLines: WarehouseNativeRow[];
  native: WarehouseNativeRow | null;
  lines: WarehouseNativeRow[];
  details: WarehouseNativeRow[];
  iqcEvidence?: WarehouseNativeRow[];
  births?: WarehouseNativeRow[];
  creations?: WarehouseNativeRow[];
  posting?: WarehouseNativeRow | null;
  orderSources?: WarehouseNativeRow[];
  approvals?: WarehouseNativeRow[];
  workflow?: WarehouseNativeRow | null;
}
/** 与 WmCertifiedArrivalNoticeService.Request 完全一致，不提交租户/认证人。 */
export interface CertifiedArrivalRequest {
  requestKey: string;
  orderId: string;
  expectedOrderVersion: number;
  purchaseLineId: string;
  quantity: string;
  noticeName: string;
  arrivalDate: string;
  remark?: string;
}
export interface CertifiedArrivalResult {
  noticeId: string;
  noticeCode: string;
  noticeLineId: string;
  status: string;
  replayed: boolean;
}
