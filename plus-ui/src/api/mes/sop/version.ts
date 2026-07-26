import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** SOP版本视图对象 */
export interface SopVersionVO {
  versionId: number;
  sopId: number;
  versionNo: string;
  fileOssId: string;
  fileUrl?: string;
  fileType?: string;
  changeLog?: string;
  publishedTime?: string;
  publishedBy?: number;
  publishedByName?: string;
  status: string;
  remark?: string;
  createTime?: string;
}

/** SOP版本查询条件 */
export interface SopVersionQuery extends PageQuery {
  sopId?: number;
  versionNo?: string;
  status?: string;
}

/** 分页查询版本列表 */
export const listVersion = (query?: SopVersionQuery): AxiosPromise<SopVersionVO[]> =>
  request({ url: '/mes/sop/version/list', method: 'get', params: query });

/** 查询SOP的版本列表(版本历史) */
export const listVersionBySop = (sopId: number | string): AxiosPromise<SopVersionVO[]> =>
  request({ url: `/mes/sop/version/by-sop/${sopId}`, method: 'get' });

/** 获取版本详情 */
export const getVersion = (versionId: number | string): AxiosPromise<SopVersionVO> =>
  request({ url: `/mes/sop/version/${versionId}`, method: 'get' });

/** 批量删除版本(守卫:CURRENT 版本不可删) */
export const delVersion = (versionIds: number | number[]) => request({ url: `/mes/sop/version/${versionIds}`, method: 'delete' });
