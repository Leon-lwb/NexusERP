import request, { download } from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 备件主数据视图对象 */
export interface EqSparePartVO {
  spareId: number;
  spareCode: string;
  spareName: string;
  spec?: string;
  model?: string;
  unit?: string;
  category?: string;
  safetyStock?: number;
  /** 当前库存(ERP 库存的派生冗余缓存, 权威账本在 ERP wm_material_stock) */
  currentStock?: number;
  /** ERP物料ID(bd_material.material_id), 未映射为 null/空 */
  materialId?: string;
  /** ERP仓库ID(wm_warehouse.warehouse_id), 未映射为 null/空 */
  warehouseId?: string;
  /** ERP可用量(在库 − 已预留, 且 frozen_flag = 0); 未映射物料时为 null */
  erpAvailableStock?: number;
  /** 是否已映射 ERP 物料 */
  erpMapped?: boolean;
  /** 低库存判定口径: ERP_AVAILABLE / NOT_MAPPED / ERP_UNAVAILABLE */
  lowStockBasis?: string;
  /** 是否低于安全库存(按 ERP 可用量判定) */
  lowStock?: boolean;
  /** 参考单价(用于 ERP 移动平均计价) */
  unitCost?: number;
  remark?: string;
}

/** 备件查询条件 */
export interface EqSparePartQuery extends PageQuery {
  spareCode?: string;
  spareName?: string;
  category?: string;
}

/** 备件表单 */
export interface EqSparePartForm {
  spareId?: number;
  spareCode: string;
  spareName: string;
  spec?: string;
  model?: string;
  unit?: string;
  category?: string;
  safetyStock?: number;
  /** ERP物料ID(bd_material.material_id); 严格模式下未映射不允许出入库 */
  materialId?: string;
  /** ERP仓库ID(wm_warehouse.warehouse_id); 出入库过账目标仓库 */
  warehouseId?: string;
  /** 参考单价(保留两位以上小数, 用于 ERP 移动平均计价) */
  unitCost?: number;
  remark?: string;
}

/** 出入库参数 */
export interface EqSpareStockBo {
  spareId: number;
  transactionType: 'IN' | 'OUT' | 'REPAIR_USE';
  quantity: number;
  refType?: string;
  refId?: number;
  remark?: string;
}

/** 库存流水视图对象 */
export interface EqSpareStockLogVO {
  logId: number;
  spareId: number;
  spareCode?: string;
  spareName?: string;
  transactionType: string;
  quantity?: number;
  beforeQty?: number;
  afterQty?: number;
  refType?: string;
  refId?: number;
  operatorName?: string;
  operateTime?: string;
  remark?: string;
}

/** 分页查询备件列表 */
export const listSparePart = (query?: EqSparePartQuery): AxiosPromise<EqSparePartVO[]> =>
  request({ url: '/mes/eq/spare-part/list', method: 'get', params: query });

/** 获取备件详情 */
export const getSparePart = (spareId: number | string): AxiosPromise<EqSparePartVO> =>
  request({ url: `/mes/eq/spare-part/${spareId}`, method: 'get' });

/** 新增备件 */
export const addSparePart = (data: EqSparePartForm): AxiosPromise<EqSparePartVO> => request({ url: '/mes/eq/spare-part', method: 'post', data });

/** 修改备件 */
export const updateSparePart = (data: EqSparePartForm): AxiosPromise<void> => request({ url: '/mes/eq/spare-part', method: 'put', data });

/** 删除备件 */
export const delSparePart = (spareIds: number | number[]) => request({ url: `/mes/eq/spare-part/${spareIds}`, method: 'delete' });

/** 导出备件 */
export const exportSparePart = (query?: EqSparePartQuery) =>
  download('/mes/eq/spare-part/export', { ...query }, `spare_part_${new Date().getTime()}.xlsx`);

/** 出入库操作 */
export const stockTransaction = (data: EqSpareStockBo): AxiosPromise<number> => request({ url: '/mes/eq/spare-part/stock', method: 'post', data });

