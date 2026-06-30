import request from '@/utils/request';

export interface AiImageRecord {
  id?: number;
  prompt: string;
  imageUrl: string;
  size?: string;
  modelId?: number;
  modelName?: string;
  createTime?: string;
}

export interface ImageGenerateRequest {
  prompt: string;
  size?: string;
  /** 图像模型ID（为空使用默认 IMAGE 模型） */
  modelId?: number;
}

/** 生成图片 */
export function generateImage(data: ImageGenerateRequest) {
  return request({
    url: '/ai/image/generate',
    method: 'post',
    data,
    timeout: 60000
  });
}

/** 查询当前用户文生图历史 */
export function listImage(query?: { pageNum?: number; pageSize?: number }) {
  return request({
    url: '/ai/image/list',
    method: 'get',
    params: query
  });
}

/** 删除文生图历史记录 */
export function delImage(ids: Array<number | string>) {
  return request({
    url: `/ai/image/${ids}`,
    method: 'delete'
  });
}
