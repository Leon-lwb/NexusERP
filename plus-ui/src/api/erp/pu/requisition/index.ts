import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import type {
  PurchaseRequisitionQuery,
  PurchaseRequisitionVO,
  PurchaseRequisitionForm,
  CertifiedPurchaseSource,
  CertifiedPurchaseSourcePreview,
  PuCertifiedPurchaseCommand,
  CertifiedPurchaseResult,
  CertifiedPurchaseResponse,
  CertifiedRequisitionWorkflowContext,
  CertifiedRequisitionWorkflowResult
} from './types';

/**
 * 采购请购模块API
 * 提供采购请购单的增删改查及相关业务操作接口
 */
const BASE = '/erp/pu/requisition';

/**
 * 查询采购请购单列表
 * @param query 采购请购单查询条件
 * @returns 采购请购单列表及总数
 */
export const listPurchaseRequisition = (query: PurchaseRequisitionQuery): AxiosPromise<PurchaseRequisitionVO[]> =>
  request({
    url: BASE + '/list',
    method: 'get',
    params: query
  });

/**
 * 获取指定ID的采购请购单详情
 * @param id 采购请购单ID
 * @returns 采购请购单详细信息
 */
export const getPurchaseRequisition = (id: string | number): AxiosPromise<PurchaseRequisitionVO> =>
  request({
    url: BASE + '/' + id,
    method: 'get'
  });

/** 普通 BO 新增已经关闭，保留兼容函数但绝不再发送旧新增写请求。 */
export const addPurchaseRequisition = (_data: PurchaseRequisitionForm): Promise<never> =>
  Promise.reject(new Error('普通 BO 新增已关闭，请使用可信来源创建并先进行服务端完整预览'));

/**
 * 修改采购请购单
 * @param data 采购请购单表单数据
 * @returns 请求结果
 */
export const updatePurchaseRequisition = (data: PurchaseRequisitionForm) =>
  request({
    url: BASE,
    method: 'put',
    data
  });

/**
 * 删除采购请购单
 * @param id 采购请购单ID或ID数组
 * @returns 请求结果
 */
export const delPurchaseRequisition = (id: string | number | (string | number)[]) =>
  request({
    url: BASE + '/' + id,
    method: 'delete'
  });

/**
 * 导出采购请购单数据
 * @param query 采购请购单查询条件
 * @returns 下载链接
 */
export const exportPurchaseRequisition = (query: PurchaseRequisitionQuery) =>
  request({
    url: BASE + '/export',
    method: 'post',
    params: query
  });

// /**
//  * 提交采购请购单（已废弃，请使用 submitRequisitionFlow 走 Flowable 工作流）
//  * @param id 采购请购单ID
//  * @returns 请求结果
//  */
// export const submitPurchaseRequisition = (id: number) =>
//   request({
//     url: BASE + '/submit/' + id,
//     method: 'put'
//   });

// /**
//  * 审核采购请购单（已废弃，请使用 Flowable 工作流审批）
//  * @param id 采购请购单ID
//  * @returns 请求结果
//  */
// export const approvePurchaseRequisition = (id: number) =>
//   request({
//     url: BASE + '/approve/' + id,
//     method: 'put'
//   });

/**
 * 将请购单转换为采购订单
 * @param id 请购单ID
 * @returns 新创建的采购订单ID
 */
export const convertPurchaseRequisition = (id: number): AxiosPromise<number> =>
  request({
    url: BASE + '/convert/' + id,
    method: 'put'
  });

/**
 * 提交流程审批（Flowable 审批流）
 * @param id 请购单ID
 * @param selectFlowId 审批流ID
 * @returns 请求结果
 */
export const submitRequisitionFlow = (id: string, selectFlowId?: string) =>
  request({
    url: BASE + '/' + id + '/submit',
    method: 'post',
    params: selectFlowId ? { selectFlowId } : {}
  });

/**
 * 获取流程实例ID
 * @param id 请购单ID
 * @returns 流程实例ID
 */
export const getRequisitionProcId = (id: string) =>
  request({
    url: BASE + '/' + id + '/proc',
    method: 'get'
  });

/**
 * 从低库存自动生成采购申请
 * 扫描所有物料，当库存低于安全库存时自动生成采购申请
 * @returns 生成的采购申请ID列表
 */
export const generateFromLowStock = (): AxiosPromise<number[]> =>
  request({
    url: BASE + '/generate-from-low-stock',
    method: 'post'
  });

/** 只读认证上下文由后端按当前凭证及有效租户核验，不读取 Pinia 的缓存结论。 */
export interface CertifiedPurchaseContext {
  tenantId: string;
  userId: string | number;
}

/** 仅当前会话内存使用，绝不能并入命令、原 intent 存储或错误文案。 */
export interface CertifiedPurchaseSession {
  token: string;
  assertCurrent: () => void;
}

