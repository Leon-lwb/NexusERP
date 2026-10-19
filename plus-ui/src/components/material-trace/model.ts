import type { ProductOutputCommand, ProductOutputContext, ProductOutputLine } from '@/api/mes/pro/completion';
import type { CodeResolution, DocumentSnItem, ResolvedMaterialObject, TrackingPolicy } from '@/api/erp/wm/identity/types';

/** 沿用现有业务行，只扩展追溯字段；下划线字段仅作前端交互状态。 */
export interface TraceRow {
  [key: string]: any;
  materialId?: string | number;
  materialCode?: string;
  batchId?: string | number;
  batchCode?: string;
  detailVersion?: string;
  submissionSnapshot?: import('@/api/erp/wm/identity/types').SubmissionMetadata;
  policy?: TrackingPolicy;
  snItems?: DocumentSnItem[];
  _traceError?: string;
  _traceResolving?: boolean;
}

/** 雪花身份不得经 Number 转换；只兼容旧表单中仍然精确的小整数。 */
export function identityText(value: unknown): string | undefined {
  if (value === undefined || value === null || value === '') return undefined;
  if (typeof value === 'string') return value;
  if (typeof value === 'number' && Number.isSafeInteger(value)) return String(value);
  throw new Error('身份必须是完整字符串，请重新加载详情');
}

/** OCR 对象携带全部识别证据，让服务端完成一致性解析。 */
export function rawCodeText(value: string | object): string {
  return typeof value === 'string' ? value.trim() : JSON.stringify(value);
}

/** 四种管理组合独立于组件混批策略。 */
export function policyCombination(policy: Pick<TrackingPolicy, 'batchManaged' | 'serialManaged'>): string {
  if (policy.batchManaged && policy.serialManaged) return '批次 + 序列号管理';
  if (policy.serialManaged) return '序列号管理';
  return policy.batchManaged ? '批次管理' : '数量管理';
}

/** 将主数据策略转为统一显示契约，旧记录不擅自升级 STRICT。 */
export function materialPolicy(material: TraceRow): TrackingPolicy {
  if (material.policy) return material.policy;
  return {
    batchManaged: material.isBatchManage === 1 || material.isBatchManage === '1' || material.isBatchManage === true,
    serialManaged: material.isSerialManage === 1 || material.isSerialManage === '1' || material.isSerialManage === true,
    allowMixedLot: material.allowMixedLot !== false && material.allowMixedLot !== 0 && material.allowMixedLot !== '0',
    version: identityText(material.trackingVersion) ?? '',
    stage: material.trackingStage === 'STRICT' ? 'STRICT' : 'AUDIT'
  };
}

/** 只接受服务端解析成功的完整对象；本地 MES snId 不是正式实物身份。 */
export function resolvedObject(resolution: CodeResolution): ResolvedMaterialObject {
  if (resolution.status !== 'RESOLVED') throw new Error(resolution.reason || '解析未完成，请明确选择候选后继续');
  if (!resolution.objectType) throw new Error('解析对象不完整');
  const object = { ...resolution } as ResolvedMaterialObject;
  for (const key of ['identityId', 'localSnId', 'materialId', 'batchId', 'objectId'] as const) object[key] = identityText(object[key]);
  if (object.objectType === 'DOCUMENT' && (!object.objectId || !object.documentType || !object.documentCode)) throw new Error('单据身份不完整');
  if (object.objectType !== 'DOCUMENT' && (!object.materialId || !object.materialCode)) throw new Error('物料身份不完整');
  if (object.objectType === 'SN' && (!object.identityId || !(object.canonicalSnCode || object.snCode))) throw new Error('实物身份不完整');
  if ((object.objectType === 'BATCH' || (object.objectType === 'SN' && object.policy?.batchManaged)) && !object.batchId) {
    throw new Error('批次身份不完整');
  }
  return object;
}

