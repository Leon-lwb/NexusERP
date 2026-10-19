import type { TraceDocumentFields } from '@/api/erp/wm/identity/types';

export interface ReceiveQuery extends PageQuery {
  receiveCode?: string;
  receiveName?: string;
  noticeCode?: string;
  purCode?: string;
  vendorName?: string;
  receiveDate?: string;
  status?: string;
}

export interface ReceiveVO extends TraceDocumentFields {
  receiveId: number;
  receiveCode: string;
  receiveName: string;
  iqcId: number;
  iqcCode: string;
  noticeId: number;
  noticeCode: string;
  purId: number;
  purCode: string;
  vendorId: number;
  vendorCode: string;
  vendorName: string;
  vendorNick: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  areaId: number;
  areaCode: string;
  areaName: string;
  locationId: number;
  locationCode: string;
  locationName: string;
  receiveDate: string;
  status: string;
  selectFlowId: number;
  shelfBy?: number;
  shelfByName?: string;
  shelfTime?: string;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
  wmMaterialReceiveLineList?: ReceiveLineVO[];
}

export interface ReceiveLineVO extends TraceDocumentFields {
  lineId: number;
  receiveId: number;
  sourceDocLineId: number;
  materialId: number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  baseUomId: string;
  baseUomName: string;
  quantityReceived: number;
  batchId: number;
  batchCode: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  areaId: number;
  areaCode: string;
  areaName: string;
  locationId: number;
  locationCode: string;
  locationName: string;
  remark: string;
  index?: number;
  detailList?: ReceiveDetailVO[];
  wmMaterialReceiveLineDetailList?: ReceiveDetailVO[];
}

export interface ReceiveDetailVO extends TraceDocumentFields {
  detailId: number;
  lineId: number;
  receiveId: number;
  materialId: number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  baseUomId: string;
  baseUomName: string;
  quantity: number;
  batchId: number;
  batchCode: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  areaId: number;
  areaCode: string;
  areaName: string;
  locationId: number;
  locationCode: string;
  locationName: string;
  remark: string;
  attr1?: string;
  attr2?: string;
  attr3?: string;
  attr4?: string;
  createBy?: number;
  createTime?: string;
  updateBy?: number;
  updateTime?: string;
}

export interface ReceiveForm extends TraceDocumentFields {
  receiveId?: number;
  receiveCode?: string;
  receiveName?: string;
  iqcId?: number;
  iqcCode?: string;
  noticeId?: number;
  noticeCode?: string;
  purId?: number;
  purCode?: string;
  vendorId?: number;
  vendorCode?: string;
  vendorName?: string;
  vendorNick?: string;
  warehouseId?: number;
  warehouseCode?: string;
  warehouseName?: string;
  areaId?: number;
  areaCode?: string;
  areaName?: string;
  locationId?: number;
  locationCode?: string;
  locationName?: string;
  receiveDate?: string;
  status?: string;
  selectFlowId?: number | string;
  shelfBy?: number;
  shelfByName?: string;
  shelfTime?: string;
  remark?: string;
  attr1?: string;
  attr2?: string;
  attr3?: string;
  attr4?: string;
  createBy?: number;
  createTime?: string;
  updateBy?: number;
  updateTime?: string;
  wmMaterialReceiveLineList?: ReceiveLineVO[];
}

/** 与既有认证收货草稿 DTO 对齐；不支持客户端批次/IQC/单价/批准事实。 */
export interface CertifiedReceiveDraftRequest {
  requestKey: string;
  orderId: string;
  expectedOrderVersion: number;
  purchaseLineId: string;
  quantity: string;
  warehouseId: string;
  areaId: string;
  locationId: string;
  actualArrivalDate: string;
  remark?: string;
  noticeId?: string;
  noticeLineId?: string;
}
export interface CertifiedReceiveDraftResult {
  receiveId: string;
  receiveCode: string;
  lineId: string;
  purchaseLineId: string;
  materialId: string;
  baseUomId: string;
  quantity: string;
  status: string;
  replayed: boolean;
  requestHash: string;
}
export interface CertifiedReceivePostingResult {
  receiveId: string;
  materialId: string;
  quantity: string;
  sourceFingerprint: string;
  replay: boolean;
  notificationPending: boolean;
}
/** 永久恢复仅原结果，消费 pending 不触发补发 MQ 或执行 POST。 */
export interface CertifiedReceiveRecovery {
  receiveId: string;
  orderId: string;
  materialId: string;
  quantity: string;
  sourceFingerprint: string;
  postedAt: string;
  notificationStatus: 'PENDING' | 'ENQUEUED';
  consumptionState: 'PENDING' | 'CONSUMED';
  consumptionEvents: unknown[];
}