function certifiedSessionConfig(session: CertifiedPurchaseSession) {
  if (!session || typeof session.token !== 'string' || !session.token) throw new Error('当前凭证未经核验，禁止发送请求');
  session.assertCurrent();
  const transforms = request.defaults.transformRequest;
  return {
    // 固定刚核验的内存凭证，禁止全局拦截器在其他 Tab 换用户后替换成本次之外的凭证。
    headers: { isToken: false, Authorization: 'Bearer ' + session.token, repeatSubmit: false },
    transformRequest: [
      ...(Array.isArray(transforms) ? transforms : transforms ? [transforms] : []),
      (body: unknown) => {
        // 位于所有请求拦截器之后、实际 adapter dispatch 之前，再次核对共享认证快照。
        session.assertCurrent();
        return body;
      }
    ]
  };
}

/** 该只读端点不清理动态租户，也不创建业务单据；身份只用于会话内比对。 */
export async function getCertifiedPurchaseContext(session: CertifiedPurchaseSession): Promise<CertifiedPurchaseContext> {
  const response = await request<unknown, CertifiedPurchaseResponse<CertifiedPurchaseContext>>({
    url: BASE + '/certified/context',
    method: 'get',
    ...certifiedSessionConfig(session)
  });
  session.assertCurrent();
  if (response?.code !== 200 || !response.data) throw new Error('当前采购认证上下文未确认');
  return response.data;
}

function definiteBusinessRefusal(response: CertifiedPurchaseResponse<unknown> | undefined, httpStatus?: number): boolean {
  // HTTP 5xx、网关异常以及 R 5xx 均不能证明 ERP 未提交；只保留明确的 4xx 业务拒绝。
  return (
    !!response &&
    typeof response.code === 'number' &&
    response.code >= 400 &&
    response.code < 500 &&
    httpStatus !== undefined &&
    ((httpStatus >= 200 && httpStatus < 300) || (httpStatus >= 400 && httpStatus < 500))
  );
}

/** 沿用全局认证/错误处理；状态不确定时原异常继续抛出，不转成可换键的拒绝。 */
async function certifiedRequest<T>(
  url: string,
  data: object,
  session: CertifiedPurchaseSession,
  strictHttpSuccess = false
): Promise<CertifiedPurchaseResponse<T>> {
  let received: CertifiedPurchaseResponse<T> | undefined;
  let httpStatus: number | undefined;
  const transforms = request.defaults.transformResponse;
  try {
    const response = await request<unknown, CertifiedPurchaseResponse<T>>({
      url,
      method: 'post',
      data,
      ...certifiedSessionConfig(session),
      transformResponse: [
        ...(Array.isArray(transforms) ? transforms : transforms ? [transforms] : []),
        (body: unknown, _headers: unknown, status?: number) => {
          httpStatus = status;
          if (body && typeof body === 'object' && 'code' in body) received = body as CertifiedPurchaseResponse<T>;
          return body;
        }
      ]
    });
    session.assertCurrent();
    if (strictHttpSuccess && response?.code === 200 && (httpStatus !== 200 || received?.code !== 200))
      throw new Error('服务端未确认正常写入信封，禁止重试');
    if (response?.code !== 200 && !definiteBusinessRefusal(response, httpStatus)) throw new Error('服务端未确认写入结果，禁止换键重试');
    return response;
  } catch (error) {
    session?.assertCurrent();
    if (definiteBusinessRefusal(received, httpStatus)) {
      // 使用全局处理后的错误文案，不重新暴露原始认证异常或按 msg 文本猜测提交结果。
      const msg = error instanceof Error ? error.message : typeof error === 'string' && error !== 'error' ? error : '服务端明确拒绝本次请求';
      return { code: received!.code, msg };
    }
    throw error;
  }
}

/** 预览契约使用 detailIds；不传入任何客户端计算的额度或可信标记。 */
export const previewCertifiedPurchaseSource = (source: CertifiedPurchaseSource, session: CertifiedPurchaseSession) =>
  certifiedRequest<CertifiedPurchaseSourcePreview>(
    '/erp/ai/certified-source/preview',
    {
      materialId: source.materialId,
      vendorId: source.vendorId,
      windowEnd: source.windowEnd,
      detailIds: [...source.requirementDetailIds]
    },
    session
  );

/** 创建契约使用 requirementDetailIds，严格限定服务端接受的六个字段。 */
export const createCertifiedPurchaseRequisition = (command: PuCertifiedPurchaseCommand, session: CertifiedPurchaseSession) =>
  certifiedRequest<CertifiedPurchaseResult>(
    BASE + '/certified',
    {
      requestKey: command.requestKey,
      materialId: command.materialId,
      vendorId: command.vendorId,
      windowEnd: command.windowEnd,
      requirementDetailIds: [...command.requirementDetailIds],
      expectedSourceFingerprint: command.expectedSourceFingerprint
    },
    session
  );

export type { CertifiedRequisitionWorkflowContext, CertifiedRequisitionWorkflowResult } from './types';