/** 原子绑定同一物料对象，保留原来源单头、行身份与详情版本，不部分替换编码。 */
export function bindResolvedObject(row: TraceRow, resolution: CodeResolution): TraceRow {
  const object = resolvedObject(resolution);
  if (object.objectType === 'DOCUMENT') throw new Error('单据对象不能绑定到物料行');
  if (row.materialId && identityText(row.materialId) !== object.materialId) throw new Error('识别物料与当前来源行不一致');
  if (row.materialCode && row.materialCode !== object.materialCode) throw new Error('物料编码与当前来源行不一致');
  if (row.batchId && object.batchId && identityText(row.batchId) !== object.batchId) throw new Error('识别批次与来源行分配批次不一致');
  if (row.batchCode && object.batchCode && row.batchCode !== object.batchCode) throw new Error('识别批次与来源行分配批次不一致');
  return {
    ...row,
    materialId: object.materialId,
    materialCode: object.materialCode,
    ...(object.materialName ? { materialName: object.materialName } : {}),
    ...(object.batchId ? { batchId: object.batchId, batchCode: object.batchCode } : {}),
    ...(object.objectType === 'SN' ? { identityId: object.identityId, snCode: object.canonicalSnCode || object.snCode } : {}),
    ...(object.policy ? { policy: object.policy } : {})
  };
}

/** 库存集合按来源行批次约束选择；allowMixedLot 仅用于组件消耗。 */
export function appendIdentity(items: DocumentSnItem[], resolution: CodeResolution, row: TraceRow): DocumentSnItem[] {
  const object = resolvedObject(resolution);
  if (object.objectType !== 'SN') throw new Error('请选择正式 SN 实物对象');
  bindResolvedObject(row, resolution);
  const snCode = object.canonicalSnCode || object.snCode;
  if (items.some((item) => item.identityId === object.identityId || item.snCode === snCode)) throw new Error('该实物已选择，别名不能重复计件');
  return [
    ...items,
    {
      identityId: object.identityId!,
      snCode,
      materialId: object.materialId,
      batchId: object.batchId,
      batchCode: object.batchCode,
      sourceStockId: identityText(row.sourceStockId ?? row.stockId),
      targetLocationId: identityText(row.targetLocationId ?? row.toLocationId ?? row.locationId),
      originalTxId: identityText(row.originalTxId)
    }
  ];
}

/** 严格读取正整数数量，使用 BigInt 比较以避免大数和浮点误差。 */
export function integerQuantity(value: unknown): bigint | undefined {
  const text = String(value ?? '').trim();
  if (!/^\d+(?:\.0+)?$/.test(text)) return undefined;
  return BigInt(text.split('.')[0]);
}

/** 库存行的实物数量及已分配来源约束必须匹配；不按组件混批策略限制成品集合。 */
export function inventoryLineError(row: TraceRow, quantityKey = 'quantity'): string {
  if (row._traceResolving) return '正在解析条码，请稍后重试';
  if (row._traceError) return row._traceError;
  if (!row.materialId) return '请先选择物料';
  const items = row.snItems || [];
  if (!row.policy) return '物料追溯策略尚未加载，请重新查询';
  if (!row.policy.serialManaged) return items.length ? '该物料未启用序列管理，请移除实物清单' : '';
  const quantityText = String(row[quantityKey] ?? '');
  const quantity = integerQuantity(quantityKey === 'differenceQuantity' ? quantityText.replace(/^-/, '') : quantityText);
  if (quantity === undefined) return '序列管理物料数量必须为整数';
  if (quantity !== BigInt(items.length)) return `实物数量 ${items.length} 与单据数量 ${String(row[quantityKey] ?? '')} 不一致`;
  const seen = new Set<string>();
  for (const item of items) {
    if (!item.identityId || seen.has(item.identityId)) return '实物身份缺失或重复';
    seen.add(item.identityId);
    if (item.materialId && identityText(row.materialId) !== item.materialId) return '实物物料与来源行不一致';
    if (row.batchId && identityText(row.batchId) !== item.batchId) return '实物批次与来源行分配批次不一致';
    if (row.batchCode && item.batchCode && row.batchCode !== item.batchCode) return '实物批次与来源行分配批次不一致';
    const stockId = identityText(row.sourceStockId ?? row.stockId);
    if (stockId && stockId !== item.sourceStockId) return '来源库存已变化，请重新选择实物';
    const targetId = identityText(row.targetLocationId ?? row.toLocationId ?? row.locationId);
    if (targetId && targetId !== item.targetLocationId) return '目标库位已变化，请重新选择实物';
  }
  return '';
}

/** 请求指纹只使用业务数据，下划线交互状态和命令号不影响重放。 */
function stablePayload(value: any): any {
  if (Array.isArray(value)) return value.map(stablePayload);
  if (value && typeof value === 'object')
    return Object.fromEntries(
      Object.keys(value)
        .sort()
        .filter((key) => !key.startsWith('_trace') && key !== 'commandId')
        .map((key) => [key, stablePayload(value[key])])
    );
  return value;
}

