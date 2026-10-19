/** 物料追溯接口中的身份、数量与版本均按原字符串传递。 */
export interface TrackingPolicy {
  batchManaged: boolean;
  serialManaged: boolean;
  allowMixedLot: boolean;
  version: string;
  stage: 'AUDIT' | 'STRICT';
}

/** 统一解析请求；OCR 对象整体编码到 rawCode，不能只抽取料号。 */
export interface ResolveCodeRequest {
  rawCode: string;
  scene: string;
  materialId?: string;
  supplierId?: string;
  customerId?: string;
  sourceDocType?: string;
  sourceDocId?: string;
  lineId?: string;
}

export interface ResolvedMaterialObject {
  objectType: 'SN' | 'BATCH' | 'MATERIAL' | 'DOCUMENT';
  /** 已有主码声明的对象类型；历史NULL不推定正式映射。 */
  primaryObjectType?: 'SN' | 'BATCH' | 'MATERIAL' | 'DOCUMENT' | null;
  /** DOCUMENT 单据身份单独建模，不借用 SN 编码或身份。 */
  objectId?: string;
  documentType?: string;
  documentCode?: string;
  identityId?: string;
  localSnId?: string;
  canonicalSnCode?: string;
  snCode?: string;
  materialId?: string;
  materialCode?: string;
  materialName?: string;
  batchId?: string;
  batchCode?: string;
  policy?: TrackingPolicy;
  reason?: string;
}

export interface CodeResolution extends Partial<ResolvedMaterialObject> {
  status: 'RESOLVED' | 'AMBIGUOUS' | 'NOT_FOUND' | 'INVALID';
  candidates?: ResolvedMaterialObject[];
}

/** 单据行实物清单；本地 MES snId 不能冒充 identityId。 */
export interface DocumentSnItem {
  identityId: string;
  snCode?: string;
  materialId?: string;
  batchId?: string;
  batchCode?: string;
  sourceStockId?: string;
  targetLocationId?: string;
  originalTxId?: string;
}

/** 既有业务字段保留在单据上，追溯字段只扩展原请求。 */
export interface TraceDocumentFields {
  commandId?: string;
  detailVersion?: string;
  /** 服务端过账协议修订，与 opaque 详情摘要分开保留。 */
  detailRevision?: string;
  snItems?: DocumentSnItem[];
  policy?: TrackingPolicy;
}

export interface SerialReservationRequest {
  commandId: string;
  materialId: string;
  quantity: number;
  ruleId?: string;
  sourceType: string;
  sourceId?: string | null;
}

export interface SerialReservation {
  reservationId: string;
  commandId: string;
  fingerprint: string;
  identities: DocumentSnItem[];
  status: string;
}

export interface SerialAliasRequest {
  commandId: string;
  sourceType: 'SUPPLIER' | 'CUSTOMER' | 'LEGACY_ERP' | 'LEGACY_MES';
  sourceId?: string;
  code: string;
}

/** PC手工发号固定来源，不能替MES或业务单据创建受控来源身份。 */
export interface ManualSerialReservationRequest extends SerialReservationRequest {
  sourceType: 'ERP_MANUAL';
  sourceId: null;
}

/** 弃用只提交原身份版本和原因，不删除或回收原号码。 */
export interface SerialRetirementRequest {
  commandId: string;
  expectedVersion: string;
  reason: string;
}

export interface SerialRetirement {
  commandId: string;
  identityId: string;
  reservationId: string;
  reservationStatus: string;
  status: 'RETIRED';
  version: string;
  operationId: string;
}

/** ERP本地提交与跨模块ACK分别由原过账凭据和owner outbox证明。 */
export interface SubmissionMetadata {
  submissionState: 'SERVER_COMMITTED' | 'CROSS_MODULE_PENDING' | 'CONFIRMED';
  commandId: string;
  postingGroupIds: string[];
  submissionWarnings?: string[];
}
