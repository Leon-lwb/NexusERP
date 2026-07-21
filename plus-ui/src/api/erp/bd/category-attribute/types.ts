export interface CategoryAttributeQuery extends PageQuery {
  categoryId?: string | number;
  attrCode?: string;
  attrName?: string;
}

export interface CategoryAttributeVO {
  attrId: number;
  categoryId: number;
  attrCode: string;
  attrName: string;
  dataType: string;
  enumValues?: string;
  unit?: string;
  requiredFlag: number;
  sortOrder: number;
  remark?: string;
}

export interface CategoryAttributeForm {
  attrId?: number;
  categoryId?: number;
  attrCode: string;
  attrName: string;
  dataType: string;
  enumValues?: string;
  unit?: string;
  requiredFlag?: number;
  sortOrder?: number;
  remark?: string;
}
