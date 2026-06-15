/**
 * 采购价格对比模块类型定义
 */

/** 价格对比查询参数 */
export interface PriceComparisonQuery {
  /** 物料ID */
  materialId: number;
  /** 供应商分类（可选） */
  vendorCategory?: string;
  /** 时间范围（可选） */
  timeRange?: string[];
}

/** 价格对比数据项 */
export interface PriceComparisonVO {
  /** 供应商ID */
  vendorId: number;
  /** 供应商编码 */
  vendorCode: string;
  /** 供应商名称 */
  vendorName: string;
  /** 单价 */
  price: number;
  /** 币种 */
  currency: string;
  /** 供货周期（天） */
  leadTime: number;
  /** 最小订货量 */
  minOrderQty: number;
  /** 是否默认供应商（1-是 0-否） */
  isDefault: number;
  /** 优先级 */
  priority: number;
  /** 状态（1-启用 0-停用） */
  status: number;
}

/** 价格趋势数据项 */
export interface PriceTrendVO {
  /** 月份 */
  month: string;
  /** 供应商ID */
  vendorId: number;
  /** 供应商名称 */
  vendorName: string;
  /** 单价 */
  price: number;
}

/** 价格排名数据项 */
export interface PriceRankingVO {
  /** 排名 */
  rank: number;
  /** 供应商ID */
  vendorId: number;
  /** 供应商名称 */
  vendorName: string;
  /** 单价 */
  price: number;
  /** 币种 */
  currency: string;
}
