/**
 * 物料-供应商关系查询参数
 */
export interface MaterialVendorQuery extends PageQuery {
  /** 物料ID */
  materialId?: number;
  /** 物料编码 */
  materialCode?: string;
  /** 物料名称 */
  materialName?: string;
  /** 供应商ID */
  vendorId?: number;
  /** 供应商编码 */
  vendorCode?: string;
  /** 供应商名称 */
  vendorName?: string;
  /** 状态 */
  status?: number;
}

/**
 * 物料-供应商关系视图对象
 */
export interface MaterialVendorVO {
  /** 关系ID */
  relationId: number;
  /** 物料ID */
  materialId: number;
  /** 物料编码（关联查询填充） */
  materialCode?: string;
  /** 物料名称（关联查询填充） */
  materialName?: string;
  /** 供应商ID */
  vendorId: number;
  /** 供应商编码 */
  vendorCode: string;
  /** 供应商名称 */
  vendorName: string;
  /** 供货周期(天) */
  leadTime: number;
  /** 最小订货量 */
  minOrderQty: number;
  /** 单价 */
  price: number;
  /** 币种 */
  currency: string;
  /** 是否默认供应商 */
  isDefault: number;
  /** 优先级 */
  priority: number;
  /** 生效日期 */
  effectiveDate: string;
  /** 失效日期 */
  expiryDate: string;
  /** 状态 */
  status: number;
  /** 备注 */
  remark: string;
  /** 创建时间 */
  createTime: string;
  /** 更新时间 */
  updateTime: string;
}

/**
 * 物料-供应商关系表单对象
 */
export interface MaterialVendorForm {
  /** 关系ID */
  relationId?: number;
  /** 物料ID */
  materialId: number;
  /** 物料编码（关联查询填充，非持久化） */
  materialCode?: string;
  /** 物料名称（关联查询填充，非持久化） */
  materialName?: string;
  /** 供应商ID */
  vendorId: number;
  /** 供应商编码 */
  vendorCode?: string;
  /** 供应商名称 */
  vendorName?: string;
  /** 供货周期(天) */
  leadTime?: number;
  /** 最小订货量 */
  minOrderQty?: number;
  /** 单价 */
  price?: number;
  /** 币种 */
  currency?: string;
  /** 是否默认供应商 */
  isDefault?: number;
  /** 优先级 */
  priority?: number;
  /** 生效日期 */
  effectiveDate?: string;
  /** 失效日期 */
  expiryDate?: string;
  /** 状态 */
  status?: number;
  /** 备注 */
  remark?: string;
}
