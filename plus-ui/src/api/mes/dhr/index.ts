import request, { download } from '@/utils/request';
import type { AxiosPromise } from 'axios';
import type { DhrRecordVo, DhrRecordQuery, DhrRecordForm, DhrSectionVo, DhrVerifyVo, DhrSignatureVo, DhrSignForm, DhrRejectForm } from './types';

/**
 * DHR（Device History Record / 批记录）API 层。
 *
 * <p>批记录是 GMP/合规场景的电子批记录：冻结快照哈希 + 分册内容哈希 + 电子签名链，
 * 状态机 DRAFT → SUBMITTED → RELEASED → ARCHIVED（SUBMITTED 可 REJECTED 回退到可重建）。</p>
 *
 * <p><b>签署类接口的凭证约定</b>：{@code credential} 只随本次请求体发送，绝不写入
 * Pinia / localStorage / URL query / console；后端为 {@code @RequestBody} 接收。</p>
 */

/** 分页查询批记录列表 */
export const listDhr = (query?: DhrRecordQuery): AxiosPromise<DhrRecordVo[]> => request({ url: '/mes/dhr/list', method: 'get', params: query });

/** 获取批记录详情（含 sections） */
export const getDhr = (dhrId: string): AxiosPromise<DhrRecordVo> => request({ url: `/mes/dhr/${dhrId}`, method: 'get' });

/** 查询批记录分册列表 */
export const listDhrSections = (dhrId: string): AxiosPromise<DhrSectionVo[]> => request({ url: `/mes/dhr/${dhrId}/sections`, method: 'get' });

/** 查询批记录签名链（只读，写入由各业务动作触发） */
export const listDhrSignatures = (dhrId: string): AxiosPromise<DhrSignatureVo[]> => request({ url: `/mes/dhr/${dhrId}/signatures`, method: 'get' });

/** 校验批记录（重算快照哈希 + 签名链校验） */
export const verifyDhr = (dhrId: string): AxiosPromise<DhrVerifyVo> => request({ url: `/mes/dhr/${dhrId}/verify`, method: 'get' });

/** 生成批记录（按工单聚合，可选批次号 / SN） */
export const buildDhr = (data: DhrRecordForm) => request({ url: '/mes/dhr/build', method: 'post', data });

/** 重建批记录（仅 DRAFT，重算分册与快照哈希） */
export const rebuildDhr = (dhrId: string) => request({ url: `/mes/dhr/${dhrId}/rebuild`, method: 'put' });

/** 删除批记录（仅 DRAFT） */
export const delDhr = (dhrId: string) => request({ url: `/mes/dhr/${dhrId}`, method: 'delete' });

/**
 * 提交复核（带二级认证签名）。
 * <p>失败（如「签署密码错误」）由 utils/request.ts 拦截器统一提示后以 Error 拒绝，
 * 调用方据此保持认证弹窗打开供改正重试，不要本地重复 toast。</p>
 */
export const submitDhr = (dhrId: string, data: DhrSignForm) => request({ url: `/mes/dhr/${dhrId}/submit`, method: 'put', data });

/** 放行（带二级认证签名，仅 SUBMITTED） */
export const releaseDhr = (dhrId: string, data: DhrSignForm) => request({ url: `/mes/dhr/${dhrId}/release`, method: 'put', data });

/** 驳回（带二级认证签名 + 驳回原因，仅 SUBMITTED） */
export const rejectDhr = (dhrId: string, data: DhrRejectForm) => request({ url: `/mes/dhr/${dhrId}/reject`, method: 'put', data });

/** 归档（仅 RELEASED） */
export const archiveDhr = (dhrId: string) => request({ url: `/mes/dhr/${dhrId}/archive`, method: 'put' });

/** 导出批记录 */
export const exportDhr = (query?: DhrRecordQuery) => download('/mes/dhr/export', { ...query }, `dhr_record_${new Date().getTime()}.xlsx`);
