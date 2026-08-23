export interface ReceiveQuery extends PageQuery {
  receiveCode?: string;
  receiveName?: string;
  noticeCode?: string;
  purCode?: string;
  vendorName?: string;
  receiveDate?: string;
  status?: string;
}

export interface ReceiveVO {
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

export interface ReceiveLineVO {
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

export interface ReceiveDetailVO {
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

export interface ReceiveForm {
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
