import request from '@/utils/request';
import type { AxiosPromise } from 'axios';

/** 用章授权视图对象（对应后端 SealGrantVo） */
export interface GrantVO {
  grantId?: string | number;
  sealId: string | number;
  /** 印章名称(服务层联表填充) */
  sealName?: string;
  granteeUserId?: string | number;
  /** 被授权人昵称(序列化时按 granteeUserId 翻译) */
  granteeUserName?: string;
  granteeDeptId?: string | number;
  /** 被授权部门名称(序列化时按 granteeDeptId 翻译) */
  granteeDeptName?: string;
  /** 限定业务场景(null=不限) */
  bizType?: string;
  /** 可否单独用章(0否 1是) */
  canSignAlone?: string;
  validFrom?: string;
  validTo?: string;
  /** 状态(0停用 1启用) */
  status?: string;
  grantBy?: string | number;
  remark?: string;
  createTime?: string;
}

/** 用章授权新增/修改入参（对应后端 SealGrantBo） */
export interface GrantForm {
  grantId?: string | number;
  sealId: string | number;
  granteeUserId?: string | number;
  granteeDeptId?: string | number;
  bizType?: string;
  canSignAlone?: string;
  validFrom?: string;
  validTo?: string;
  status?: string;
  remark?: string;
}

/** 用章授权查询参数 */
export interface GrantQuery extends PageQuery {
  sealId?: string | number;
  granteeUserId?: string | number;
  granteeDeptId?: string | number;
  bizType?: string;
  status?: string;
}

// 查询用章授权列表
export function listGrant(query?: GrantQuery): AxiosPromise<GrantVO[]> {
  return request({
    url: '/system/sign/grant/list',
    method: 'get',
    params: query
  });
}

// 按印章查询授权列表
export function listGrantBySeal(sealId: string | number): AxiosPromise<GrantVO[]> {
  return request({
    url: '/system/sign/grant/by-seal/' + sealId,
    method: 'get'
  });
}

// 新增用章授权
export function addGrant(data: GrantForm) {
  return request({
    url: '/system/sign/grant',
    method: 'post',
    data: data
  });
}

// 修改用章授权
export function updateGrant(data: GrantForm) {
  return request({
    url: '/system/sign/grant',
    method: 'put',
    data: data
  });
}

// 删除用章授权
export function delGrant(grantIds: string | number | (string | number)[]) {
  return request({
    url: '/system/sign/grant/' + grantIds,
    method: 'delete'
  });
}
