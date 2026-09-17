import request from '@/utils/request';
import type { AxiosPromise } from 'axios';

/**
 * 印章落位（对应后端 `SealPlacement`，即 `DocSignBo.placements` 的元素）。
 *
 * <p><b>坐标约定（本文件唯一易错点，改前必读）：</b>五个比例全部是<b>归一化到 [0,1] 的小数</b>，
 * 原点在页面<b>左上角</b>，x 向右、y 向下 —— 与浏览器 CSS 坐标系一致，
 * <b>前端不做任何 PDF 用户空间换算</b>。PDF 用户空间的原点在左下角、y 向上，
 * 翻转 y（`y = pageHeight - yRatio * pageHeight - h`）是服务端 `PdfStampGeometry` 的职责，
 * 在前端换算会导致印章整体上移一个自身高度。</p>
 *
 * <ul>
 *   <li>`pageIndex`：页序号，<b>从 0 开始</b>（与 PDFBox `PDDocument#getPage` 一致）</li>
 *   <li>`rotation`：旋转角度（度），<b>正值为屏幕上逆时针</b>，非 0 时绕印章自身中心旋转</li>
 * </ul>
 */
export interface SealPlacement {
  /** 页序号（0 基） */
  pageIndex: number;
  /** 左边缘位置比例（0..1，原点左上） */
  xRatio: number;
  /** 上边缘位置比例（0..1，原点左上，向下为正） */
  yRatio: number;
  /** 印章宽度比例（>0 且 xRatio + wRatio ≤ 1） */
  wRatio: number;
  /** 印章高度比例（>0 且 yRatio + hRatio ≤ 1） */
  hRatio: number;
  /** 旋转角度（度，正值屏幕上逆时针） */
  rotation: number;
  /** 所用印章ID（es_seal.seal_id，雪花 ID 按字符串处理） */
  sealId: string | number;
}

/**
 * 待签文档正文视图对象（对应后端 EsSignDocumentVo）
 * <p>注意：本模块<b>不下发印章原图</b>，这里的 fileUrl/signedUrl 是合同正文自身的地址，
 * 由后端 `@Translation(OSS_ID_TO_URL)` 翻译为可直接访问的地址。</p>
 */
export interface EsSignDocumentVO {
  docId: string | number;
  /** 业务类型（后端 SignBizType，如 SO_CONTRACT / PU_CONTRACT） */
  bizType: string;
  /** 业务单据ID */
  bizId?: string | number;
  /** 上传时的原始正文 OSS ID */
  fileOssId?: string | number;
  /** 原始文件名 */
  fileName?: string;
  /** 文件大小(字节) */
  fileSize?: number;
  /** 上传时 SM3 值（验签比对基准） */
  fileHash?: string;
  /** PDF 页数 */
  pageCount?: number;
  /** 正文版本（同一业务键每次上传 +1） */
  docVersion?: number;
  /** 是否当前生效版本(1是 0否) */
  isCurrent?: string;
  /** 签章后文件 OSS ID（未签署为 null） */
  signedOssId?: string | number;
  /** 签署状态(0未签 1部分签 2已签) */
  signStatus?: string;
  createTime?: string;
  updateTime?: string;
  /** 原始正文访问地址（由 fileOssId 翻译） */
  fileUrl?: string;
  /** 签署后正文访问地址（未签署为 null） */
  signedUrl?: string;
}

/** 签署状态文案（后端 signStatus：0未签 1部分签 2已签） */
export const DOC_SIGN_STATUS_TEXT: Record<string, string> = {
  '0': '未签署',
  '1': '部分签署',
  '2': '已签署'
};

/** 签署状态对应的 el-tag 类型 */
export const DOC_SIGN_STATUS_TAG: Record<string, ElTagType> = {
  '0': 'info',
  '1': 'warning',
  '2': 'success'
};

/** 文档签章入参（对应后端 DocSignBo；<b>不含 bizType/bizId</b>，业务键一律由服务端从文档行取，客户端传了也不会被采用） */
export interface DocSignForm {
  /** 印章落位列表（必填且非空，可含多页多章） */
  placements: SealPlacement[];
  /** 签署含义（必填，写入 PDF 签名的 /Reason） */
  signatureMeaning: string;
  /** 认证方式（必填，见后端 SignAuthMethod，本期支持 LOGIN_ONLY / PASSWORD） */
  authMethod: string;
  /** 二级认证凭证（LOGIN_ONLY 时可为空） */
  credential?: string;
}

/**
 * 文档级验签结果（对应后端 DocumentVerifyVo）
 * <p>只列前端会展示的字段，其余诊断字段（byteRange / eofCount 等）需要时再补。</p>
 */
export interface DocumentVerifyVO {
  signatureId?: string | number;
  docId?: string | number;
  /** 总体是否通过（verdict=OK 且台账摘要一致） */
  passed?: boolean;
  /** 台账登记的签署后文件 SM3 */
  docHash?: string;
  /** 实际算出的文件 SM3 */
  actualDocHash?: string;
  /** 文件内容是否仍与台账一致 */
  hashPassed?: boolean;
  /** 综合结论：OK / MODIFIED_IN_PLACE / REVISION_APPENDED / UNSIGNED_TRAILING_BYTES / TRUNCATED / INVALID_SIGNATURE / NO_SIGNATURE */
  verdict?: string;
  /** CMS/PKCS#7 验签是否通过 */
  cmsPassed?: boolean;
  /** 签名是否覆盖整个文件 */
  coveragePassed?: boolean;
  /** 签名覆盖区间是否被等长改写 */
  inPlaceModified?: boolean;
  /** 签名后是否追加了新修订 */
  revisionAppended?: boolean;
  /** 签名者证书主题（自签证书 CN 为签署人姓名快照） */
  signerSubject?: string;
  /** CMS 摘要算法（如 SHA-256） */
  digestAlgorithm?: string;
  /** PDF 签名域记录的签署时间 */
  signedAt?: string;
  /** 人可读的明细说明 */
  messages?: string[];
}

