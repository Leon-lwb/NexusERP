import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** SOP文档视图对象 */
export interface SopDocumentVO {
  sopId: number;
  sopCode: string;
  sopName: string;
  sopType: string;
  currentVersion?: string;
  status: string;
  fileOssId?: string;
  fileUrl?: string;
  fileType?: string;
  description?: string;
  approvalBy?: number;
  approvalByName?: string;
  approvalTime?: string;
  approvalRemark?: string;
  archiveReason?: string;
  createBy?: number;
  createByName?: string;
  remark?: string;
  createTime?: string;
}

/** SOP文档查询条件 */
export interface SopDocumentQuery extends PageQuery {
  sopCode?: string;
  sopName?: string;
  sopType?: string;
  status?: string;
}

/** SOP文档表单(新增/编辑) */
export interface SopDocumentForm {
  sopId?: number;
  sopCode?: string;
  sopName: string;
  sopType: string;
  fileType?: string;
  fileOssId?: string;
  description?: string;
  remark?: string;
}

/** SOP新增版本入参 */
export interface SopVersionAddBo {
  sopId: number;
  versionNo?: string;
  fileOssId: string;
  fileType?: string;
  changeLog?: string;
  remark?: string;
}

/** 分页查询SOP文档列表 */
export const listDocument = (query?: SopDocumentQuery): AxiosPromise<SopDocumentVO[]> =>
  request({ url: '/mes/sop/document/list', method: 'get', params: query });

/** 获取SOP详情 */
export const getDocument = (sopId: number | string): AxiosPromise<SopDocumentVO> => request({ url: `/mes/sop/document/${sopId}`, method: 'get' });

/** 新增SOP(编码自动生成 + 首版本 PENDING) */
export const addDocument = (data: SopDocumentForm) => request({ url: '/mes/sop/document', method: 'post', data });

/** 修改SOP(仅 DRAFT/ARCHIVED 可编辑) */
export const updateDocument = (data: SopDocumentForm) => request({ url: '/mes/sop/document', method: 'put', data });

/** 批量删除SOP(孤儿守卫) */
export const delDocument = (sopIds: number | number[]) => request({ url: `/mes/sop/document/${sopIds}`, method: 'delete' });

/** 新增版本(DRAFT 或 PUBLISHED 上修订) */
export const addVersion = (data: SopVersionAddBo) => request({ url: '/mes/sop/document/version', method: 'post', data });

/** 提交审批: DRAFT -> IN_REVIEW (selectFlowId 非空走 Flowable) */
export const submitDocument = (sopId: number | string, selectFlowId?: string) =>
  request({ url: `/mes/sop/document/submit/${sopId}`, method: 'post', params: { selectFlowId } });

/** 兜底审批通过: IN_REVIEW -> PUBLISHED */
export const approveDocument = (sopId: number | string, remark?: string) =>
  request({ url: `/mes/sop/document/approve/${sopId}`, method: 'put', params: { remark } });

/** 兜底驳回: IN_REVIEW -> DRAFT */
export const rejectDocument = (sopId: number | string, reason?: string) =>
  request({ url: `/mes/sop/document/reject/${sopId}`, method: 'put', params: { reason } });

/** 归档(废止): PUBLISHED -> ARCHIVED */
export const archiveDocument = (sopId: number | string, reason?: string) =>
  request({ url: `/mes/sop/document/archive/${sopId}`, method: 'put', params: { reason } });

/** 契约: 按工序查SOP(工位推送用) */
export const getSopByProcess = (processId: number | string): AxiosPromise<SopDocumentVO> =>
  request({ url: `/mes/sop/document/by-process/${processId}`, method: 'get' });

/** 契约: 按流转卡工序记录查SOP */
export const getSopByStep = (stepRecordId: number | string): AxiosPromise<SopDocumentVO> =>
  request({ url: `/mes/sop/document/by-step/${stepRecordId}`, method: 'get' });

/** 契约: 按物料查全部工序SOP(工单开工预加载) */
export const getSopByMaterial = (materialId: number | string): AxiosPromise<Record<string, SopDocumentVO>> =>
  request({ url: `/mes/sop/document/by-material/${materialId}`, method: 'get' });

/** 契约: 触发SSE推送当前工序SOP到工位 */
export const pushSop = (stepRecordId: number | string): AxiosPromise<SopDocumentVO> =>
  request({ url: `/mes/sop/document/push/${stepRecordId}`, method: 'post' });
