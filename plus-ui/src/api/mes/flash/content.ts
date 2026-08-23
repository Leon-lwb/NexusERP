import request from '@/utils/request';

/** 配置文件档案VO */
export interface MesContentFileVO {
  contentFileId: number | string;
  contentCode: string;
  contentName: string;
  contentType: string;
  materialId: number | string;
  materialCode: string;
  materialName: string;
  version: string;
  ossId: number | string;
  fileMd5: string;
  fileSize: number;
  releaseNote: string;
  status: string;
  isDefault: string;
  publishTime: string;
  createTime: string;
}

export interface MesContentFileForm {
  contentFileId?: number | string;
  contentName: string;
  contentType?: string;
  materialId: number | string;
  materialCode?: string;
  materialName?: string;
  version: string;
  ossId?: number | string;
  fileMd5?: string;
  fileSize?: number;
  releaseNote?: string;
  remark?: string;
}

export interface MesContentFileQuery extends PageQuery {
  contentName?: string;
  materialId?: number | string;
  status?: string;
}

export function listContent(query: MesContentFileQuery) {
  return request({ url: '/mes/flash/content/list', method: 'get', params: query });
}
export function getContent(contentFileId: number | string) {
  return request({ url: `/mes/flash/content/${contentFileId}`, method: 'get' });
}
export function addContent(data: MesContentFileForm) {
  return request({ url: '/mes/flash/content', method: 'post', data });
}
export function updateContent(data: MesContentFileForm) {
  return request({ url: '/mes/flash/content', method: 'put', data });
}
export function delContent(ids: number | string | Array<number | string>) {
  return request({ url: `/mes/flash/content/${ids}`, method: 'delete' });
}
/** 发布: DRAFT -> RELEASED */
export function releaseContent(contentFileId: number | string) {
  return request({ url: `/mes/flash/content/release/${contentFileId}`, method: 'put' });
}
