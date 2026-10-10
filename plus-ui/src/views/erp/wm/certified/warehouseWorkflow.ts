import { createBusinessSession, createBusinessIntentStore, normalizeBusinessId, normalizeBusinessVersion } from '@/utils/certified-business';
import type { BusinessScope, CertifiedBusinessSession, CertifiedBusinessResponse as Response } from '@/utils/certified-business';
import type { CertifiedWarehouseContext, CertifiedWarehouseDetail, WarehouseDomain, WarehouseTarget } from '@/api/erp/wm/arrivalnotice/types';

export type WarehouseOperation = 'createNotice' | 'submitNotice' | 'synchronizeNotice' | 'createReceive' | 'shelfReceive' | 'executeReceive';
/** 全部实际写合同字段由人工明确填写，不默认已到/已收数量。 */
export interface WarehouseInput {
  requestKey: string;
  purchaseLineId: string;
  quantity: string;
  noticeName: string;
  arrivalDate: string;
  actualArrivalDate: string;
  warehouseId: string;
  areaId: string;
  locationId: string;
  noticeId?: string;
  noticeLineId?: string;
  remark: string;
  flowId: string;
  materialsConfirmed: boolean;
}
export interface WarehousePorts {
  domain: WarehouseDomain;
  storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;
  readCredential: () => string | null | undefined;
  readScope: () => { tenantId: unknown; userId: unknown; routeKey: string };
  hasPermission: (permission: string) => boolean;
  context: (session: CertifiedBusinessSession) => Promise<Response<CertifiedWarehouseContext>>;
  detail: (kind: WarehouseTarget, id: string, session: CertifiedBusinessSession) => Promise<Response<CertifiedWarehouseDetail>>;
  write: (operation: WarehouseOperation, id: string, body: any, session: CertifiedBusinessSession, flowId?: string) => Promise<Response<any>>;
  bindingContext?: (session: CertifiedBusinessSession) => Promise<Response<CertifiedWarehouseContext>>;
  bindingDetail?: (noticeId: string, session: CertifiedBusinessSession) => Promise<Response<CertifiedWarehouseDetail>>;
  recovery: (id: string, session: CertifiedBusinessSession) => Promise<Response<any>>;
  confirm: (message: string) => Promise<boolean>;
  changed?: (state: WarehouseState) => void;
}
export interface WarehouseState {
  kind: WarehouseTarget;
  documentId: string;
  context: CertifiedWarehouseContext | null;
  detail: CertifiedWarehouseDetail | null;
  busy: boolean;
  reading: boolean;
  blocked: boolean;
  message: string;
  result: unknown;
  recovery: unknown;
}
function safeId(value: unknown): string | null {
  try {
    return normalizeBusinessId(value);
  } catch {
    return null;
  }
}
const operations: Record<
  WarehouseOperation,
  { domain: WarehouseDomain; kind: WarehouseTarget; permission: string; hint: keyof CertifiedWarehouseContext; label: string }
