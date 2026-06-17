/** BOM替代料视图对象 */
export interface BomAlternativeVO {
  alternativeId: number;
  detailId: number;
  bomId: number;
  altMaterialId: number;
  altMaterialCode: string;
  altMaterialName: string;
  altQuantity: number;
  priority: number;
  substitutionType: string;
  effectiveDate: string;
  expiryDate: string;
  status: number;
  remark: string;
}

/** BOM替代料业务对象 */
export interface BomAlternativeForm {
  alternativeId?: number;
  detailId: number;
  bomId: number;
  altMaterialId: number;
  altMaterialCode?: string;
  altMaterialName?: string;
  altQuantity: number;
  priority?: number;
  substitutionType?: string;
  effectiveDate?: string;
  expiryDate?: string;
  status?: number;
  remark?: string;
}
