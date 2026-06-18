export interface ProductQuery extends PageQuery {
  productCode?: string;
  productName?: string;
  productType?: string;
  protocolType?: string;
  status?: string;
}

export interface ProductVO {
  productId: number;
  productCode: string;
  productName: string;
  productType: string;
  protocolType: string;
  description: string;
  status: string;
  remark: string;
  createTime: string;
}

export interface ProductForm {
  productId?: number;
  productCode: string;
  productName: string;
  productType?: string;
  protocolType?: string;
  description?: string;
  status?: string;
  remark?: string;
}
