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
  CertifiedPurchaseResponse
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
async function certifiedRequest<T>(url: string, data: object, session: CertifiedPurchaseSession): Promise<CertifiedPurchaseResponse<T>> {
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
