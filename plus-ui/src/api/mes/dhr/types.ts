/**
 * DHR（Device History Record / 批记录）类型定义。
 *
 * <p><b>ID 一律按 string 处理</b>：后端主键为雪花 ID（ASSIGN_ID），超出 JS 安全整数范围，
 * Jackson 已全局序列化为字符串；前端不得对其做数值运算、不得 parseFloat。</p>
 *
 * <p>状态取值（字典 mes_dhr_status）：DRAFT / SUBMITTED / RELEASED / REJECTED / ARCHIVED。<br/>
 * 分册编码（字典 mes_dhr_section）：WO_INFO / PROCESS / MATERIAL / EQUIPMENT / TOOLING /
 * SOP / QC / DATA_COLLECT / SN_TRACE / NCR。</p>
 */
import type { SignatureVO } from '@/api/system/sign/record';

/** DHR 批记录视图对象 */
export interface DhrRecordVo {
  dhrId: string;
  dhrNo?: string;
  workOrderId?: string;
  workOrderNo?: string;
  materialId?: string;
  materialCode?: string;
  materialName?: string;
  lotNo?: string;
  sn?: string;
  plannedQty?: number;
  completedQty?: number;
  scrapQty?: number;
  status?: string;
  /** 冻结时的聚合快照哈希（冻结后不可变，校验以此为准） */
  snapshotHash?: string;
  frozenAt?: string;
  frozenBy?: string;
  submittedAt?: string;
  submittedBy?: string;
  submitSignatureId?: string;
  releasedAt?: string;
  releasedBy?: string;
  releaseSignatureId?: string;
  rejectedAt?: string;
  rejectedBy?: string;
  rejectReason?: string;
  rejectSignatureId?: string;
  archivedAt?: string;
  createTime?: string;
  updateTime?: string;
  /** 分册明细（详情查询时填充；列表通常为空） */
  sections?: DhrSectionVo[];
}

/** DHR 分册视图对象 */
export interface DhrSectionVo {
  sectionId: string;
  dhrId?: string;
  /** 分册编码，取值见 mes_dhr_section 字典 */
  sectionCode?: string;
  sectionName?: string;
  /** 分册内明细条数 */
  itemCount?: number;
  /** 分册内容 JSON（字符串；解析后为对象数组） */
  contentJson?: string;
  /** 分册内容哈希 */
  contentHash?: string;
  sortOrder?: number;
}

/** DHR 校验结果视图对象 */
export interface DhrVerifyVo {
  /** 冻结时记录的快照哈希 */
  snapshotHash?: string;
  /** 按当前数据重算的哈希 */
  recomputedHash?: string;
  /** 两个哈希是否一致 */
  hashMatched?: boolean;
  /** 签名链校验结论（后端下发的文案/布尔语义，原样展示） */
  chainVerify?: string | boolean;
  /** 综合结论 */
  verdict?: string;
}

/** DHR 列表筛选条件 */
export interface DhrRecordQuery extends PageQuery {
  dhrNo?: string;
  workOrderNo?: string;
  materialCode?: string;
  lotNo?: string;
  sn?: string;
  status?: string;
  beginTime?: string;
  endTime?: string;
}

/** DHR 生成（build）入参 */
export interface DhrRecordForm {
  workOrderId: string;
  lotNo?: string;
  sn?: string;
  remark?: string;
}

/** 批记录签署入参（提交复核 / 放行）：凭证只随本次请求体发送 */
export interface DhrSignForm {
  authMethod: string;
  credential: string;
  remark?: string;
}

/** 批记录驳回入参：驳回必须给出原因 */
export interface DhrRejectForm {
  authMethod: string;
  credential: string;
  reason: string;
}

/** DHR 签名记录（复用系统签名链 SignatureVo，字段与后端一致） */
export type DhrSignatureVo = SignatureVO;