/** 库存流水分页查询 */
export const listStockLog = (params?: any): AxiosPromise<EqSpareStockLogVO[]> =>
  request({ url: '/mes/eq/spare-part/stock-log', method: 'get', params });

/** 导出库存流水 */
export const exportStockLog = (params?: any) =>
  download('/mes/eq/spare-part/stock-log/export', { ...params }, `spare_stock_log_${new Date().getTime()}.xlsx`);

/** 低于安全库存的备件 */
export const listLowStock = (): AxiosPromise<EqSparePartVO[]> => request({ url: '/mes/eq/spare-part/lowStock', method: 'get' });

/* ------------------------------------------------------------------
 * MES-EQ-04 备件↔ERP 打通（前端契约冻结，端点/字段/权限码不可改名）
 * ------------------------------------------------------------------ */

/** 备件→ERP 过账同步台账视图对象 */
export interface EqSpareErpSyncVO {
  /** 台账ID */
  syncId: string;
  /** 租户ID */
  tenantId?: string;
  /** 业务幂等键: EQSP:&lt;tenantId&gt;:&lt;logId&gt; */
  bizKey?: string;
  /** 备件ID */
  spareId?: string;
  /** 备件编码 */
  spareCode?: string;
  /** ERP物料ID */
  materialId?: string;
  /** ERP仓库ID */
  warehouseId?: string;
  /** 变动类型: IN / OUT / REPAIR_USE / RETURN */
  transactionType?: string;
  /** 数量 */
  quantity?: number;
  /** 来源单据类型 */
  refType?: string;
  /** 来源单据ID */
  refId?: string;
  /** MES 库存流水ID */
  logId?: string;
  /** 同步状态: PENDING / SENT / SYNCED / FAILED */
  syncStatus?: string;
  /** 重投次数 */
  retryCount?: number;
  /** 最近失败原因 */
  lastError?: string;
  /** 最近投递时间 */
  lastPublishTime?: string;
  /** ERP过账后可用量 */
  erpAvailableAfter?: number;
  /** 备注 */
  remark?: string;
  /** 创建时间 */
  createTime?: string;
}

/** 低库存一键生成采购申请入参 */
export interface EqSparePurchaseRequest {
  /** 备件ID集合(空/不传表示取当前全部低库存备件) */
  spareIds?: (string | number)[];
  /** 备注 */
  remark?: string;
}

/**
 * 低库存一键生成 ERP 采购申请
 * <p>权限 mes:eq:spare:purchase；成功返回提交的请购行数；未映射备件由后端拒绝并返回编码列表，
 * 这里关闭统一错误 toast，由调用方原样展示后端错误信息（避免重复提示）。</p>
 */
export const purchaseRequest = (data: EqSparePurchaseRequest): AxiosPromise<number> =>
  request({ url: '/mes/eq/spare-part/purchase-request', method: 'post', data, headers: { ignoreErrorMsg: 'true' } });

/** 备件→ERP 过账同步台账查询(权限 mes:eq:spare:query) */
export const erpSyncList = (syncStatus?: string): AxiosPromise<EqSpareErpSyncVO[]> =>
  request({ url: '/mes/eq/spare-part/erp-sync', method: 'get', params: syncStatus ? { syncStatus } : {} });

/**
 * 重投/对账单笔过账(权限 mes:eq:spare:stock)
 * <p>返回 false 表示仍未确认 ERP 落账，需检查失败原因；失败信息由调用方原样提示。</p>
 */
export const retryErpSync = (syncId: string | number): AxiosPromise<boolean> =>
  request({ url: `/mes/eq/spare-part/erp-sync/${syncId}/retry`, method: 'post', headers: { ignoreErrorMsg: 'true' } });

/**
 * 回填默认仓库(权限 mes:eq:spare:edit)，返回影响行数。
 * <p>后端未配置默认仓库时返回业务错误，这里关闭统一错误 toast，
 * 由调用方原样展示后端错误信息（避免重复提示）。</p>
 */
export const backfillWarehouse = (): AxiosPromise<number> =>
  request({ url: '/mes/eq/spare-part/backfill-warehouse', method: 'post', headers: { ignoreErrorMsg: 'true' } });