> = {
  createNotice: { domain: 'arrivalnotice', kind: 'ORDER', permission: 'wm:arrivalnotice:add', hint: 'createAllowed', label: '创建认证到货通知' },
  submitNotice: {
    domain: 'arrivalnotice',
    kind: 'NOTICE',
    permission: 'wm:arrivalnotice:submit',
    hint: 'submitAllowed',
    label: '提交实际到货审批流程'
  },
  synchronizeNotice: {
    domain: 'arrivalnotice',
    kind: 'NOTICE',
    permission: 'wm:arrivalnotice:submit',
    hint: 'submitAllowed',
    label: '同步实际通知流程状态'
  },
  createReceive: { domain: 'receive', kind: 'ORDER', permission: 'wm:receive:add', hint: 'createAllowed', label: '创建认证收货草稿（非库存）' },
  shelfReceive: { domain: 'receive', kind: 'RECEIVE', permission: 'wm:receive:shelf', hint: 'shelfAllowed', label: '认证上架（非入库）' },
  executeReceive: { domain: 'receive', kind: 'RECEIVE', permission: 'wm:receive:execute', hint: 'executeAllowed', label: '实际库存过账' }
};
function need(ok: unknown): asserts ok {
  if (!ok) throw new Error('仓储条件不完整');
}
function snapshotKey(value: unknown): string {
  if (Array.isArray(value)) return '[' + value.map(snapshotKey).join(',') + ']';
  if (value && typeof value === 'object')
    return (
      '{' +
      Object.entries(value)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([k, v]) => JSON.stringify(k) + ':' + snapshotKey(v))
        .join(',') +
      '}'
    );
  return JSON.stringify(value);
}
function freezeNative<T>(value: T): T {
  if (value && typeof value === 'object') {
    Object.freeze(value);
    for (const child of Object.values(value)) freezeNative(child);
  }
  return value;
}
function decimalKey(value: unknown): string {
  need(typeof value === 'string' && /^[0-9]+(?:\.[0-9]+)?$/.test(value));
  const [integer, fraction = ''] = value.split('.');
  return (integer.replace(/^0+(?=[0-9])/, '') + '.' + fraction.replace(/0+$/, '')).replace(/\.$/, '');
}
function id(value: unknown): string {
  const normalized = normalizeBusinessId(value);
  need(normalized);
  return normalized;
}
/** 可选来源只允许 null/缺省或精确 ID，空串/损坏值不得伪装成无来源。 */
function optionalId(value: unknown): string | null {
  return value == null ? null : id(value);
}
function quantity(value: unknown): string {
  need(typeof value === 'string' && /^(?:0|[1-9][0-9]{0,13})(?:\.[0-9]{1,4})?$/.test(value) && /[1-9]/.test(value));
  return value;
}
function date(value: unknown): string {
  need(typeof value === 'string' && /^[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}$/.test(value));
  const parsed = new Date(`${value}T00:00:00Z`);
  need(Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value);
  return value;
}
/** 只认可独立读接口给出的明确普通单，绝不读取可编辑 form 的 attr/status 来撤掉保护。 */
export function isLegacyWarehouseDetail(detail: CertifiedWarehouseDetail | null | undefined, domain: WarehouseDomain, documentId: unknown): boolean {
  return (
    !!detail &&
    detail.domain === domain &&
    detail.kind === (domain === 'arrivalnotice' ? 'NOTICE' : 'RECEIVE') &&
    detail.documentId === safeId(documentId) &&
    detail.protection === 'LEGACY'
  );
}
/** 无客户端写资格、无真实数据持久化；唯一持久化内容是共享意图护栏。 */
export function createWarehouseWorkflow(ports: WarehousePorts) {
  const state: WarehouseState = {
    kind: 'ORDER',
    documentId: '',
    context: null,
    detail: null,
    busy: false,
    reading: false,
    blocked: false,
    message: '',
    result: null,
    recovery: null
  };
  const store = createBusinessIntentStore(ports.storage);
  let generation = 0;
  let reviewedSnapshot: CertifiedWarehouseDetail | null = null;
  const publish = () => ports.changed?.({ ...state });
  function capture() {
    const revision = generation;
    const documentId = id(state.documentId);
    const kind = state.kind;
    const actor = ports.readScope();
    const tenantId = actor.tenantId;
    const userId = id(actor.userId);
    need(typeof tenantId === 'string' && /^[0-9]{6,20}$/.test(tenantId));
    const session = createBusinessSession(ports.readCredential, () => {
      const current = ports.readScope();
      need(
        revision === generation &&
          kind === state.kind &&
          documentId === state.documentId &&
          current.tenantId === tenantId &&
          normalizeBusinessId(current.userId) === userId &&
          current.routeKey === actor.routeKey
      );
    });
    const scope: BusinessScope = { tenantId, userId, feature: `wm:${ports.domain}:${kind}`, documentId };
    return { session, scope, kind, documentId, revision };
  }
  async function fresh(captured: ReturnType<typeof capture>) {
    const { session, kind, documentId, scope } = captured;
    session.assertCurrent();
    const ctx = await ports.context(session);
    session.assertCurrent();
    need(
      ctx.code === 200 &&
        ctx.data &&
        ctx.data.domain === ports.domain &&
        ctx.data.tenantId === scope.tenantId &&
        normalizeBusinessId(ctx.data.userId) === scope.userId
    );
    const response = await ports.detail(kind, documentId, session);
    session.assertCurrent();
    need(
      response.code === 200 &&
        response.data &&
        response.data.domain === ports.domain &&
        response.data.kind === kind &&
        normalizeBusinessId(response.data.documentId) === documentId
    );
    // 与编辑输入分离的冻结原生快照：迟到响应不能覆盖另一对象，表单不能篡改标量保护事实。
    const detail = JSON.parse(JSON.stringify(response.data)) as CertifiedWarehouseDetail;
    freezeNative(detail);
    state.context = Object.freeze({ ...ctx.data });
    state.detail = detail;
    state.blocked = store.isBlocked(scope);
    publish();
    return { context: state.context, detail };
  }
  function source(detail: CertifiedWarehouseDetail) {
    need(
      detail.protection === 'CERTIFIED' &&
        detail.sourceComplete === true &&
        detail.order &&
        Array.isArray(detail.orderLines) &&
        detail.orderLines.length > 0
    );
    const order = detail.order;
    const version = normalizeBusinessVersion(order.version);
    need(
      version !== null &&
        version !== undefined &&
        version > 0 &&
        version < 2147483647 &&
        order.delFlag === '0' &&
        order.tenantId === state.context?.tenantId &&
        order.selectFlowId == null &&
        normalizeBusinessId(order.termsId)
    );
    need(['APPROVED', 'PARTIALLY_RECEIVED', 'RECEIVED'].includes(String(order.status)));
    id(order.orderId);
    for (const line of detail.orderLines)
      need(
        line.tenantId === order.tenantId &&
          line.delFlag === '0' &&
          id(line.orderId) === id(order.orderId) &&
          normalizeBusinessId(line.lineId) &&
          normalizeBusinessId(line.materialId) &&
          normalizeBusinessId(line.baseUomId)
      );
    return { orderId: id(order.orderId), expectedOrderVersion: version };
  }
  function bodyFor(op: WarehouseOperation, values: WarehouseInput, detail: CertifiedWarehouseDetail) {
    const origin = source(detail);
    need(values.materialsConfirmed === true);
    if (op === 'createNotice' || op === 'createReceive') {
      need(/^[A-Za-z0-9:_-]{1,128}$/.test(values.requestKey) && typeof values.remark === 'string' && values.remark.length <= 500);
      const purchaseLineId = id(values.purchaseLineId);
      const selected = detail.orderLines.find((line) => id(line.lineId) === purchaseLineId);
      need(selected);
      const common = { requestKey: values.requestKey, ...origin, purchaseLineId, quantity: quantity(values.quantity) };
      if (op === 'createNotice') {
        need(typeof values.noticeName === 'string' && values.noticeName.trim() && values.noticeName.length <= 255);
        return { ...common, noticeName: values.noticeName, arrivalDate: date(values.arrivalDate), remark: values.remark };
      }
      const actualArrivalDate = date(values.actualArrivalDate);
      need(actualArrivalDate <= date(state.context?.databaseDate));
      const body: Record<string, unknown> = {
        ...common,
        warehouseId: id(values.warehouseId),
        areaId: id(values.areaId),
        locationId: id(values.locationId),
        actualArrivalDate,
        remark: values.remark
      };
      // 通知可选引用必须成对；完整通知原生对账在独立到货 query 域完成，未提供证明时封闭。
      if (values.noticeId || values.noticeLineId) {
        body.noticeId = id(values.noticeId);
        body.noticeLineId = id(values.noticeLineId);
      }
      return body;
    }
    need(detail.native && detail.native.tenantId === state.context?.tenantId && detail.native.delFlag === '0' && detail.lines.length > 0);
    need(detail.flowState !== 'UNKNOWN');
    if (op === 'submitNotice') {
      need(['DRAFT', 'REJECTED'].includes(String(detail.native.status)) && ['NOT_STARTED', 'DRAFT', 'REJECTED'].includes(detail.flowState));
      id(values.flowId); // selectFlowId 是实际自定义表单关联 Long ID，不是引擎定义 ID。
    } else if (op === 'synchronizeNotice') need(['APPROVING', 'APPROVED', 'REJECTED', 'DRAFT'].includes(detail.flowState));
    else {
      need(detail.flowState === 'APPROVED');
      need(detail.native.status === (op === 'shelfReceive' ? 'APPROVED' : 'SHELVED'));
      // 实际批次/储位/来料检验事实只能来自原生详情，不能在本工作台补造。
      need(detail.details.length > 0 && detail.lines.length === 1 && detail.iqcEvidence?.length === 1 && detail.births?.length === 1);
      const line = detail.lines[0];
      const evidence = detail.iqcEvidence[0];
      const birth = detail.births[0];
      need(
        evidence.tenantId === state.context?.tenantId &&
          birth.tenantId === state.context?.tenantId &&
          id(evidence.receiveId) === state.documentId &&
          id(birth.receiveId) === state.documentId &&
          id(evidence.materialId) === id(line.materialId) &&
          id(birth.materialId) === id(line.materialId) &&
          id(evidence.baseUomId) === id(line.baseUomId) &&
          id(birth.baseUomId) === id(line.baseUomId)
      );
      need(
        evidence.sourceKind === 'MES_QC_IQC_RECEIVE_MATERIAL_V2' &&
          evidence.inspectResult === 'QUALIFIED' &&
          id(evidence.birthId) === id(birth.birthId) &&
          evidence.birthSha256 === birth.birthSha256 &&
          /^[a-f0-9]{64}$/.test(String(evidence.birthSha256)) &&
          /^[a-f0-9]{64}$/.test(String(evidence.evidenceSha256)) &&
          decimalKey(evidence.inspectedQuantity) === decimalKey(line.quantity) &&
          decimalKey(birth.quantity) === decimalKey(line.quantity)
      );
      for (const row of detail.details)
        need(
          row.tenantId === state.context?.tenantId &&
            row.delFlag === '0' &&
            normalizeBusinessId(row.lineId) &&
            normalizeBusinessId(row.batchId) &&
            normalizeBusinessId(row.warehouseId) &&
            normalizeBusinessId(row.areaId) &&
            normalizeBusinessId(row.locationId) &&
            typeof row.quantity === 'string'
        );
    }
    return undefined;
  }
  /** 收货通知引用必须额外走 arrival query 域；不能用 receive query 读取或补证另一领域。 */
  async function verifyBinding(body: any, captured: ReturnType<typeof capture>, before: CertifiedWarehouseDetail) {
    if (!body?.noticeId) return null;
    need(ports.domain === 'receive' && ports.hasPermission('wm:arrivalnotice:query') && ports.bindingContext && ports.bindingDetail);
    const ctx = await ports.bindingContext(captured.session);
    captured.session.assertCurrent();
    need(
      ctx.code === 200 &&
        ctx.data &&
        ctx.data.domain === 'arrivalnotice' &&
        ctx.data.tenantId === captured.scope.tenantId &&
        id(ctx.data.userId) === captured.scope.userId
    );
    const response = await ports.bindingDetail(body.noticeId, captured.session);
    captured.session.assertCurrent();
    const notice = response.data;
    need(
      response.code === 200 &&
        notice &&
        notice.domain === 'arrivalnotice' &&
        notice.kind === 'NOTICE' &&
        id(notice.documentId) === body.noticeId &&
        notice.protection === 'CERTIFIED' &&
        notice.sourceComplete === true &&
        notice.flowState === 'APPROVED'
    );
    need(
      notice.native &&
        notice.native.tenantId === captured.scope.tenantId &&
        id(notice.native.noticeId) === body.noticeId &&
        ['APPROVED', 'PARTIAL_RECEIVED'].includes(String(notice.native.status)) &&
        notice.native.delFlag === '0' &&
        notice.order &&
        id(notice.order.orderId) === body.orderId
    );
    const line = notice.lines.find((value) => id(value.lineId) === body.noticeLineId);
    const origin = before.orderLines.find((value) => id(value.lineId) === body.purchaseLineId);
    const command = notice.creations?.find((value) => id(value.noticeLineId) === body.noticeLineId);
    need(
      line &&
        origin &&
        line.tenantId === captured.scope.tenantId &&
        line.delFlag === '0' &&
        id(line.purchaseLineId ?? command?.purchaseLineId) === body.purchaseLineId &&
        id(line.materialId) === id(origin.materialId) &&
        id(line.baseUomId) === id(origin.baseUomId)
    );
    return snapshotKey(notice);
  }
  function validResult(op: WarehouseOperation, response: Response<any>, documentId: string) {
    if (!response || response.code !== 200) return false;
    const r = response.data;
    if (op === 'createNotice')
      return (
        !!r &&
        !!normalizeBusinessId(r.noticeId) &&
        !!normalizeBusinessId(r.noticeLineId) &&
        typeof r.noticeCode === 'string' &&
        r.noticeCode.length > 0 &&
        r.status === 'DRAFT' &&
        r.replayed === false
      );
    if (op === 'createReceive')
      return (
        !!r &&
        !!normalizeBusinessId(r.receiveId) &&
        !!normalizeBusinessId(r.lineId) &&
        !!normalizeBusinessId(r.purchaseLineId) &&
        !!normalizeBusinessId(r.materialId) &&
        !!normalizeBusinessId(r.baseUomId) &&
        typeof r.receiveCode === 'string' &&
        r.status === 'DRAFT_CREATED' &&
        r.replayed === false &&
        /^[a-f0-9]{64}$/.test(r.requestHash) &&
        typeof r.quantity === 'string'
      );
    if (op === 'executeReceive')
      return (
        !!r &&
        normalizeBusinessId(r.receiveId) === documentId &&
        !!normalizeBusinessId(r.materialId) &&
        typeof r.quantity === 'string' &&
        /^[a-f0-9]{64}$/.test(r.sourceFingerprint) &&
        r.replay === false &&
        typeof r.notificationPending === 'boolean'
      );
    return r == null;
  }
  /** 明确成功仍必须用完整原生读回核对来源、行、操作主体；任何坏回读保留 UNKNOWN。 */
  async function verifyReadback(
    op: WarehouseOperation,
    response: Response<any>,
    body: any,
    captured: ReturnType<typeof capture>,
    before: CertifiedWarehouseDetail,
    values: WarehouseInput
  ) {
    const kind: WarehouseTarget = op === 'createNotice' ? 'NOTICE' : op === 'createReceive' ? 'RECEIVE' : captured.kind;
    const documentId =
      op === 'createNotice' ? id(response.data.noticeId) : op === 'createReceive' ? id(response.data.receiveId) : captured.documentId;
    const ctx = await ports.context(captured.session);
    captured.session.assertCurrent();
    need(
      ctx.code === 200 &&
        ctx.data &&
        ctx.data.domain === ports.domain &&
        ctx.data.tenantId === captured.scope.tenantId &&
        id(ctx.data.userId) === captured.scope.userId
    );
    const read = await ports.detail(kind, documentId, captured.session);
    captured.session.assertCurrent();
    const after = read.data;
    need(
      read.code === 200 &&
        after &&
        after.kind === kind &&
        after.domain === ports.domain &&
        id(after.documentId) === documentId &&
        after.protection === 'CERTIFIED' &&
        after.sourceComplete === true
    );
    need(
      after.order &&
        before.order &&
        id(after.order.orderId) === id(before.order.orderId) &&
        after.native &&
        after.native.tenantId === captured.scope.tenantId &&
        after.native.delFlag === '0'
    );
    need(id(after.native[kind === 'NOTICE' ? 'noticeId' : 'receiveId']) === documentId && after.lines.length > 0);
    if (op === 'createNotice' || op === 'createReceive') {
      need(after.creations?.length === 1 && after.lines.length === 1);
      const c = after.creations[0];
      const line = after.lines[0];
      const expectedLine = op === 'createNotice' ? id(response.data.noticeLineId) : id(response.data.lineId);
      need(
        c.tenantId === captured.scope.tenantId &&
          id(c.actorId) === captured.scope.userId &&
          id(after.native.createBy) === captured.scope.userId &&
          c.requestKey === body.requestKey &&
          id(c.orderId) === body.orderId &&
          id(c.purchaseLineId) === body.purchaseLineId
      );
      need(
        id(line.lineId) === expectedLine &&
          id(c[op === 'createNotice' ? 'noticeLineId' : 'lineId']) === expectedLine &&
          id(line.materialId) === id(c.materialId) &&
          id(line.baseUomId) === id(c.baseUomId) &&
          decimalKey(line.quantity) === decimalKey(body.quantity) &&
          decimalKey(c.quantity) === decimalKey(body.quantity)
      );
      const originalLine = before.orderLines.find((value) => id(value.lineId) === body.purchaseLineId);
      need(originalLine && id(originalLine.materialId) === id(line.materialId) && id(originalLine.baseUomId) === id(line.baseUomId));
      need(after.native.status === 'DRAFT');
      if (op === 'createNotice')
        need(
          c.orderVersion === body.expectedOrderVersion &&
            after.native.noticeCode === response.data.noticeCode &&
            after.native.noticeName === body.noticeName &&
            after.native.arrivalDate === body.arrivalDate &&
            after.flowState === 'NOT_STARTED'
        );
      else
        need(
          after.native.receiveCode === response.data.receiveCode &&
            c.receiveCode === response.data.receiveCode &&
            c.requestHash === response.data.requestHash &&
            id(c.receiveId) === documentId &&
            id(line.receiveId) === documentId &&
            line.tenantId === captured.scope.tenantId &&
            line.delFlag === '0' &&
            id(response.data.purchaseLineId) === body.purchaseLineId &&
            id(line.purchaseLineId) === body.purchaseLineId &&
            id(response.data.materialId) === id(line.materialId) &&
            id(response.data.baseUomId) === id(line.baseUomId) &&
            decimalKey(response.data.quantity) === decimalKey(body.quantity) &&
            optionalId(after.native.noticeId) === optionalId(body.noticeId) &&
            optionalId(c.noticeId) === optionalId(body.noticeId) &&
            optionalId(line.noticeLineId) === optionalId(body.noticeLineId) &&
            optionalId(c.noticeLineId) === optionalId(body.noticeLineId) &&
            id(after.native.purId) === body.orderId &&
            id(c.warehouseId) === body.warehouseId &&
            id(c.areaId) === body.areaId &&
            id(c.locationId) === body.locationId &&
            c.arrivalDate === body.actualArrivalDate
        );
    } else {
      need(after.lines.length === before.lines.length);
      for (const line of before.lines) {
        const found = after.lines.find((value) => id(value.lineId) === id(line.lineId));
        need(
          found &&
            id(found.materialId) === id(line.materialId) &&
            id(found.baseUomId) === id(line.baseUomId) &&
            decimalKey(found.quantity) === decimalKey(line.quantity) &&
            id(found.purchaseLineId ?? after.creations?.find((c) => id(c.noticeLineId) === id(found.lineId))?.purchaseLineId) ===
              id(line.purchaseLineId ?? before.creations?.find((c) => id(c.noticeLineId) === id(line.lineId))?.purchaseLineId)
        );
      }
      if (op === 'shelfReceive')
        need(
          after.native.status === 'SHELVED' &&
            id(after.native.shelfBy) === captured.scope.userId &&
            typeof after.native.shelfTime === 'string' &&
            after.native.shelfTime.length > 0
        );
      else if (op === 'executeReceive') {
        const posted = after.posting;
        need(
          after.native.status === 'FINISHED' &&
            id(after.native.updateBy) === captured.scope.userId &&
            posted &&
            posted.tenantId === captured.scope.tenantId &&
            id(posted.receiveId) === documentId &&
            id(posted.actorId) === captured.scope.userId &&
            posted.sourceFingerprint === response.data.sourceFingerprint &&
            id(posted.materialId) === id(response.data.materialId) &&
            decimalKey(posted.quantity) === decimalKey(response.data.quantity) &&
            decimalKey(posted.quantity) === decimalKey(before.lines[0].quantity) &&
            id(posted.materialId) === id(before.lines[0].materialId)
        );
      } else {
        need(
          after.workflow &&
            after.workflow.tenantId === captured.scope.tenantId &&
            after.workflow.dataId === documentId &&
            after.workflow.nextStatus === after.flowState &&
            after.native.status === after.flowState &&
            typeof after.workflow.processInstanceId === 'string' &&
            after.workflow.processInstanceId.length > 0
        );
        if (op === 'submitNotice')
          need(
            typeof ctx.data.username === 'string' &&
              after.workflow.initiatorName === ctx.data.username &&
              ['APPROVING', 'APPROVED'].includes(after.flowState)
          );
        // synchronize 原 SQL 不留永久同步 actor；这里只确认当前主体/本通知真实自身流程及原生状态，不虚构持久审计。
      }
    }
    return freezeNative(JSON.parse(JSON.stringify(after)) as CertifiedWarehouseDetail);
  }
  /** 对象/路由/账号变更使所有待回调过期；不释放任何持久意图。 */
  function setTarget(kind: WarehouseTarget, value: unknown) {
    generation++;
    reviewedSnapshot = null;
    state.kind = kind;
    state.documentId = safeId(value) ?? '';
    state.context = null;
    state.detail = null;
    state.result = null;
    state.recovery = null;
    state.blocked = false;
    state.message = '';
    publish();
  }
  /** UI 仅作提示；真正写前仍 fresh context/detail 重做合同校验。 */
  function available(op: WarehouseOperation, values: WarehouseInput) {
    try {
      const contract = operations[op];
      need(reviewedSnapshot);
      need(
        contract &&
          contract.domain === ports.domain &&
          contract.kind === state.kind &&
          state.context &&
          state.detail &&
          !state.busy &&
          !state.reading &&
          !state.blocked &&
          ports.hasPermission(contract.permission) &&
          state.context[contract.hint] === true
      );
      const captured = capture();
      captured.session.assertCurrent();
      need(!store.isBlocked(captured.scope));
      bodyFor(op, values, state.detail);
      return true;
    } catch {
      return false;
    }
  }
  async function inspect() {
    if (state.busy || state.reading) return;
    state.reading = true;
    publish();
    try {
      const current = await fresh(capture());
      reviewedSnapshot = current.detail;
      state.message = state.blocked
        ? 'UNKNOWN：已有未决意图，只可只读核对。刷新、换键及恢复查询均不解锁。'
        : '已读取原生事实；不代表审批、IQC或写入资格。';
    } catch {
      state.detail = null;
      state.context = null;
      state.message = 'BLOCKED：身份、读取或保护事实未知，只读；请重新选择真实对象。';
    } finally {
      state.reading = false;
      publish();
    }
  }
  async function perform(op: WarehouseOperation, values: WarehouseInput) {
    if (state.busy || state.reading) return false;
    state.busy = true;
    publish();
    let pending: ReturnType<typeof store.begin> | undefined;
    let dispatched = false;
    try {
      const captured = capture();
      const contract = operations[op];
      need(contract && contract.domain === ports.domain && contract.kind === captured.kind && ports.hasPermission(contract.permission));
      state.blocked = store.isBlocked(captured.scope);
      publish();
      need(!state.blocked);
      need(reviewedSnapshot);
      const reviewed = reviewedSnapshot;
      const { context, detail } = await fresh(captured);
      need(context[contract.hint] === true);
      if (snapshotKey(reviewed) !== snapshotKey(detail)) {
        reviewedSnapshot = null;
        state.detail = null;
        throw new Error('已审阅事实变化');
      }
      const input = { ...values };
      const body = bodyFor(op, input, detail);
      const reviewedBinding = op === 'createReceive' ? await verifyBinding(body, captured, detail) : null;
      const confirmed = await ports.confirm(
        `确认仅执行「${contract.label}」？对象 ${captured.documentId}。请已核对原生来源、真实资料及数量/日期；不会自动执行下一步骤。`
      );
      captured.session.assertCurrent();
      if (!confirmed) return false;
      need(ports.hasPermission(contract.permission));
      const checked = await fresh(captured);
      if (snapshotKey(reviewed) !== snapshotKey(checked.detail)) {
        reviewedSnapshot = null;
        state.detail = null;
        throw new Error('确认期间已审阅事实变化');
      }
      need(checked.context[contract.hint] === true);
      if (op === 'createReceive') need(reviewedBinding === (await verifyBinding(body, captured, checked.detail)));
      pending = store.begin(captured.scope, op, input.requestKey || `${op}:${captured.documentId}`);
      store.assertPending(pending);
      captured.session.assertCurrent();
      dispatched = true;
      const response = await ports.write(op, captured.documentId, body, captured.session, input.flowId);
      captured.session.assertCurrent();
      if (response && Number.isInteger(response.code) && response.code >= 400 && response.code < 500) {
        store.release(pending);
        pending = undefined;
        dispatched = false;
        state.blocked = false;
        state.message = '服务端明确拒绝；未执行后续步骤，请核对真实权限与业务条件。';
        return false;
      }
      need(validResult(op, response, captured.documentId));
      const reconciled = await verifyReadback(op, response, body, captured, detail, input);
      captured.session.assertCurrent();
      store.release(pending);
      pending = undefined;
      state.blocked = false;
      state.result = response.data ?? {
        operation: op,
        code: response.code,
        ...(op === 'synchronizeNotice' ? { actorAudit: '只确认当前请求主体，原接口不记录永久同步 actor' } : {})
      };
      reviewedSnapshot = null;
      state.detail = captured.kind === reconciled.kind ? reconciled : null;
      state.message = '本次独立操作返回明确成功；请人工刷新并核对原生详情，不自动继续审批/上架/执行。';
      return true;
    } catch (error) {
      // 共享传输仅以脱敏 R4xx 信封报告明确拒绝；抛错统一 UNKNOWN。
      state.blocked = state.blocked || !!pending || dispatched;
      state.message = pending
        ? 'UNKNOWN：结果不可确定。禁止重发或换键；只能读取原生详情及永久恢复，需人工对账。'
        : 'BLOCKED：已审阅的原生事实已变化或权限/来源/材料条件不完整，请刷新详情并重新确认；不静默采用新事实。';
      return false;
    } finally {
      state.busy = false;
      publish();
    }
  }
  /** 仅调用 GET/recovery；不调用 POST、不补发 MQ、不释放意图。 */
  async function recover() {
    if (ports.domain !== 'receive' || state.kind !== 'RECEIVE' || state.busy || state.reading) return;
    state.reading = true;
    publish();
    try {
      const captured = capture();
      captured.session.assertCurrent();
      const response = await ports.recovery(captured.documentId, captured.session);
      captured.session.assertCurrent();
      need(response.code === 200 && response.data && normalizeBusinessId(response.data.receiveId) === captured.documentId);
      state.recovery = response.data;
      state.message = '仅显示原永久结果；pending 不补发消息，UNKNOWN 不自动清除。';
    } catch {
      state.recovery = null;
      state.message = '原永久结果不可读取；没有执行重放、补发或解锁。';
    } finally {
      state.reading = false;
      publish();
    }
  }
  return {
    state,
    setTarget,
    inspect,
    perform,
    recover,
    available,
    invalidate: () => {
      generation++;
      reviewedSnapshot = null;
      state.detail = null;
      state.context = null;
      publish();
    }
  };
}

