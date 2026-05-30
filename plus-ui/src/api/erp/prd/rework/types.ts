export interface PrdReworkOrderVO {
  reworkId: number;
  reworkNo: string;
  sourceWoId: number;
  sourceWoNo: string;
  materialId: number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  baseUomName: string;
  reworkQty: number;
  reworkReason: string;
  actualQty: number;
  scrapQty: number;
  status: string;
  remark: string;
  createTime: string;
}

export interface PrdReworkOrderQuery extends PageQuery {
  reworkNo?: string;
  sourceWoNo?: string;
  materialCode?: string;
  materialName?: string;
  status?: string;
  reworkReason?: string;
}

export interface PrdReworkOrderForm {
  reworkId?: number;
  reworkNo?: string;
  sourceWoId?: number;
  sourceWoNo?: string;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  baseUomName?: string;
  reworkQty?: number;
  reworkReason?: string;
  actualQty?: number;
  scrapQty?: number;
  remark?: string;
}