/** 每次有意改变命令内容产生新号，同内容超时重试沿用同一号。 */
export function createCommandSession(next = () => globalThis.crypto.randomUUID()) {
  const commands = new Map<string, string>();
  return {
    forPayload(payload: unknown): string {
      const key = JSON.stringify(stablePayload(payload));
      if (!commands.has(key)) commands.set(key, next());
      return commands.get(key)!;
    }
  };
}

/** 清理纯交互字段，保留全部源业务字段；ID、数量与金额按字符串发送。 */
export function prepareTraceDocument<T extends TraceRow>(header: T, commandId: string): T & { commandId: string } {
  const copy = (value: any, key = ''): any => {
    if (Array.isArray(value)) return value.map((item) => copy(item));
    if (value && typeof value === 'object')
      return Object.fromEntries(
        Object.entries(value)
          .filter(([name]) => !name.startsWith('_trace'))
          .map(([name, item]) => [name, copy(item, name)])
      );
    if (value !== undefined && value !== null && value !== '' && /(?:Id|Version)$/.test(key)) return identityText(value);
    if (typeof value === 'number' && /(?:quantity|qty|amount|price|cost)$/i.test(key)) return String(value);
    return value;
  };
  return { ...copy(header), commandId };
}

/** 原执行端点使用原详情版本与源单头；没有服务端版本时阻断，不发明版本。 */
export function postingBody(header: TraceRow, rows: TraceRow[], quantityKey: string, commandId: string): TraceRow {
  const headerVersion = typeof header.detailVersion === 'string' && !!header.detailVersion;
  const rowVersions = rows.length > 0 && rows.every((row) => typeof row.detailVersion === 'string' && !!row.detailVersion);
  if (!headerVersion && !rowVersions) throw new Error('缺少服务端详情版本，请重新加载单据');
  const seen = new Set<string>();
  for (const row of rows) {
    const error = inventoryLineError(row, quantityKey);
    if (error) throw new Error(error);
    for (const item of row.snItems || []) {
      if (seen.has(item.identityId)) throw new Error('同一实物不能出现在多行');
      seen.add(item.identityId);
    }
  }
  return prepareTraceDocument({ ...header, snItems: rows.flatMap((row) => row.snItems || []) }, commandId);
}

export interface ConsumptionEntry {
  productIdentityId: string;
  componentMaterialId: string;
  lotId?: string;
  erpBatchId?: string;
  componentIdentityId?: string;
  quantity: string;
  allowMixedLot?: boolean;
  [key: string]: unknown;
}

/** 同一产品实物、同一组件物料的批次锁覆盖已有分录，数量仅验证文本不重算。 */
export function consumptionError(entries: ConsumptionEntry[], allowMixedLot: boolean, prior: ConsumptionEntry[] = []): string {
  if (!entries.length) return '请添加组件消耗明细';
  const lots = new Map<string, Set<string>>();
  for (const entry of [...prior, ...entries]) {
    if (!entry.productIdentityId || !entry.componentMaterialId || !(entry.lotId || entry.erpBatchId || entry.componentIdentityId))
      return '组件身份或批次证据不完整';
    if (!/^\d+(?:\.\d+)?$/.test(entry.quantity) || !/[1-9]/.test(entry.quantity)) return '组件数量必须是正的十进制字符串';
    const key = `${entry.productIdentityId}\u0000${entry.componentMaterialId}`;
    const batch = entry.lotId || entry.erpBatchId;
    if (batch) {
      const values = lots.get(key) || new Set<string>();
      values.add(batch);
      lots.set(key, values);
    }
  }
  for (const entry of entries) {
    if (!(entry.allowMixedLot ?? allowMixedLot) && (lots.get(`${entry.productIdentityId}\u0000${entry.componentMaterialId}`)?.size || 0) > 1)
      return '该组件禁止在同一产品 SN 内混批，请核对已有消耗';
  }
  return '';
}

