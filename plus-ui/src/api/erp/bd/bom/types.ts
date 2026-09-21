export interface BomQuery extends PageQuery {
  parentMaterialId?: number;
  bomVersion?: string;
  bomName?: string;
  bomType?: string;
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
  bomType?: string;
  routingId?: number;
  routingCode?: string;
  routingName?: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface BomForm {
  bomId?: number;
  parentMaterialId?: number;
  parentMaterialName?: string;
  bomVersion?: string;
  bomName?: string;
  effectiveDate?: string;
  expiryDate?: string;
  isCurrent?: number;
  status?: number;
  remark?: string;
  sourceType?: string;
  bomType?: string;
  routingId?: number;
  backflushFlag?: number;
  createBy?: number;
  createTime?: string;
  updateBy?: number;
  updateTime?: string;
  [key: string]: any;
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
  leadTime: number;
  effectiveDate: string;
  expiryDate: string;
  sortOrder: number;
  status: number;
  statusDesc: string;
  remark: string;
  sourceType?: string;
  /** 供应方: SELF_PROVIDED 自备 / VENDOR_PROVIDED 外协厂供 / CUSTOMER_PROVIDED 客供（ERP-BD-01，字典 bd_supply_type） */
  supplyType?: string;
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
  leadTime?: number;
  effectiveDate?: string;
  expiryDate?: string;
  sortOrder?: number;
  status?: number;
  remark?: string;
  sourceType?: string;
  /** 供应方（ERP-BD-01，后端 BdBomDetailBo/Vo.supplyType 已打通） */
  supplyType?: string;
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
  unitCost?: number;
  lineCost?: number;
  unitName: string;
  sourceType: string;
  leadTime: number;
  workstationName: string;
  expandable: boolean;
  detailId: number;
  parentBomId: number;
}

/** BOM比较结果 */
export interface BomComparisonVo {
  bom1: BomSummary;
  bom2: BomSummary;
  onlyInBom1: ComparisonLine[];
  onlyInBom2: ComparisonLine[];
  changed: ComparisonLine[];
  unchanged: ComparisonLine[];
}

export interface BomSummary {
  bomId: number;
  bomName: string;
  bomVersion: string;
  bomType: string;
  materialCode: string;
  materialName: string;
  totalItems: number;
}

export interface ComparisonLine {
  childMaterialId: number;
  childMaterialCode: string;
  childMaterialName: string;
  childMaterialSpec: string;
  quantity1: number;
  quantity2: number;
  lossRate1: number;
  lossRate2: number;
  operationStep1: number;
  operationStep2: number;
  changeType: 'ADDED' | 'REMOVED' | 'MODIFIED' | 'UNCHANGED';
  changeDetail: string;
}

/** BOM成本卷积结果 */
export interface BomCostRollupVo {
  bomId: number;
  parentMaterialId: number;
  parentMaterialCode: string;
  parentMaterialName: string;
  bomVersion: string;
  routingId: number;
  routingCode: string;
  totalMaterialCost: number;
  totalRoutingCost: number;
  totalCostPerUnit: number;
  routingCostAvailable: boolean;
  materialLines: MaterialCostLine[];
  routingLines: RoutingCostLine[];
}

export interface MaterialCostLine {
  materialId: number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  cumulativeQuantity: number;
  unitCost: number;
  lineCost: number;
}

export interface RoutingCostLine {
  stepId: number;
  sequenceNum: number;
  processName: string;
  workstationName: string;
  standardHours: number;
  laborCount: number;
  laborRate: number;
  machineCount: number;
  machineRate: number;
  stepCost: number;
}
