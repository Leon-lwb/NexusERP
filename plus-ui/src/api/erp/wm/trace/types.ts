/** 追溯节点 VO */
export interface WmTraceNodeVO {
  /** 唯一节点ID */
  nodeId: string;
  /** 父节点ID */
  parentNodeId: string;
  /** 物料ID */
  materialId: number;
  /** 物料编码 */
  materialCode: string;
  /** 物料名称 */
  materialName: string;
  /** 批次编码 */
  batchCode: string;
  /** 序列号编码 */
  snCode: string;
  /** 数量 */
  quantity: number;
  /** 来源单据类型 */
  docType: string;
  /** 来源单据ID */
  docId: number;
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
  /** 物料ID */
  materialId: number;
  /** 批次编码 */
  batchCode?: string;
  /** 序列号编码 */
  snCode?: string;
}