/** 仅展示服务端声明的追溯范围，缺省不宣称实物级追溯。 */
export function traceScopeText(result: TraceRow): string {
  const scope = result.traceScope ?? result.scope;
  const labels: Record<string, string> = {
    SN: '实物 SN 范围',
    IDENTITY: '实物 SN 范围',
    BATCH: '批次范围',
    BATCH_COMPONENT_RECALL: '组件批次召回范围',
    MATERIAL: '物料范围',
    DOCUMENT: '单据范围',
    LEGACY: '历史汇总范围'
  };
  return labels[scope] || (scope ? `服务端范围：${scope}` : '服务端未声明追溯范围，未提供完整实物证明');
}

/** 下载服务端已审阅的不可变清单，不在浏览器构造修复或实时执行脚本。 */
export function reviewedManifest(run: TraceRow): unknown {
  if (run.status !== 'REVIEWED' || !run.manifestDigest || !run.reviewedManifest) throw new Error('只有已审阅且带摘要的清单允许下载');
  return run.reviewedManifest;
}

/** 正十进制字符串比较，库存余额和逐批消耗不经浮点数处理。 */
export function decimalCompare(left: string, right: string): -1 | 0 | 1 {
  if (![left, right].every((value) => /^\d+(?:\.\d+)?$/.test(value))) throw new Error('数量必须为十进制字符串');
  const [li, lf = ''] = left.split('.');
  const [ri, rf = ''] = right.split('.');
  const scale = Math.max(lf.length, rf.length);
  const l = BigInt(li + lf.padEnd(scale, '0'));
  const r = BigInt(ri + rf.padEnd(scale, '0'));
  return l === r ? 0 : l > r ? 1 : -1;
}

/** 批次异步生成只能绑定到发起时仍未变化的上架行，避免覆盖后到的 OCR 结果。 */
export function generatedBatchBinding(
  current: TraceRow,
  original: TraceRow,
  batch: { batchId?: string | number; batchCode?: string }
): TraceRow | undefined {
  for (const key of ['materialId', 'lineId', 'batchId', 'batchCode', 'identityId', 'snCode']) {
    if (current[key] !== original[key]) return undefined;
  }
  if (current.snItems?.length || !batch.batchId || !batch.batchCode) return undefined;
  return { ...current, batchId: identityText(batch.batchId), batchCode: batch.batchCode };
}

/** 精确累加非负十进制文本，不通过浮点数聚合产品良品和报废数量。 */
export function decimalSum(values: string[]): string {
  if (!values.every((value) => typeof value === 'string' && /^\d+(?:\.\d+)?$/.test(value))) throw new Error('数量必须是十进制字符串');
  const scale = Math.max(0, ...values.map((value) => (value.split('.')[1] || '').length));
  const total = values
    .reduce((sum, value) => {
      const [whole, fraction = ''] = value.split('.');
      return sum + BigInt(whole + fraction.padEnd(scale, '0'));
    }, 0n)
    .toString()
    .padStart(scale + 1, '0');
  if (!scale) return total;
  const fraction = total.slice(-scale).replace(/0+$/, '');
  return total.slice(0, -scale) + (fraction ? '.' + fraction : '');
}

/** batch-only末工序良品和各工序报废都需要实际lot，普通数量/SN模式沿用原流程。 */
export function productOutputRequirement(
  policy: Pick<TrackingPolicy, 'batchManaged' | 'serialManaged'>,
  finalStep: boolean,
  goodQty: string,
  scrapQty: string
) {
  decimalSum([goodQty, scrapQty]);
  const batchOnly = policy.batchManaged && !policy.serialManaged;
  const actualGood = batchOnly && finalStep ? goodQty : '0';
  const actualScrap = batchOnly ? scrapQty : '0';
  return { required: decimalCompare(decimalSum([actualGood, actualScrap]), '0') > 0, goodQty: actualGood, scrapQty: actualScrap };
}