/** API 边界独立核验 ID，避免导入视图运行时造成 API/组件循环依赖。 */
function certifiedWorkflowId(id: string | number): string {
  const value = typeof id === 'number' && Number.isSafeInteger(id) && id > 0 ? String(id) : id;
  if (typeof value !== 'string' || !/^[1-9]\d{0,18}$/.test(value) || (value.length === 19 && value > '9223372036854775807')) {
    throw new Error('请购单 ID 未确认，禁止发送请求');
  }
  return value;
}

function certifiedWorkflowVersion(version: number): number {
  if (typeof version !== 'number' || !Number.isInteger(version) || version < 0 || version >= 2147483647) {
    throw new Error('请购单版本未确认，禁止发送请求');
  }
  return version;
}

/** 沿用固定会话和 dispatch 前复核；只读也必须收到真实 HTTP 200/R 200 信封。 */
async function certifiedWorkflowRead<T>(url: string, session: CertifiedPurchaseSession): Promise<T> {
  let httpStatus: number | undefined;
  const transforms = request.defaults.transformResponse;
  try {
    const response = await request<unknown, CertifiedPurchaseResponse<T>>({
      url,
      method: 'get',
      ...certifiedSessionConfig(session),
      transformResponse: [
        ...(Array.isArray(transforms) ? transforms : transforms ? [transforms] : []),
        (body: unknown, _headers: unknown, status?: number) => {
          httpStatus = status;
          return body;
        }
      ]
    });
    session.assertCurrent();
    if (httpStatus !== 200 || response?.code !== 200 || !response.data || typeof response.data !== 'object' || Array.isArray(response.data)) {
      throw new Error('认证只读结果未确认');
    }
    return response.data;
  } catch {
    throw new Error('认证只读结果未确认，请重新核验当前身份与单据');
  }
}

/**
 * 查询认证生命周期上下文，与创建入口的 add 权限独立。
 * @param session 仅内存保存的固定凭证会话
 * @returns 当前真实主体及按钮提示，不代表服务端写授权
 */
export async function getCertifiedRequisitionWorkflowContext(session: CertifiedPurchaseSession): Promise<CertifiedRequisitionWorkflowContext> {
  const context = await certifiedWorkflowRead<CertifiedRequisitionWorkflowContext>(BASE + '/certified/workflow-context', session);
  if (
    typeof context.tenantId !== 'string' ||
    !context.tenantId ||
    context.tenantId.length > 20 ||
    context.tenantId.trim() !== context.tenantId ||
    typeof context.approveAllowed !== 'boolean' ||
    typeof context.convertAllowed !== 'boolean'
  )
    throw new Error('认证生命周期主体未确认');
  certifiedWorkflowId(context.userId);
  return context;
}

/**
 * 正常读取原生完整 VO，不用来源提示代替后端认证或永久 command。
 * @param id 原请购单精确 ID，长整数字符串不得转 Number
 * @param session 本次固定凭证会话
 * @returns 当前完整请购单详情
 */
export async function getCertifiedRequisitionWorkflowDetail(id: string | number, session: CertifiedPurchaseSession): Promise<PurchaseRequisitionVO> {
  const normalized = certifiedWorkflowId(id);
  const detail = await certifiedWorkflowRead<PurchaseRequisitionVO>(BASE + '/' + normalized, session);
  if (certifiedWorkflowId(detail.requisitionId) !== normalized) throw new Error('当前请购单详情不匹配');
  return detail;
}

/**
 * 认证整单批准；仅提交期望版本，不传主体、数量或可信标记。
 * @param id 原请购单精确 ID
 * @param expectedVersion 刚从完整详情读取的合法整数版本
 * @param session 固定会话，实际派发前再次复核
 * @returns 原始业务信封；不确定结果仍拒绝 Promise，交由控制器安全锁存
 */
export async function approveCertifiedPurchaseRequisition(id: string | number, expectedVersion: number, session: CertifiedPurchaseSession) {
  return certifiedRequest<CertifiedRequisitionWorkflowResult>(
    BASE + '/certified/' + certifiedWorkflowId(id) + '/approve',
    { expectedVersion: certifiedWorkflowVersion(expectedVersion) },
    session,
    true
  );
}

/**
 * 认证转单或 ORDERED 原单受控回查；绝不另建申请或猜订单编号。
 * @param id 原请购单精确 ID
 * @param expectedVersion 当前详情版本
 * @param session 仅内存保存的固定凭证会话
 * @returns 服务端 Result 业务信封，5xx/网络错误不是明确拒绝
 */
export async function convertCertifiedPurchaseRequisition(id: string | number, expectedVersion: number, session: CertifiedPurchaseSession) {
  return certifiedRequest<CertifiedRequisitionWorkflowResult>(
    BASE + '/certified/' + certifiedWorkflowId(id) + '/convert',
    { expectedVersion: certifiedWorkflowVersion(expectedVersion) },
    session,
    true
  );
}
