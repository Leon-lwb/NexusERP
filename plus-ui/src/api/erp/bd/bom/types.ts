export interface BomQuery extends PageQuery {
  parentMaterialId?: number;
  bomVersion?: string;
  bomName?: string;
  isCurrent?: number;
  status?: number;
}

export interface BomVO {
  bomId: number;
  parentMaterialId: number;
  parentMaterialCode: string;
  parentMaterialName: string;
  bomVersion: string;
  bomName: string;
  effectiveDate: string;
  expiryDate: string;
  isCurrent: number;
  status: number;
  remark: string;
  sourceType?: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface BomForm {
  bomId?: number;
  parentMaterialId: number;
  bomVersion: string;
  bomName: string;
  effectiveDate?: string;
  expiryDate?: string;
  isCurrent?: number;
  status?: number;
  remark?: string;
  sourceType?: string;
  backflushFlag?: number;
}

export interface BomDetailVO {
  detailId: number;
  bomId: number;
  parentMaterialId: number;
  parentMaterialCode: string;
  parentMaterialName: string;
  childMaterialId: number;
  childMaterialCode: string;
  childMaterialName: string;
  childMaterialSpec: string;
  baseUomId: number;
  baseUomName: string;
  quantityPerParent: number;
  lossRate: number;
  totalQuantity: number;
  operationStep: number;
  workstationId: number;
  workstationName: string;
  leadTime: number;
  effectiveDate: string;
  expiryDate: string;
  sortOrder: number;
  status: number;
  statusDesc: string;
  remark: string;
  sourceType?: string;
  backflushFlag?: number;
}

export interface BomDetailForm {
  detailId?: number;
  bomId: number;
  childMaterialId: number;
  childMaterialCode?: string;
  childMaterialName?: string;
  quantityPerParent: number;
  lossRate?: number;
  operationStep: number;
  workstationId?: number;
  workstationName?: string;
  leadTime?: number;
  effectiveDate?: string;
  expiryDate?: string;
  sortOrder?: number;
  status?: number;
  remark?: string;
  sourceType?: string;
}

export interface BomTreeNodeVo {
  bomId: number;
  materialId: number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  materialType: string;
  level: number;
  nodeType: 'ASSEMBLY' | 'PART';
  parentBomId: number;
  detailId: number;
  quantityPerParent: number;
  lossRate: number;
  totalQuantity: number;
  sourceType: string;
  leadTime: number;
  hasChildren: boolean;
  isLeaf: boolean;
  children: BomTreeNodeVo[];
}

export interface IndentedBomLineVo {
  level: number;
  indent: string;
  bomId: number;
  materialId: number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  materialType: string;
  nodeType: 'ASSEMBLY' | 'PART';
  quantityPerParent: number;
  lossRate: number;
  cumulativeQuantity: number;
  unitName: string;
  sourceType: string;
  leadTime: number;
  workstationName: string;
  expandable: boolean;
  detailId: number;
  parentBomId: number;
}