/** 整lot来源与品质必须可核对；不能将同lot拆成两种分类或套用组件禁止混批。 */
export function validateProductOutputs(
  lines: ProductOutputLine[],
  context: ProductOutputContext,
  expected: { goodQty: string; scrapQty: string }
): void {
  if (!context.policy.batchManaged || context.policy.serialManaged) throw new Error('只有批次管理且不管理SN的产品使用实际lot产出');
  if (!lines.length) throw new Error('请选择实际产品批次');
  const seen = new Set<string>();
  for (const line of lines) {
    if (!line.lotId || !line.sourceAllocationId || seen.has(line.lotId)) throw new Error('实际批次来源缺失或重复，整批只能选择一种品质');
    seen.add(line.lotId);
    const sources = context.sources.filter((source) => source.lotId === line.lotId && source.sourceAllocationId === line.sourceAllocationId);
    if (sources.length !== 1) throw new Error('产品批次缺少唯一原生产分配，不能猜测来源');
    const source = sources[0];
    if (source.materialId !== context.materialId || (source.mesWoId && source.mesWoId !== context.mesWoId))
      throw new Error('批次物料或工单与当前实际生产不一致');
    if (context.outputs.some((output) => output.lotId === line.lotId)) throw new Error('该产品批次已登记产出，不能重复登记');
    if (typeof line.quantity !== 'string' || decimalCompare(line.quantity, '0') <= 0 || decimalCompare(line.quantity, source.quantity) !== 0)
      throw new Error('实际产出必须使用整lot的原数量，不可拆分或超出来源');
    if (line.result === 'GOOD') {
      if (!context.isFinalStep) throw new Error('只有实际末工序可以登记良品产出');
      if (source.status !== 'IN_STOCK' || source.qcStatus !== 'PASS') throw new Error('良品必须选择已检验合格的实际产品批次');
    } else if (line.result === 'SCRAP') {
      if (source.status !== 'SCRAPPED' || !['PASS', 'FAIL'].includes(source.qcStatus))
        throw new Error('报废必须选择已实际报废且品质已判定的产品批次');
    } else throw new Error('产出品质只能是良品或报废');
  }
  const sum = (result: 'GOOD' | 'SCRAP') => decimalSum(lines.filter((line) => line.result === result).map((line) => line.quantity));
  if (decimalCompare(sum('GOOD'), expected.goodQty) !== 0 || decimalCompare(sum('SCRAP'), expected.scrapQty) !== 0)
    throw new Error('实际产品批次良品/报废数量与本次原报工不守恒');
}

/** 缺少跨库凭据时保留待确认提示；旧普通数量响应沿用原成功文案。 */
export function submissionNotice(response: any, fallback = '操作已提交') {
  const value = response?.data ?? response;
  const state = value?.submissionState;
  const warnings: string[] = Array.isArray(value?.submissionWarnings)
    ? value.submissionWarnings.filter((item: unknown) => typeof item === 'string')
    : [];
  const message =
    state === 'CONFIRMED'
      ? '服务端已提交，跨模块已确认'
      : state === 'SERVER_COMMITTED'
        ? '服务端已提交，跨模块确认待核查'
        : state === 'CROSS_MODULE_PENDING'
          ? '服务端已提交，跨模块待确认'
          : state
            ? '提交状态待核查，请刷新服务端详情'
            : fallback;
  return {
    type: (state && state !== 'CONFIRMED' ? 'warning' : 'success') as 'warning' | 'success',
    message: message + (warnings.length ? '；' + warnings.join('；') : ''),
    commandId: value?.commandId,
    postingGroupIds: Array.isArray(value?.postingGroupIds) ? value.postingGroupIds : [],
    warnings
  };
}

/** 原报工为总量时只补未登记产出，拒绝以较小报工覆盖既有实际产出。 */
export function remainingProductOutputs(context: Pick<ProductOutputContext, 'outputs'>, expected: { goodQty: string; scrapQty: string }) {
  const subtract = (total: string, used: string) => {
    if (decimalCompare(total, used) < 0) throw new Error('已登记实际产出超出本次报工，请核对原报工或走审计修订');
    const scale = Math.max((total.split('.')[1] || '').length, (used.split('.')[1] || '').length);
    const integer = (value: string) => {
      const [whole, fraction = ''] = value.split('.');
      return BigInt(whole + fraction.padEnd(scale, '0'));
    };
    const value = (integer(total) - integer(used)).toString().padStart(scale + 1, '0');
    return scale ? decimalSum([value.slice(0, -scale) + '.' + value.slice(-scale)]) : value;
  };
  const used = (result: 'GOOD' | 'SCRAP') => decimalSum(context.outputs.filter((row) => row.result === result).map((row) => row.quantity));
  return { goodQty: subtract(expected.goodQty, used('GOOD')), scrapQty: subtract(expected.scrapQty, used('SCRAP')) };
}