/**
 * 上传并登记待签文档正文（PDF）
 * <p>multipart 字段名固定为 `file`（本仓库既有约定，改名会让请求 422）；同一业务键重复上传即换版。</p>
 *
 * @param file    待签 PDF 正文
 * @param bizType 业务类型（如 SO_CONTRACT）
 * @param bizId   业务单据ID
 * @returns 新登记的文档ID
 */
export function uploadDocument(file: File, bizType: string, bizId: string | number): AxiosPromise<string | number> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('bizType', bizType);
  formData.append('bizId', String(bizId));
  return request({
    url: '/system/sign/document/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  });
}

/**
 * 按业务键查询文档列表（按版本倒序）
 * @param bizId 为空时返回该业务类型下的全部文档
 */
export function listDocument(bizType: string, bizId?: string | number): AxiosPromise<EsSignDocumentVO[]> {
  return request({
    url: '/system/sign/document/list',
    method: 'get',
    params: { bizType, bizId }
  });
}

/** 查询业务键下当前生效版本的文档（尚无文档时 data 为 null） */
export function getCurrentDocument(bizType: string, bizId: string | number): AxiosPromise<EsSignDocumentVO> {
  return request({
    url: '/system/sign/document/current',
    method: 'get',
    params: { bizType, bizId }
  });
}

/** 获取文档详细信息 */
export function getDocument(docId: string | number): AxiosPromise<EsSignDocumentVO> {
  return request({
    url: '/system/sign/document/' + docId,
    method: 'get'
  });
}

/**
 * 取文档正文「当前应下载」的 OSS ID
 * <p>已签署返回 signedOssId，否则返回上传的原始正文 fileOssId —— 业务方要看的是最后生效的那一份。</p>
 * <p><b>为何是 ossId 而不是直接给流：</b>签章模块位于 common 层，而能把 ossId 解析成字节流的
 * `ISysOssService` 在 ruoyi-system，反向依赖会构成 Maven 循环依赖，
 * 故由前端经既有鉴权端点取流（与印章预览图同一手法）。</p>
 */
export function resolveDocumentOssId(docId: string | number): AxiosPromise<string | number> {
  return request({
    url: `/system/sign/document/${docId}/download`,
    method: 'get'
  });
}

/** 文档签署（盖章 + 数字签名，产出新 PDF）→ 返回新写入的签名ID */
export function signDocument(docId: string | number, data: DocSignForm): AxiosPromise<string | number> {
  return request({
    url: `/system/sign/document/${docId}/sign`,
    method: 'post',
    data
  });
}

/**
 * 文档级验签（校验签署后 PDF 的完整性）
 * <p>验签对象是「某一次签署产生的那个文件」，故路径以 signatureId 为主键。</p>
 */
export function verifyDocumentSignature(signatureId: string | number): AxiosPromise<DocumentVerifyVO> {
  return request({
    url: `/system/sign/document/signature/${signatureId}/verify`,
    method: 'get'
  });
}

/**
 * 文档正文的取流地址（同源，走 VITE_APP_BASE_API 代理）。
 * <p>该端点是鉴权端点，<b>必须在请求头带上 Authorization</b>（见 `fetchOssBytes`）；
 * 直接放进 `<a href>` / `<img src>` 会 401，需要直链的场景请改用 VO 上的
 * `fileUrl` / `signedUrl`（后端 `@Translation` 已翻译成可直接访问的地址）。</p>
 */
export function ossDownloadUrl(ossId: string | number): string {
  return `${import.meta.env.VITE_APP_BASE_API}/resource/oss/download/${ossId}`;
}

/**
 * 取 OSS 对象的二进制内容（同源 + 带鉴权，浏览器端无跨域问题）。
 * <p>用于把 PDF 正文交给 pdfjs 预览：先拿 ossId，再取字节。</p>
 */
export async function fetchOssBytes(ossId: string | number): Promise<ArrayBuffer> {
  // axios 拦截器对 responseType=arraybuffer 的响应直接返回 res.data，故这里按实际类型断言
  const data = (await request({
    url: '/resource/oss/download/' + ossId,
    method: 'get',
    responseType: 'arraybuffer'
  })) as unknown as ArrayBuffer;
  return data;
}

/**
 * 取文档正文二进制（已签署优先），供 pdfjs 预览使用。
 * <p>需要 `system:oss:download` 权限（本模块的 `system:sign:document:list` 不含该权限）；
 * 若调用方已经持有 `EsSignDocumentVO.fileUrl` / `signedUrl`，可直接用该地址，省一次往返。</p>
 */
export async function fetchDocumentBytes(docId: string | number): Promise<ArrayBuffer> {
  const { data: ossId } = await resolveDocumentOssId(docId);
  if (!ossId) {
    throw new Error('该文档没有可用的正文文件');
  }
  return fetchOssBytes(ossId);
}
