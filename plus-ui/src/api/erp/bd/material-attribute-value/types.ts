export interface MaterialAttributeValueVO {
  valueId?: number;
  materialId?: number;
  attrId: number;
  dataType?: string;
  numericValue?: number | string;
  textValue?: string;
  boolValue?: number;
  dateValue?: string;
  remark?: string;
}

export interface MaterialAttributeValueForm {
  valueId?: number;
  materialId?: number;
  attrId: number;
  dataType?: string;
  numericValue?: number | string;
  textValue?: string;
  boolValue?: number;
  dateValue?: string;
  remark?: string;
}
