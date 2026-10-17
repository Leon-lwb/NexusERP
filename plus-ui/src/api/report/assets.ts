import request from '@/utils/request';
import type { ReportResource } from './query';
/** 私有资源的安全元数据，打印尺寸另以毫米设置。 */
export interface ReportAssetMetadata {
  /** 请求的不可变版本。 */
  versionId: string;
  /** 授权资源标识。 */
  resourceId: string;
  /** 当前授权资源标题。 */
  name: string;
  /** 固定图片像素宽度。 */
  width: number;
  /** 固定图片像素高度。 */
  height: number;
}
/** 读取指定版本属性，不回退到最新版本或公开存储地址。 */
export const assetMetadata = (versionId: string): Promise<{ data: ReportAssetMetadata }> =>
  request({ url: '/report/assets/versions/' + versionId + '/metadata' });
/** 通过当前认证代理读取私有版本，避免公开地址和URL令牌。 */
export const assetImage = (versionId: string): Promise<Blob> =>
  request({ url: '/report/assets/versions/' + versionId + '/file', responseType: 'blob' });
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
