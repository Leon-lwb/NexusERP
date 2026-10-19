import type { BatchTraceResult, BatchTracePageQuery, BatchTraceSectionName, TraceModule } from '@/api/erp/wm/trace/types';
import type { BatchRecallResult, IdentityTracePageQuery, IdentityTraceResult, TraceSectionName, TraceSectionPage } from '@/api/erp/wm/trace/types';

/** 翻页只替换指定模块的指定分节，其它模块及证据必须保留。 */
export function mergeIdentitySection(
  current: IdentityTraceResult,
  query: Pick<IdentityTracePageQuery, 'module' | 'section'>,
  response: IdentityTraceResult
): IdentityTraceResult {
  if (current.identityId !== response.identityId) throw new Error('追溯分页身份已变化，拒绝覆盖当前结果');
  const base = current.parts.filter((part) => part.module === query.module);
  const incoming = response.parts.filter((part) => part.module === query.module);
  if (base.length !== 1 || incoming.length !== 1 || !query.section || !incoming[0].sections[query.section])
    throw new Error('响应缺少明确授权模块或分节，不能采用其它结果');
  if (incoming[0].identityId !== current.identityId) throw new Error('模块正式身份不一致');
  return {
    ...current,
    parts: current.parts.map((part) =>
      part.module !== query.module
        ? part
        : {
            ...part,
            // 显式null撤回旧正式码，省略字段兼容旧响应；本地原码不参与身份匹配。
            ...('canonicalSnCode' in incoming[0] ? { canonicalSnCode: incoming[0].canonicalSnCode } : {}),
            ...('localSnCode' in incoming[0] ? { localSnCode: incoming[0].localSnCode } : {}),
            evidenceGaps: incoming[0].evidenceGaps ?? part.evidenceGaps,
            sections: { ...part.sections, [query.section!]: incoming[0].sections[query.section!] }
          }
    )
  };
}

/** 恢复已缓存页时也只替换指定分节，不访问服务端或变更游标。 */
export function replaceIdentityPage(
  current: IdentityTraceResult,
  module: 'ERP' | 'MES',
  section: TraceSectionName,
  page: TraceSectionPage
): IdentityTraceResult {
  return mergeIdentitySection(
    current,
    { module, section },
    { ...current, parts: current.parts.filter((part) => part.module === module).map((part) => ({ ...part, sections: { [section]: page } })) }
  );
}

/** 只使用服务端提供的非空字符串游标，不推算下一页，也不 Number 化。 */
export function pageCursor(page: { hasMore?: boolean; nextCursor?: string | null }): string | undefined {
  if (!page.hasMore) return undefined;
  if (typeof page.nextCursor !== 'string' || !page.nextCursor) throw new Error('服务端声明有后续页但未提供有效游标');
  return page.nextCursor;
}

/** 表格原样显示精确数值文本，嵌套证据序列化为可阅读文本。 */
export function traceCell(value: unknown): string {
  if (value === null || value === undefined) return '-';
  return typeof value === 'object' ? JSON.stringify(value) : String(value);
}

/** 召回分页与深度/容量截断分别说明，不把当前页面宣称为完整召回。 */
export function recallBoundary(result: Partial<BatchRecallResult>): string {
  const notices: string[] = [];
  if (result.truncatedByDepth) notices.push('达到跨层深度上限，召回结果按深度截断');
  if (result.truncatedByCapacity) notices.push('达到服务端容量上限，召回结果按容量截断');
  if (result.truncated && !notices.length) notices.push('服务端声明当前召回已截断');
  if (result.hasMore) notices.push('正式身份还有后续游标页，请继续分页核对');
  if (result.scopeImpacts?.hasMore) notices.push('工单范围还有后续游标页，请独立分页核对');
  if (result.batchImpacts?.hasMore) notices.push('受影响批次还有后续游标页，请独立分页核对');
  return notices.join('；') || '当前页没有后续游标，实际覆盖范围以证据缺口为准';
}

/** 可能影响与未知范围保留警示，禁止把粗粒度推演升级为逐件消耗证明。 */
export function recallPrecision(scope: string | null | undefined): { label: string; type: 'success' | 'warning' } {
  if (scope === undefined) return { label: '逐件确认', type: 'success' };
  if (scope === 'BATCH_POSSIBLE') return { label: '批次可能影响', type: 'warning' };
  if (scope === 'WORK_ORDER_POSSIBLE') return { label: '工单可能影响', type: 'warning' };
  return { label: '范围未确认', type: 'warning' };
}

/** 批次分节只替换该模块的该页，保留其它模块、来源及客户去向。 */
export function mergeBatchSection(
  current: BatchTraceResult,
  query: Pick<BatchTracePageQuery, 'module' | 'section'>,
  response: BatchTraceResult
): BatchTraceResult {
  if (current.batchId !== response.batchId) throw new Error('批次分页身份已变化，拒绝覆盖当前结果');
  const base = current.parts.filter((part) => part.module === query.module);
  const incoming = response.parts.filter((part) => part.module === query.module);
  if (base.length !== 1 || incoming.length !== 1 || !query.section || !incoming[0].sections[query.section])
    throw new Error('响应缺少明确授权模块或分节，不能采用其它结果');
  if (incoming[0].batchId !== current.batchId) throw new Error('模块批次身份不一致');
  return {
    ...current,
    parts: current.parts.map((part) =>
      part.module !== query.module
        ? part
        : {
            ...part,
            evidenceGaps: incoming[0].evidenceGaps ?? part.evidenceGaps,
            sections: { ...part.sections, [query.section!]: incoming[0].sections[query.section!] }
          }
    )
  };
}

/** 批次缓存返回也仅替换原授权模块分节，不重新发请求。 */
export function replaceBatchPage(
  current: BatchTraceResult,
  module: TraceModule,
  section: BatchTraceSectionName,
  page: TraceSectionPage
): BatchTraceResult {
  return mergeBatchSection(
    current,
    { module, section },
    { ...current, parts: current.parts.filter((part) => part.module === module).map((part) => ({ ...part, sections: { [section]: page } })) }
  );
}