/** 单次产出尝试保留不可变原载荷；保存成功后只重试原完成动作。 */
export function createProductOutputAttempt(command: ProductOutputCommand, record: (command: ProductOutputCommand) => Promise<{ data: string[] }>) {
  const original = structuredClone(command);
  let saved = false;
  return {
    get saved() {
      return saved;
    },
    async submit<T>(finish: () => Promise<T>): Promise<T> {
      if (!saved) {
        const response = await record(structuredClone(original));
        if (
          !Array.isArray(response.data) ||
          response.data.length !== original.lines.length ||
          response.data.some((id) => typeof id !== 'string' || !id)
        )
          throw new Error('实际产出响应缺少完整字符串身份，请重试原命令核验');
        saved = true;
      }
      return finish();
    }
  };
}

/** 精确相减非负十进制文本；冲销与剩余量不经浮点数。 */
export function decimalSubtract(total: string, used: string): string {
  if (decimalCompare(total, used) < 0) throw new Error('冲销数量超过原实际消耗净额');
  const scale = Math.max((total.split('.')[1] || '').length, (used.split('.')[1] || '').length);
  const integer = (value: string) => {
    const [whole, fraction = ''] = value.split('.');
    return BigInt(whole + fraction.padEnd(scale, '0'));
  };
  const value = (integer(total) - integer(used)).toString().padStart(scale + 1, '0');
  return scale ? decimalSum([value.slice(0, -scale) + '.' + value.slice(-scale)]) : value;
}
export interface NetComponentEntry {
  entryId: string;
  componentMaterialId: string;
  lotId: string;
  remainingQuantity: string;
}

/** 原trace返回不可变CONSUME/REVERSE，先扣历史冲销后才允许继续冲销。 */
export function netComponentEntries(rows: Record<string, any>[]): NetComponentEntry[] {
  if (!Array.isArray(rows)) throw new Error('服务端未提供单件不可变消耗明细');
  const original = new Map<string, NetComponentEntry>();
  const seen = new Set<string>();
  for (const row of rows) {
    const id = identityText(row.entryId ?? row.entry_id);
    if (!id || seen.has(id)) throw new Error('原分录缺失或重复');
    seen.add(id);
    const quantity = row.quantity;
    if (typeof quantity !== 'string' || decimalCompare(quantity, '0') <= 0) throw new Error('原分录数量缺少精确证据');
    const kind = row.entryType ?? row.entry_type;
    if (kind === 'CONSUME') {
      const material = identityText(row.componentMaterialId ?? row.component_material_id),
        lot = identityText(row.componentLotId ?? row.component_lot_id);
      if (!material || !lot) throw new Error('原分录组件/批次存在历史缺口');
      original.set(id, { entryId: id, componentMaterialId: material, lotId: lot, remainingQuantity: quantity });
    } else if (kind !== 'REVERSE') throw new Error('原分录方向存在历史缺口');
  }
  for (const row of rows)
    if ((row.entryType ?? row.entry_type) === 'REVERSE') {
      const old = original.get(identityText(row.originalEntryId ?? row.original_entry_id) || '');
      if (!old) throw new Error('历史冲销缺少原分录，不能推定净额');
      old.remainingQuantity = decimalSubtract(old.remainingQuantity, row.quantity);
    }
  return [...original.values()];
}
export interface ComponentCorrectionContext {
  manifest: Pick<import('@/api/mes/pro/completion').CompletionManifestHeader, 'manifestId' | 'mesWoId' | 'revision' | 'status'>;
  sn: {
    snId: string | number;
    identityId?: string;
    identityVersion?: string;
    mesWoId: string | number;
    cardId?: string | number;
    snStatus: string;
    handoffStatus?: string;
  };
  entries: Record<string, any>[];
  sources: Array<{
    consumptionId: string | number;
    woId: string | number;
    materialId: string | number;
    lotId: string | number;
    consumedQty: string | number;
  }>;
  steps: Array<{ stepRecordId: string | number; stepStatus: string }>;
  policy?: TrackingPolicy;
}

