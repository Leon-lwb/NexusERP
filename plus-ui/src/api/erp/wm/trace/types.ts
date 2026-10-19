/** 追溯节点 VO */
export interface WmTraceNodeVO {
  /** 唯一节点ID */
  nodeId: string;
  /** 父节点ID */
  parentNodeId: string;
  /** 物料ID */
  materialId: string | number;
  /** 物料编码 */
  materialCode: string;
  /** 物料名称 */
  materialName: string;
  /** 批次编码 */
  batchCode: string;
  /** 序列号编码 */
  snCode: string;
  /** 数量 */
  quantity: string | number;
  /** 来源单据类型 */
  docType: string;
  /** 来源单据ID */
  docId: string | number;
  /** 来源单据编号 */
  docNo: string;
  /** 仓库名称 */
  warehouseName: string;
  /** 事务时间 */
  transactionTime: string;
  /** 子节点 */
  children: WmTraceNodeVO[];
}

/** 追溯结果 VO */
export interface WmTraceResultVO {
  traceScope?: string;
  scope?: string;
  legacyGaps?: unknown[];
  gaps?: unknown[];
  consumptions?: Record<string, unknown>[];
  /** 追溯方向：FORWARD / BACKWARD */
  direction: string;
  /** 追溯关键字 */
  traceKey: string;
  /** 扁平节点列表 */
  nodes: WmTraceNodeVO[];
  /** 树形结构根节点 */
  tree: WmTraceNodeVO;
}

/** 追溯查询参数 */
export interface WmTraceQuery {
  identityId?: string;
  /** 物料ID */
  materialId: string | number;
  /** 批次编码 */
  batchCode?: string;
  /** 序列号编码 */
  snCode?: string;
}
/** 授权模块由首次身份查询返回，后续只查询明确模块的明确分节。 */
export type TraceModule = 'ERP' | 'MES';
export type TraceSectionName = 'STOCK' | 'MOVEMENTS' | 'ALIASES' | 'CONSUMPTION' | 'TEST' | 'IPQC_WORK_ORDER' | 'MANIFEST';

export interface TraceSectionPage {
  rows: Record<string, unknown>[];
  nextCursor?: string | null;
  hasMore: boolean;
}

export interface IdentityTracePart {
  module: TraceModule;
  identityId: string;
  localSnId?: string;
  /** 模块本地原码只供展示，不作为正式登记或跨模块关联的证明。 */
  localSnCode?: string | null;
  /** 服务端缺少正式登记证据时明确返回null，不能回退为本地原码。 */
  canonicalSnCode?: string | null;
  scope: string;
  sections: Partial<Record<TraceSectionName, TraceSectionPage>>;
  evidenceGaps?: unknown[];
}

export interface IdentityTraceResult {
  identityId: string;
  scope: string;
  parts: IdentityTracePart[];
  truncated?: boolean;
  evidenceGaps?: unknown[];
}

export interface IdentityTracePageQuery {
  module?: TraceModule;
  section?: TraceSectionName;
  cursor?: string;
  limit?: number;
}

/** 正式身份可能来自逐件证明或粗粒度影响；只有省略scope的原身份行表示逐件确认。 */
export interface BatchRecallIdentityRow {
  identityId: string;
  scope?: string;
}

/** 工单范围消耗及其已分配/未分配数量，不代表某个产品SN已确认耗用该批次。 */
export interface BatchRecallScopeImpact {
  module: TraceModule;
  scope: 'WORK_ORDER';
  consumptionId: string;
  mesWoId: string;
  componentMaterialId: string | null;
  consumedQuantity: string;
  allocatedQuantity: string;
  unallocatedQuantity: string;
}

export interface BatchRecallScopePage {
  rows: BatchRecallScopeImpact[];
  hasMore: boolean;
  nextCursor?: string | null;
}

/** 受影响批次只说明可能影响，逐批按需读取事实，不代表已确认逐件使用。 */
export interface BatchRecallBatchPage {
  rows: Array<{ batchId: string; scope: 'BATCH_POSSIBLE' | 'WORK_ORDER_POSSIBLE' }>;
  hasMore: boolean;
  nextCursor?: string | null;
}

/** 三组游标分别由服务端给出，不能互相替代或从显示行推算。 */
export interface BatchRecallPageQuery {
  afterIdentity?: string;
  scopeCursor?: string;
  batchCursor?: string;
  limit?: number;
  depth?: number;
}

/** 批次自身事实由模块授权裁剪，SOURCE仓库与STOCK当前仓库不是同一含义。 */
export type BatchTraceSectionName = 'STOCK' | 'MOVEMENTS' | 'SOURCE' | 'LOTS';
export interface BatchTracePart {
  module: TraceModule;
  batchId: string;
  scope: 'BATCH';
  sections: Partial<Record<BatchTraceSectionName, TraceSectionPage>>;
  evidenceGaps?: unknown[];
}
export interface BatchTraceResult {
  batchId: string;
  scope: 'BATCH';
  parts: BatchTracePart[];
  truncated?: boolean;
  evidenceGaps?: unknown[];
}
export interface BatchTracePageQuery {
  module?: TraceModule;
  section?: BatchTraceSectionName;
  cursor?: string;
  limit?: number;
}

export interface BatchRecallResult {
  parts?: BatchTracePart[];
  batchId: string;
  scope: string;
  rows: BatchRecallIdentityRow[];
  /** 旧契约可能尚未提供，缺失不表示工单范围零影响。 */
  scopeImpacts?: BatchRecallScopePage;
  /** 缺字段不能当作受影响批次为零。 */
  batchImpacts?: BatchRecallBatchPage;
  hasMore: boolean;
  nextCursor?: string | null;
  depthReached?: number | string;
  truncated?: boolean;
  truncatedByDepth?: boolean;
  truncatedByCapacity?: boolean;
  evidenceGaps?: unknown[];
}

/** 同一组件身份的去向分析也提供独立身份/工单/批次页。 */
export type IdentityWhereUsedResult = Omit<BatchRecallResult, 'batchId' | 'parts'> & { componentIdentityId: string };
export type ComponentImpactResult = BatchRecallResult | IdentityWhereUsedResult;
