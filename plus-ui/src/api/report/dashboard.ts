import request from '@/utils/request';
import type { TypedValue, ReportParameter, DatasetField } from '@/types/report';
import type { ReportResource } from './query';
/** 组件只引用固定报告版本及声明过的参数。 */
export interface DashboardWidget {
  /**
   * 固定标识，按字符串传输以保留完整精度
   */
  id: string;
  /**
   * 受控类型
   */
  type: 'METRIC' | 'BAR' | 'LINE' | 'PIE' | 'TABLE' | 'PIVOT';
  /**
   * 当前契约的componentVersion配置或状态
   */
  componentVersion: number;
  /**
   * 当前契约的title配置或状态
   */
  title: string;
  /**
   * 绑定的不可变报表发布版本
   */
  reportVersionId: string | null;
  /**
   * 当前契约的parameterBindings配置或状态
   */
  parameterBindings: Record<string, string>;
  /**
   * 当前契约的categoryField配置或状态
   */
  categoryField: string | null;
  /**
   * 当前契约的valueField配置或状态
   */
  valueField: string | null;
  /**
   * 白名单聚合函数
   */
  function: string | null;
  /**
   * 当前契约的comparison配置或状态
   */
  comparison: string | null;
  /**
   * 当前契约的startParameter配置或状态
   */
  startParameter: string | null;
  /**
   * 当前契约的endParameter配置或状态
   */
  endParameter: string | null;
  /**
   * 当前契约的layout配置或状态
   */
  layout: {
    /**
     * 当前契约的x配置或状态
     */
    x: number; /**
     * 当前契约的y配置或状态
     */
    y: number; /**
     * 布局宽度
     */
    width: number; /**
     * 布局高度
     */
    height: number; /**
     * 当前契约的mobileOrder配置或状态
     */
    mobileOrder: number;
  };
  /**
   * 当前契约的links配置或状态
   */
  links: {
    /**
     * 当前契约的targetWidget配置或状态
     */
    targetWidget: string; /**
     * 当前契约的sourceField配置或状态
     */
    sourceField: string; /**
     * 当前契约的targetParameter配置或状态
     */
    targetParameter: string;
  }[];
}
/** 仪表盘独立定义格式，禁止脚本或HTML。 */
export interface DashboardDefinition {
  /**
   * 契约格式版本
   */
  formatVersion: 1;
  /**
   * 当前契约的title配置或状态
   */
  title: string;
  /**
   * 固定参数契约
   */
  parameters: ReportParameter[];
  /**
   * 当前契约的widgets配置或状态
   */
  widgets: DashboardWidget[];
  /**
   * 当前契约的refreshSeconds配置或状态
   */
  refreshSeconds: number;
}
/** 逐组件的服务器结果与明确的一致性时间。 */
export interface DashboardView {
  /**
   * 固定标识，按字符串传输以保留完整精度
   */
  id: string;
  /**
   * 受控类型
   */
  type: DashboardWidget['type'];
  /**
   * 当前契约的title配置或状态
   */
  title: string;
  /**
   * 当前契约的layout配置或状态
   */
  layout: DashboardWidget['layout'];
  /**
   * 绑定的不可变报表发布版本
   */
  reportVersionId: string;
  /**
   * 当前契约的reportId配置或状态
   */
  reportId: string | null;
  /**
   * 当前契约的datasetCode配置或状态
   */
  datasetCode: string | null;
  /**
   * 当前契约的dataTime配置或状态
   */
  dataTime: string | null;
  /**
   * 当前契约的consistency配置或状态
   */
  consistency: string | null;
  /**
   * 当前契约的total配置或状态
   */
  total: number;
  /**
   * 同一固定快照投影出的数据
   */
  data: unknown;
  /**
   * 当前契约的error配置或状态
   */
  error: string | null;
}
/** 编辑器获得固定报告已授权字段和参数。 */
export interface DashboardSource {
  /**
   * 不可变发布版本标识
   */
  versionId: string;
  /**
   * 当前契约的reportId配置或状态
   */
  reportId: string;
  /**
   * 当前契约的title配置或状态
   */
  title: string;
  /**
   * 当前契约的fields配置或状态
   */
  fields: DatasetField[];
  /**
   * 固定参数契约
   */
  parameters: ReportParameter[];
}
/** 联动只沿发布批准的映射，业务值保持TypedValue。 */
export interface DashboardSelection {
  /**
   * 当前契约的sourceWidget配置或状态
   */
  sourceWidget: string;
  /**
   * 当前契约的targetWidget配置或状态
   */
  targetWidget: string;
  /**
   * 当前契约的sourceField配置或状态
   */
  sourceField: string;
  /**
   * 当前契约的value配置或状态
   */
  value: TypedValue;
}
/** 查询可访问仪表盘目录。 */
export const listDashboards = (): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: ReportResource[];
}> => request({ url: '/report/dashboards' });
/** 获取可编辑定义。 */
export const dashboardDetail = (
  id: string
): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: ReportResource;
}> => request({ url: '/report/dashboards/' + id });
/** 获得已发布布局及参数，撤权组件契约被屏蔽。 */
export const dashboardPublished = (
  id: string
): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: DashboardDefinition;
}> => request({ url: '/report/dashboards/' + id + '/published' });
/** 刷新各组件；局部错误由服务端返回。 */
export const renderDashboard = (
  id: string,
  parameters: Record<string, string>,
  selections: DashboardSelection[]
): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: {
    /**
     * 不可变发布版本标识
     */
    versionId: string; /**
     * 当前契约的refreshSeconds配置或状态
     */
    refreshSeconds: number; /**
     * 当前契约的widgets配置或状态
     */
    widgets: DashboardView[];
  };
}> => request({ url: '/report/dashboards/' + id + '/render', method: 'post', data: { parameters, selections }, timeout: 120000 });
/** 新建独立仪表盘草稿。 */
export const createDashboard = (
  code: string,
  title: string,
  definition: DashboardDefinition
): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: ReportResource;
}> => request({ url: '/report/dashboards', method: 'post', data: { code, title, definition } });
/** 按revision更新草稿。 */
export const saveDashboard = (
  row: ReportResource,
  title: string,
  definition: DashboardDefinition
): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: ReportResource;
}> => request({ url: '/report/dashboards/' + row.id, method: 'put', data: { revision: row.revision, title, definition } });
/** 发布固定组件及报告契约。 */
export const publishDashboard = (row: ReportResource) =>
  request({ url: '/report/dashboards/' + row.id + '/publish', method: 'post', data: { revision: row.revision } });
/** 查询个人收藏，收藏不改变访问权限。 */
export const dashboardFavorites = (): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: string[];
}> => request({ url: '/report/dashboards/favorites' });
/** 显式修改收藏。 */
export const favoriteDashboard = (id: string, enabled: boolean) =>
  request({ url: '/report/dashboards/' + id + '/favorite', method: 'put', params: { enabled } });
/** 读取固定历史版本的字段，不能自动升级当前报告。 */
export const dashboardSource = (
  versionId: string
): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: DashboardSource;
}> => request({ url: '/report/dashboards/sources/' + versionId });
