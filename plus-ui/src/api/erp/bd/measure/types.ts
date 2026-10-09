export interface MeasureQuery extends PageQuery {
  uomCode?: string;
  uomName?: string;
  uomSymbol?: string;
  category?: string;
  status?: number | string;
  isBase?: string;
  excludeId?: number | string;
}

/** 原生 BdUnitOfMeasureVo；Long 可由全局序列化器返回字符串，不借此声明额外认证字段。 */
export interface MeasureVO {
  uomId: string | number;
  uomCode: string;
  uomName: string;
  uomSymbol: string;
  category: string;
  isBase: string | number;
  primaryId: string | number | null;
  primaryName: string | null;
  conversionRate: string | number | null;
  status: string | number;
  sortOrder: number;
  quantityScale: number | null;
  conversionVersion: string | null;
  conversionVerifiedAt: string | null;
}

/** 仅为当前登录主体及按钮提示；不是数量事实认证或服务端写授权。 */
export interface QuantityConfirmationContext {
  tenantId: string;
  userId: string | number;
  confirmAllowed: boolean;
}

/** 与既有 BdUnitQuantityConfirmBo 完全同名；NULL 原版本合法，decimal 不转换为 JS Number。 */
export interface QuantityConfirmationBody {
  expectedVersion: string | null;
  primaryId: string;
  conversionRate: string;
  quantityScale: number;
  reference: string;
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
  conversionRate?: string | number;
  status?: number | string;
  sortOrder?: number;
  remark?: string;
  createBy?: number | string;
  createTime?: string;
  updateBy?: number | string;
  updateTime?: string;
}
