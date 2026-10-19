import request from '@/utils/request';
import type { AxiosPromise } from 'axios';
import type { TrackingPolicy } from '@/api/erp/wm/identity/types';

/** 实际产出只使用原生产分配身份，客户端不输入技术事件ID。 */
export interface ProductOutputLine {
  lotId: string;
  sourceAllocationId: string;
  quantity: string;
  result: 'GOOD' | 'SCRAP';
}
export interface ProductOutputCommand {
  commandId: string;
  cardId: string;
  stepRecordId: string;
  lines: ProductOutputLine[];
}
export interface ProductLotSource {
  lotId: string;
  lotCode: string;
  sourceAllocationId: string;
  materialId: string;
  mesWoId?: string;
  erpBatchId?: string;
  batchCode?: string;
  quantity: string;
  status: string;
  qcStatus: string;
}
export interface ProductOutputRecord extends ProductOutputLine {
  outputId: string;
  commandId: string;
}
export interface ProductOutputContext {
  cardId: string;
  stepRecordId: string;
  mesWoId: string;
  materialId: string;
  isFinalStep: boolean;
  policy: TrackingPolicy;
  sources: ProductLotSource[];
  outputs: ProductOutputRecord[];
  hasMore?: boolean;
  nextCursor?: string | null;
}
export interface CompletionManifestHeader {
  manifestId: string;
  revision: number | string;
  mesWoId: string;
  erpWoId: string;
  materialId: string;
  goodQty: string;
  scrapQty: string;
  contentHash: string;
  lineCount: string;
  status: 'FROZEN' | 'APPLIED' | 'RECEIVED' | 'CANCEL_REQUESTED' | 'CANCELLED';
}

/** 先登记真实产品lot，再由原完成URL推进报工；此请求不触发PC入库。 */
export const recordProductOutputs = (data: ProductOutputCommand): AxiosPromise<string[]> =>
  request({ url: '/mes/lt/trace/product-output', method: 'post', data, headers: { repeatSubmit: false } });

/** 只申请取消屏障，HTTP成功不能当作ERP已经确认取消。 */
export function requestCompletionCancellation(manifestId: string, data: { commandId: string; revision: number | string; reason: string }) {
  if (!data.reason?.trim() || data.reason.length > 500) return Promise.reject(new Error('取消原因必填且不能超过500字'));
  return request({
    url: `/mes/pro/completion-manifest/${encodeURIComponent(manifestId)}/request-cancel`,
    method: 'post',
    data,
    headers: { repeatSubmit: false }
  });
}

/** 依据原已取消修订重新冻结，不重置报工或额外调用库存入库。 */
export function refreezeCompletionManifest(
  woId: string,
  data: { commandId: string; cancelledRevision: number | string; reason: string }
): AxiosPromise<CompletionManifestHeader> {
  if (!data.reason?.trim() || data.reason.length > 500) return Promise.reject(new Error('重新冻结原因必填且不能超过500字'));
  return request({
    url: `/mes/pro/completion-manifest/work-order/${encodeURIComponent(woId)}/refreeze`,
    method: 'post',
    data,
    headers: { repeatSubmit: false }
  });
}

/** 既有工单详情可选的审计状态；latest=null明确表示尚无清单。 */
export interface CompletionState {
  latest: CompletionManifestHeader | null;
  revisions: CompletionManifestHeader[];
  cancellation?: { status: string; reason?: string };
  hasMore?: boolean;
  nextCursor?: string | null;
  truncated?: boolean;
}

/** 修订仅追加原分录冲销和真实来源分配，不改写旧分录或ERP库存。 */
export interface ComponentCorrectionRequest {
  commandId: string;
  manifestId: string;
  cancelledRevision: string | number;
  snId: string;
  componentMaterialId: string;
  expectedVersion: string;
  reversals: Array<{ originalEntryId: string; quantity: string }>;
  allocations: Array<{ consumptionId: string; quantity: string; stepRecordId: string; operationRef: string }>;
  reason: string;
}
export interface ComponentCorrectionResult {
  reversalEntryIds: string[];
  allocationEntryIds: string[];
  version: string;
}

/** 原取消修订和原单件版本逐字透传，服务端复核取消ACK与冻结物料策略。 */
export function correctSnComponent(data: ComponentCorrectionRequest): AxiosPromise<ComponentCorrectionResult> {
  if (!data.reason?.trim() || data.reason.length > 500) return Promise.reject(new Error('修订原因必填且不能超过500字'));
  if (typeof data.expectedVersion !== 'string' || !/^[1-9]\d*$/.test(data.expectedVersion))
    return Promise.reject(new Error('修订必须使用服务端原字符串版本'));
  return request({ url: '/mes/lt/trace/sn-consumption/correct', method: 'post', data, headers: { repeatSubmit: false } });
}
