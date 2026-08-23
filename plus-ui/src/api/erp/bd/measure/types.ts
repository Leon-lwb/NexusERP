export interface MeasureQuery extends PageQuery {
  uomCode?: string;
  uomName?: string;
  uomSymbol?: string;
  category?: string;
  status?: number | string;
  isBase?: string;
  excludeId?: number | string;
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
  uomId?: number | string;
  uomCode?: string;
  uomName?: string;
  uomSymbol?: string;
  category?: string;
  isBase?: number | string;
  primaryId?: number | string;
  primaryName?: string;
  conversionRate?: number;
  status?: number | string;
  sortOrder?: number;
  remark?: string;
  createBy?: number | string;
  createTime?: string;
  updateBy?: number | string;
  updateTime?: string;
}
