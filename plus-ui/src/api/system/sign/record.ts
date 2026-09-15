import request from '@/utils/request';
import type { AxiosPromise } from 'axios';

/**
 * 签名记录视图对象（对应后端 SignatureVo，只读）
 * <p>签名链 append-only，写入统一走服务端 {@code ESignatureService.sign()}，无对外创建接口。</p>
 */
export interface SignatureVO {
  signatureId: string | number;
  bizType: string;
  bizId?: string | number;
  /** 业务单据编号(冗余快照) */
  bizNo?: string;
  signerUserId: string | number;
  /** 签署人姓名(冗余快照) */
  signerName: string;
  signerDeptId?: string | number;
  sealId?: string | number;
  /** 印章类型快照 */
  sealType?: string;
  /** 签署含义 */
  signatureMeaning?: string;
  /** 认证方式 */
  authMethod?: string;
  /** 签署后文档OSS ID */
  docOssId?: string | number;
  /** 签署后文档SM3 */
  docHash?: string;
  docId?: string | number;
  /** 签署时间(服务端时间) */
  signedAt?: string;
  signIp?: string;
  signUa?: string;
  /** 前一条链哈希(链首为 null) */
  prevHash?: string;
  /** 本条链哈希(SM3) */
  chainHash?: string;
  remark?: string;
}

/** 验签结果（对应后端 VerifyResultVo） */
export interface VerifyResultVO {
  /** 整体是否通过 */
  passed?: boolean;
  /** 链校验是否通过 */
  chainPassed?: boolean;
  /** 断链位置(首个不一致的 signatureId；无断链为 null) */
  brokenAt?: string | number;
  /** 校验的记录条数 */
  checkedCount?: number;
  /** 明细说明 */
  messages?: string[];
}

// 按业务查询签名记录（bizId 为空时返回该业务类型下的全部记录）
export function listSignature(bizType: string, bizId?: string | number): AxiosPromise<SignatureVO[]> {
  return request({
    url: '/system/sign/record/list',
    method: 'get',
    params: { bizType, bizId }
  });
}

// 单条验签
export function verifySignature(signatureId: string | number): AxiosPromise<VerifyResultVO> {
  return request({
    url: `/system/sign/record/${signatureId}/verify`,
    method: 'get'
  });
}

// 全链验签（bizId 为空时校验该业务类型下全部记录）
export function verifySignatureChain(bizType: string, bizId?: string | number): AxiosPromise<VerifyResultVO> {
  return request({
    url: '/system/sign/record/verify-chain',
    method: 'get',
    params: { bizType, bizId }
  });
}
