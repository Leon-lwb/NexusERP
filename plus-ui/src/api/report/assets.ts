import request from '@/utils/request';
import type { ReportResource } from './query';
/** 私有图片目录，资源版本ID始终使用字符串。 */
export const listAssets = (): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: ReportResource[];
}> => request({ url: '/report/assets' });
/** 上传经过服务端像素与格式验证的图片。 */
export const uploadAsset = (
  code: string,
  name: string,
  file: File
): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: {
    /**
     * 固定标识，按字符串传输以保留完整精度
     */
    id: string; /**
     * 当前契约的resourceId配置或状态
     */
    resourceId: string;
  };
}> => {
  const data = new FormData();
  data.append('code', code);
  data.append('name', name);
  data.append('file', file);
  return request({ url: '/report/assets', method: 'post', data });
};
/** 显式改变资产资源状态，使未来读取重新经过ACL。 */
export const assetState = (id: string, revision: number, state: string) =>
  request({ url: '/report/assets/' + id + '/state', method: 'post', data: { revision, state } });
