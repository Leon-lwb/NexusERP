export interface PriceListQuery extends PageQuery {
  listName?: string;
  listType?: string;
  clientId?: number;
  status?: string;
  effectiveDateFrom?: string;
  effectiveDateTo?: string;
}

export interface PriceListLineVO {
  lineId?: number;
  priceListId?: number;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  unitPrice?: number;
  minQty?: number;
  maxQty?: number;
  discountRate?: number;
  effectiveDateFrom?: string;
  effectiveDateTo?: string;
}

export interface PriceListVO {
  priceListId?: number;
  listName?: string;
  listType?: string;
  clientId?: number;
  clientName?: string;
  effectiveDateFrom?: string;
  effectiveDateTo?: string;
  status?: string;
  currency?: string;
  remark?: string;
  soPriceListLineList?: PriceListLineVO[];
  createBy?: number;
  createTime?: string;
  updateBy?: number;
  updateTime?: string;
}

export interface PriceListForm {
  priceListId?: number;
  listName: string;
  listType?: string;
  clientId?: number;
  clientName?: string;
  effectiveDateFrom?: string;
  effectiveDateTo?: string;
  status?: string;
  currency?: string;
  remark?: string;
  soPriceListLineList?: PriceListLineVO[];
}
