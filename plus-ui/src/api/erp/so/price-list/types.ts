export interface PriceListQuery extends PageQuery {
  listName?: string;
  listType?: string;
  clientId?: number;
  status?: string;
  /** 生效日期下界（对应实体 validFrom） */
  validFrom?: string;
  /** 失效日期上界（对应实体 validTo） */
  validTo?: string;
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
  /** 留空表示不设上限 */
  maxQty?: number;
  /** 折扣率（百分数：5 表示让利 5%，0 表示不让利） */
  discountRate?: number;
}

export interface PriceListVO {
  priceListId?: number;
  listCode?: string;
  listName?: string;
  listType?: string;
  clientId?: number;
  /** 客户名称（后端按 clientId 回填，不落库） */
  clientName?: string;
  currency?: string;
  priority?: number;
  validFrom?: string;
  validTo?: string;
  status?: string;
  remark?: string;
  soPriceListLineList?: PriceListLineVO[];
  createBy?: number;
  createTime?: string;
  updateBy?: number;
  updateTime?: string;
}

export interface PriceListForm {
  priceListId?: number;
  /** 留空由后端按 PRICE_LIST_CODE 规则生成 */
  listCode?: string;
  listName: string;
  listType?: string;
  clientId?: number;
  clientName?: string;
  currency?: string;
  priority?: number;
  validFrom?: string;
  validTo?: string;
  status?: string;
  remark?: string;
  soPriceListLineList?: PriceListLineVO[];
}
