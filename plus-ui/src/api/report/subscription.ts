import request from '@/utils/request';
import type { ReportParameter } from '@/types/report';
import type { ReportResource } from './query';
/** 固定版本、接收人及日期规则，不接受脚本或外部交付地址。 */
export interface SubscriptionDefinition {
  /**
   * 契约格式版本
   */
  formatVersion: 1;
  /**
   * 绑定的不可变报表发布版本
   */
  reportVersionId: string;
  /**
   * 受控格式
   */
  format: 'PDF' | 'XLSX_DATA' | 'XLSX_LAYOUT';
  /**
   * 当前契约的parameterRules配置或状态
   */
  parameterRules: Record<
    string,
    {
      /**
       * 当前契约的kind配置或状态
       */
      kind: 'FIXED' | 'DATE_RELATIVE' | 'RECIPIENT_ID'; /**
       * 当前契约的value配置或状态
       */
      value: string | null;
    }
  >;
  /**
   * 独立生成和领取文件的接收人列表
   */
  recipients: string[];
  /**
   * 受控cron表达式
   */
  cron: string;
  /**
   * 计划日期计算使用的明确时区
   */
  timezone: string;
  /**
   * 当前契约的dstPolicy配置或状态
   */
  dstPolicy: 'EARLIER_OFFSET';
  /**
   * 允许补跑的时间窗口秒数
   */
  catchupSeconds: number;
  /**
   * 文件就绪时的通知偏好
   */
  notifyOnReady: boolean;
}
/** 每个接收人的独立固定执行记录。 */
export interface SubscriptionExecution {
  /**
   * 固定标识，按字符串传输以保留完整精度
   */
  id: string;
  /**
   * 当前契约的recipient配置或状态
   */
  recipient: string;
  /**
   * 当前契约的job配置或状态
   */
  job: string | null;
  /**
   * 任务或资源状态
   */
  state: string;
  /**
   * 当前契约的error配置或状态
   */
  error: string | null;
  /**
   * 当前契约的scheduledAt配置或状态
   */
  scheduledAt: string;
  /**
   * 当前契约的eventType配置或状态
   */
  eventType: string;
}
/** 主库调度状态及明确的下一真实时刻。 */
export interface SubscriptionSchedule {
  /**
   * 当前契约的subscriptionId配置或状态
   */
  subscriptionId: string;
  /**
   * 不可变发布版本标识
   */
  versionId: string;
  /**
   * 当前契约的enabled配置或状态
   */
  enabled: boolean;
  /**
   * 当前契约的nextAt配置或状态
   */
  nextAt: string;
  /**
   * 当前契约的error配置或状态
   */
  error: string | null;
}
/** 当前接收人的交付目录不透出原始参数。 */
export interface ReportDelivery {
  /**
   * 固定标识，按字符串传输以保留完整精度
   */
  id: string;
  /**
   * 当前契约的subscriptionId配置或状态
   */
  subscriptionId: string;
  /**
   * 当前契约的jobId配置或状态
   */
  jobId: string | null;
  /**
   * 任务或资源状态
   */
  state: string;
  /**
   * 当前契约的notification配置或状态
   */
  notification: string;
  /**
   * 当前契约的expires配置或状态
   */
  expires: string;
  /**
   * 当前契约的error配置或状态
   */
  error: string | null;
}
/** 查询可管理订阅目录。 */
export const listSubscriptions = (): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: ReportResource[];
}> => request({ url: '/report/subscriptions' });
/** 读取当前编辑正文。 */
export const subscriptionDetail = (
  id: string
): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: ReportResource;
}> => request({ url: '/report/subscriptions/' + id });
/** 新建独立订阅草稿。 */
export const createSubscription = (
  code: string,
  title: string,
  definition: SubscriptionDefinition
): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: ReportResource;
}> => request({ url: '/report/subscriptions', method: 'post', data: { code, title, definition } });
/** 按revision保存，不自动升级固定报告。 */
export const saveSubscription = (
  row: ReportResource,
  title: string,
  definition: SubscriptionDefinition
): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: ReportResource;
}> => request({ url: '/report/subscriptions/' + row.id, method: 'put', data: { revision: row.revision, title, definition } });
/** 冻结定义并登记主库调度。 */
export const publishSubscription = (row: ReportResource) =>
  request({ url: '/report/subscriptions/' + row.id + '/publish', method: 'post', data: { revision: row.revision } });
/** 手动运行有独立请求键，不能改写计划时刻。 */
export const runSubscription = (id: string, requestKey: string) =>
  request({ url: '/report/subscriptions/' + id + '/run', method: 'post', data: { requestKey } });
/** 显式停用或恢复。 */
export const subscriptionState = (row: ReportResource, state: string) =>
  request({ url: '/report/subscriptions/' + row.id + '/state', method: 'post', data: { revision: row.revision, state } });
/** 查询逐接收人执行。 */
export const subscriptionExecutions = (
  id: string
): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: SubscriptionExecution[];
}> => request({ url: '/report/subscriptions/' + id + '/executions' });
/** 获取主库nextAt。 */
export const subscriptionSchedule = (
  id: string
): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: SubscriptionSchedule | null;
}> => request({ url: '/report/subscriptions/' + id + '/schedule' });
/** 已固定执行重试，不更换报告版本和参数日期。 */
export const retrySubscriptionExecution = (id: string) => request({ url: '/report/subscriptions/executions/' + id + '/retry', method: 'post' });
/** 取消选定接收人任务。 */
export const cancelSubscriptionExecution = (id: string) => request({ url: '/report/subscriptions/executions/' + id + '/cancel', method: 'post' });
/** 只返回当前角色已安装的开关，不提供凭证。 */
export const subscriptionCapabilities = (): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: {
    /**
     * 当前契约的schedulingEnabled配置或状态
     */
    schedulingEnabled: boolean; /**
     * 当前契约的notificationsEnabled配置或状态
     */
    notificationsEnabled: boolean; /**
     * 当前契约的minSeconds配置或状态
     */
    minSeconds: number; /**
     * 当前契约的maxRecipients配置或状态
     */
    maxRecipients: number;
  };
}> => request({ url: '/report/subscriptions/capabilities' });
/** 固定报告参数契约供原生设计器使用。 */
export const subscriptionSource = (
  versionId: string
): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: {
    /**
     * 固定参数契约
     */
    parameters: ReportParameter[];
  };
}> => request({ url: '/report/subscriptions/sources/' + versionId });
/** 当前用户的私有领取目录。 */
export const listReportDeliveries = (): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: ReportDelivery[];
}> => request({ url: '/report/deliveries' });
/** 领取时重新检查所有权限，并返回正常导出中心任务ID。 */
export const claimReportDelivery = (
  id: string
): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: string;
}> => request({ url: '/report/deliveries/' + id + '/claim', method: 'post' });