/** 只从原清单、原单件、真实消耗和已完成工序构造修订，技术ID不能由用户自由填写。 */
export function buildComponentCorrection(
  draft: Pick<
    import('@/api/mes/pro/completion').ComponentCorrectionRequest,
    'commandId' | 'componentMaterialId' | 'reason' | 'reversals' | 'allocations'
  >,
  context: ComponentCorrectionContext
): import('@/api/mes/pro/completion').ComponentCorrectionRequest {
  const { manifest, sn } = context;
  if (manifest.status !== 'CANCELLED' || !manifest.manifestId || !String(manifest.revision).match(/^[1-9]\d*$/))
    throw new Error('仅服务端确认取消的原清单可以修订');
  if (identityText(sn.mesWoId) !== manifest.mesWoId) throw new Error('所选单件不属于原取消清单工单');
  if (!sn.identityId || !sn.cardId || !['COMPLETED', 'SCRAPPED'].includes(sn.snStatus) || sn.handoffStatus !== 'NONE')
    throw new Error('单件状态或移交状态不允许修订');
  if (typeof sn.identityVersion !== 'string' || !sn.identityVersion.match(/^[1-9]\d*$/)) throw new Error('服务端未提供单件原字符串版本');
  if (!draft.reason?.trim() || draft.reason.length > 500) throw new Error('修订原因必填且不能超过500字');
  if (!draft.commandId || !draft.componentMaterialId || (!draft.reversals.length && !draft.allocations.length))
    throw new Error('请选择组件及原冲销/新分配明细');
  for (const row of context.entries) {
    if (identityText(row.localSnId ?? row.local_sn_id) !== identityText(sn.snId) || identityText(row.identityId ?? row.identity_id) !== sn.identityId)
      throw new Error('原消耗分录属于其他单件，不能拼接修订');
  }
  const net = netComponentEntries(context.entries);
  const reversed = new Map<string, string>();
  for (const row of draft.reversals) {
    const old = net.find((entry) => entry.entryId === row.originalEntryId && entry.componentMaterialId === draft.componentMaterialId);
    if (!old || reversed.has(row.originalEntryId)) throw new Error('原冲销分录不属于所选组件或重复');
    if (typeof row.quantity !== 'string' || decimalCompare(row.quantity, '0') <= 0 || decimalCompare(row.quantity, old.remainingQuantity) > 0)
      throw new Error('冲销不能超过原实际消耗净额');
    reversed.set(row.originalEntryId, row.quantity);
  }
  const allocated = new Map<string, string>();
  for (const row of draft.allocations) {
    const candidates = context.sources.filter(
      (source) =>
        identityText(source.consumptionId) === row.consumptionId &&
        identityText(source.woId) === manifest.mesWoId &&
        identityText(source.materialId) === draft.componentMaterialId
    );
    if (candidates.length !== 1 || typeof row.quantity !== 'string' || decimalCompare(row.quantity, '0') <= 0)
      throw new Error('新分配必须选择本工单组件的真实实际消耗');
    if (
      !context.steps.some((step) => identityText(step.stepRecordId) === row.stepRecordId && step.stepStatus === 'COMPLETED') ||
      !row.operationRef?.trim()
    )
      throw new Error('新分配必须选择该单件实际已完成工序');
    const used = decimalSum([allocated.get(row.consumptionId) || '0', row.quantity]);
    if (decimalCompare(used, String(candidates[0].consumedQty)) > 0) throw new Error('新分配超过原实际消耗数量上限');
    allocated.set(row.consumptionId, used);
  }
  if (context.policy) {
    const projected: ConsumptionEntry[] = net
      .filter((row) => row.componentMaterialId === draft.componentMaterialId)
      .map((row) => ({
        ...row,
        quantity: decimalSubtract(row.remainingQuantity, reversed.get(row.entryId) || '0'),
        productIdentityId: sn.identityId!,
        componentMaterialId: row.componentMaterialId
      }))
      .filter((row) => decimalCompare(row.quantity, '0') > 0);
    for (const row of draft.allocations) {
      const source = context.sources.find((source) => identityText(source.consumptionId) === row.consumptionId)!;
      projected.push({
        productIdentityId: sn.identityId,
        componentMaterialId: draft.componentMaterialId,
        lotId: identityText(source.lotId),
        quantity: row.quantity
      });
    }
    if (projected.length) {
      const problem = consumptionError(projected, context.policy.allowMixedLot);
      if (problem) throw new Error(problem);
    }
  }
  return {
    commandId: draft.commandId,
    manifestId: manifest.manifestId,
    cancelledRevision: manifest.revision,
    snId: identityText(sn.snId)!,
    componentMaterialId: draft.componentMaterialId,
    expectedVersion: sn.identityVersion,
    reversals: draft.reversals.map((row) => ({ ...row })),
    allocations: draft.allocations.map((row) => ({ ...row })),
    reason: draft.reason
  };
}
