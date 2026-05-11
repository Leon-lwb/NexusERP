export interface MeasureQuery extends PageQuery {
  uomCode?: string;
  uomName?: string;
  category?: string;
  status?: number;
  isBase?: string;
}

export interface MeasureVO {
  uomId: number;
  uomCode: string;
  uomName: string;
  uomSymbol: string;
  category: string;
  isBase: number;
  primaryId: number;
  primaryName: string;
  conversionRate: number;
  status: number;
  sortOrder: number;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface MeasureForm {
  uomId?: number;
  uomCode: string;
  uomName: string;
  uomSymbol?: string;
  category?: string;
  isBase?: number;
  primaryId?: number;
  primaryName?: string;
  conversionRate?: number;
  status?: number;
  sortOrder?: number;
  remark?: string;
}
