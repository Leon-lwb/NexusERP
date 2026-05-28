export interface WmReconciliationVO {
  reconId: number;
  materialId: number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  baseUomName: string;
  bookQty: number;
  txBalance: number;
  diffQty: number;
  status: string;
  reconTime: string;
  remark: string;
  createTime: string;
}

export interface WmReconciliationQuery extends PageQuery {
  materialCode?: string;
  materialName?: string;
  status?: string;
}
