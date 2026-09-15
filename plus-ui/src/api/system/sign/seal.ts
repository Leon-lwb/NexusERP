import request from '@/utils/request';
import type { AxiosPromise } from 'axios';

/**
 * 印章视图对象（对应后端 SealVo）
 * <p>注意：<b>不含</b> sealOssId —— 印章原图仅服务端使用，任何接口都不下发。</p>
 */
export interface SealVO {
  sealId?: string | number;
  /** 印章类型(1个人 2公司) */
  sealType: string;
  sealName: string;
  /** 个人章归属用户ID(公司章为 null) */
  ownerUserId?: string | number;
  /** 预览图 OSS ID(低清+水印) */
  previewOssId?: string | number;
  /** 预览图访问地址(后端 @Translation(OSS_ID_TO_URL) 由 previewOssId 翻译；previewOssId 未生成时为 null) */
  previewUrl?: string;
  imgWidth?: number;
  imgHeight?: number;
  /** 状态(0草稿 1启用 2停用 3销毁) */
  status?: string;
  sealVersion?: number;
  validFrom?: string;
  validTo?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 印章新增/修改入参（对应后端 SealBo）
 * <p>sealOssId 在<b>新增</b>时必填；修改时留空表示沿用原章面。</p>
 */
export interface SealForm {
  sealId?: string | number;
  sealType: string;
  sealName: string;
  ownerUserId?: string | number;
  sealOssId?: string | number;
  imgWidth?: number;
  imgHeight?: number;
  validFrom?: string;
  validTo?: string;
  remark?: string;
}

/** 当前用户可用印章（对应后端 AvailableSealVo） */
export interface AvailableSealVO {
  sealId: string | number;
  sealType: string;
  sealName: string;
  previewOssId?: string | number;
  /** 预览图访问地址（同 SealVO.previewUrl） */
  previewUrl?: string;
  imgWidth?: number;
  imgHeight?: number;
}

/** 印章查询参数 */
export interface SealQuery extends PageQuery {
  sealName?: string;
  sealType?: string;
  status?: string;
}

// 查询印章列表
export function listSeal(query?: SealQuery): AxiosPromise<SealVO[]> {
  return request({
    url: '/system/sign/seal/list',
    method: 'get',
    params: query
  });
}

// 查询印章详细
export function getSeal(sealId: string | number): AxiosPromise<SealVO> {
  return request({
    url: '/system/sign/seal/' + sealId,
    method: 'get'
  });
}

// 查询当前用户在指定业务场景下可用的印章
export function listAvailableSeal(bizType: string): AxiosPromise<AvailableSealVO[]> {
  return request({
    url: '/system/sign/seal/available',
    method: 'get',
    params: { bizType }
  });
}

// 获取印章预览图的 OSS ID（仅返回 ossId，需再换取访问地址）
export function getSealPreview(sealId: string | number): AxiosPromise<string | number> {
  return request({
    url: `/system/sign/seal/${sealId}/preview`,
    method: 'get'
  });
}

// 新增印章
export function addSeal(data: SealForm) {
  return request({
    url: '/system/sign/seal',
    method: 'post',
    data: data
  });
}

// 修改印章
export function updateSeal(data: SealForm) {
  return request({
    url: '/system/sign/seal',
    method: 'put',
    data: data
  });
}

// 删除印章
export function delSeal(sealIds: string | number | (string | number)[]) {
  return request({
    url: '/system/sign/seal/' + sealIds,
    method: 'delete'
  });
}

// 启停/销毁印章（0草稿 1启用 2停用 3销毁，销毁为终态）
export function changeSealStatus(sealId: string | number, status: string) {
  return request({
    url: `/system/sign/seal/${sealId}/status/${status}`,
    method: 'put'
  });
}