/** 通用页面的独立只读保护：原生标量存于私有 Map，编辑 form 永远不能授予普通单资格。 */
export function createWarehouseLegacyGate(
  ports: Pick<WarehousePorts, 'domain' | 'readCredential' | 'readScope' | 'hasPermission' | 'context' | 'detail'> & { changed?: () => void }
) {
  let readRevision = 0;
  const facts = new Map<string, { tenantId: unknown; userId: unknown; protection: string }>();
  function captureLegacy() {
    const actor = ports.readScope();
    const revision = readRevision;
    const session = createBusinessSession(ports.readCredential, () => {
      const current = ports.readScope();
      need(
        revision === readRevision && current.tenantId === actor.tenantId && current.userId === actor.userId && current.routeKey === actor.routeKey
      );
    });
    return { actor, session };
  }
  async function context(session: CertifiedBusinessSession, actor: ReturnType<typeof ports.readScope>) {
    const response = await ports.context(session);
    session.assertCurrent();
    need(
      response.code === 200 &&
        response.data &&
        response.data.domain === ports.domain &&
        response.data.tenantId === actor.tenantId &&
        id(response.data.userId) === id(actor.userId)
    );
  }
  async function native(value: unknown, session: CertifiedBusinessSession, actor: ReturnType<typeof ports.readScope>) {
    const documentId = id(value);
    const response = await ports.detail(ports.domain === 'arrivalnotice' ? 'NOTICE' : 'RECEIVE', documentId, session);
    session.assertCurrent();
    need(response.code === 200 && response.data && response.data.domain === ports.domain && id(response.data.documentId) === documentId);
    facts.set(documentId, { tenantId: actor.tenantId, userId: actor.userId, protection: response.data.protection });
    ports.changed?.();
    return response.data;
  }
  async function probe(value: unknown) {
    try {
      const captured = captureLegacy();
      await context(captured.session, captured.actor);
      return await native(value, captured.session, captured.actor);
    } catch {
      const normalized = safeId(value);
      if (normalized) facts.delete(normalized);
      ports.changed?.();
      return null;
    }
  }
  function known(value: unknown) {
    const normalized = safeId(value);
    const actor = ports.readScope();
    const fact = normalized ? facts.get(normalized) : null;
    return !!fact && fact.tenantId === actor.tenantId && fact.userId === actor.userId && fact.protection === 'LEGACY';
  }
  /** 每次真正派发前重新 query；按钮提示与旧表单字段不能替代此门禁。 */
  async function requireLegacy(values: unknown[], permission: string) {
    need(values.length > 0 && ports.hasPermission(permission));
    const captured = captureLegacy();
    await context(captured.session, captured.actor);
    for (const value of values) need(isLegacyWarehouseDetail(await native(value, captured.session, captured.actor), ports.domain, value));
    captured.session.assertCurrent();
    need(ports.hasPermission(permission));
    return captured.session;
  }
  /** 新普通单仅保留明确人工、无来源绑定路径；未知/认证来源应使用专属工作台而非通用创建。 */
  async function requireNew(permission: string, form: Record<string, any>) {
    need(ports.hasPermission(permission));
    const captured = captureLegacy();
    await context(captured.session, captured.actor);
    need(!form.purId && !form.orderId && !form.noticeId && !form.purOrderCode && !form.iqcId && !form.requestKey && !form.attr1);
    const lines = form.wmMaterialReceiveLineList ?? form.wmArrivalNoticeLineList ?? [];
    need(Array.isArray(lines) && lines.every((line: any) => !line.purchaseLineId && !line.noticeLineId && !line.sourceDocLineId));
    captured.session.assertCurrent();
    return captured.session;
  }
  return {
    probe,
    known,
    requireLegacy,
    requireNew,
    readSession: () => captureLegacy().session,
    clear: () => {
      readRevision++;
      facts.clear();
      ports.changed?.();
    }
  };
}
