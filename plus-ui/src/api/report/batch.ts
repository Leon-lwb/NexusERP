import request from '@/utils/request';
import type { ReportResource } from './query';
/** 固定版本单据项；参数为精确字符串。 */
export interface BatchDocument {
  /**
   * 绑定的不可变报表发布版本
   */
  reportVersionId: string;
  /**
   * 固定参数契约
   */
  parameters: Record<string, string>;
}
/** 当前可交付任务状态，撤权项不会返回jobId。 */
export interface BatchItem {
  /**
   * 当前契约的number配置或状态
   */
  number: number;
  /**
   * 绑定的不可变报表发布版本
   */
  reportVersionId: string;
  /**
   * 当前契约的jobId配置或状态
   */
  jobId: string | null;
  /**
   * 任务或资源状态
   */
  state: string;
  /**
   * 当前契约的error配置或状态
   */
  error: string | null;
  /** 是否允许在固定快照与有效期内重试原任务。 */
  canRetry: boolean;
  /** 当前可见轮次，作为重试的预期轮次传回。 */
  retryRound: number;
  /** 原任务固定有效期，未入队项为null。 */
  expires: string | null;
}
/** 可访问批次目录。 */
export const listBatches = (): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: ReportResource[];
}> => request({ url: '/report/batches' });
/** 创建不可变单据集合。 */
export const createBatch = (
  code: string,
  title: string,
  documents: BatchDocument[]
): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: ReportResource;
}> => request({ url: '/report/batches', method: 'post', data: { code, title, documents } });
/** 逐项鉴权和幂等入队。 */
export const startBatch = (id: string) => request({ url: '/report/batches/' + id + '/start', method: 'post' });
/** 刷新当前用户仍可领取的各项状态。 */
export const batchItems = (
  id: string
): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: BatchItem[];
}> => request({ url: '/report/batches/' + id + '/items' });

/** 所有项成功后按固定顺序领取合并PDF，权限和有效期由后端重新检查。 */
export const batchPdf = (id: string): Promise<Blob> =>
  request({ url: '/report/batches/' + id + '/pdf', method: 'get', responseType: 'blob', timeout: 120000 });
