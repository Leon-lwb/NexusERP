import request from '@/utils/request';
import type { ValueType, TypedRow, DatasetField } from '@/types/report';
import type { QueryColumn, QueryFilter } from '@/views/report/query/model';

/** 原生扩展资源元数据，雪花标识保持字符串。 */
export interface ReportResource {
  /**
   * 固定标识，按字符串传输以保留完整精度
   */
  id: string;
  /**
   * 租户隔离标识
   */
  tenant: string;
  /**
   * 当前契约的code配置或状态
   */
  code: string;
  /**
   * 当前契约的title配置或状态
   */
  title: string;
  /**
   * 固定版本的契约正文
   */
  json: string;
  /**
   * 当前契约的revision配置或状态
   */
  revision: number;
  /**
   * 当前契约的publishedVersion配置或状态
   */
  publishedVersion: string | null;
  /**
   * 当前契约的status配置或状态
   */
  status: string;
  /**
   * 资源创建者或当前尝试持有者
   */
  owner: string;
}
/** 只包含逻辑标识的目录，不暴露SQL、连接串或策略列名。 */
export interface QueryCatalog {
  /**
   * 当前契约的version配置或状态
   */
  version: number;
  /**
   * 当前契约的entities配置或状态
   */
  entities: {
    /**
     * 固定标识，按字符串传输以保留完整精度
     */
    id: string;
    /**
     * 用户可见标签
     */
    label: string;
    /**
     * 当前契约的fields配置或状态
     */
    fields: {
      /**
       * 固定标识，按字符串传输以保留完整精度
       */
      id: string; /**
       * 用户可见标签
       */
      label: string; /**
       * 受控类型
       */
      type: ValueType; /**
       * 当前契约的aggregationKeys配置或状态
       */
      aggregationKeys: string[]; /**
       * 当前契约的aggregatable配置或状态
       */
      aggregatable: boolean;
    }[];
  }[];
  /**
   * 当前契约的relations配置或状态
   */
  relations: {
    /**
     * 固定标识，按字符串传输以保留完整精度
     */
    id: string; /**
     * 当前契约的from配置或状态
     */
    from: string; /**
     * 当前契约的to配置或状态
     */
    to: string; /**
     * 当前契约的cardinality配置或状态
     */
    cardinality: string; /**
     * 当前契约的joinType配置或状态
     */
    joinType: string;
  }[];
}
/** 目录查询持久协议一版。 */
export interface QueryDefinition {
  /**
   * 契约格式版本
   */
  formatVersion: number;
  /**
   * 当前契约的catalogVersion配置或状态
   */
  catalogVersion: number;
  /**
   * 当前契约的entity配置或状态
   */
  entity: string;
  /**
   * 当前契约的joins配置或状态
   */
  joins: string[];
  /**
   * 当前契约的fields配置或状态
   */
  fields: Omit<QueryColumn, 'group'>[];
  /**
   * 当前契约的filters配置或状态
   */
  filters: Pick<QueryFilter, 'field' | 'operator' | 'parameter'>[];
  /**
   * 固定参数契约
   */
  parameters: {
    /**
     * 契约名称
     */
    name: string; /**
     * 受控类型
     */
    type: ValueType; /**
     * 是否必须提供该参数
     */
    required: boolean;
  }[];
  /**
   * 当前契约的groupBy配置或状态
   */
  groupBy: string[];
  /**
   * 当前契约的sort配置或状态
   */
  sort: {
    /**
     * 已批准的逻辑字段
     */
    field: string; /**
     * 当前契约的descending配置或状态
     */
    descending: boolean;
  }[];
}
/** 查看当前身份可见的固定目录。 */
export const queryCatalogs = (): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: QueryCatalog[];
}> => request({ url: '/report/queries/catalog' });
/** 列出当前有权限的受控查询。 */
export const listQueries = (): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: ReportResource[];
}> => request({ url: '/report/queries' });
/** 创建查询草稿，参数和字段由服务器检查。 */
export const createQuery = (
  code: string,
  title: string,
  model: QueryDefinition
): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: ReportResource;
}> => request({ url: '/report/queries', method: 'post', data: { code, title, model } });
/** 按当前修订保存，不覆盖并发修改。 */
export const saveQuery = (
  resource: ReportResource,
  title: string,
  model: QueryDefinition
): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: ReportResource;
}> => request({ url: '/report/queries/' + resource.id, method: 'put', data: { revision: resource.revision, title, model } });
/** 发布为不可变查询并生成可供设计器绑定的数据集。 */
export const publishQuery = (
  resource: ReportResource
): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: {
    /**
     * 当前契约的version配置或状态
     */
    version: {
      /**
       * 固定标识，按字符串传输以保留完整精度
       */
      id: string;
    }; /**
     * 当前契约的dataset配置或状态
     */
    dataset: {
      /**
       * 固定标识，按字符串传输以保留完整精度
       */
      id: string; /**
       * 当前契约的code配置或状态
       */
      code: string; /**
       * 当前契约的publishedVersion配置或状态
       */
      publishedVersion: string;
    };
  };
}> => request({ url: '/report/queries/' + resource.id + '/publish', method: 'post', data: { revision: resource.revision } });
/** 只读获取精确类型预览，允许快速刷新，不在浏览器计算聚合值。 */
export const previewQuery = (
  model: QueryDefinition,
  parameters: Record<string, string>
): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: {
    /**
     * 精确类型的数据行
     */
    rows: TypedRow[]; /**
     * 当前契约的total配置或状态
     */
    total: number; /**
     * 当前契约的dataTime配置或状态
     */
    dataTime: string; /**
     * 当前契约的fields配置或状态
     */
    fields: DatasetField[];
  };
}> => request({ url: '/report/queries/preview', method: 'post', headers: { repeatSubmit: false }, data: { model, parameters } });
/** 显式下线或归档查询及其运行依赖。 */
export const queryState = (
  resource: ReportResource,
  state: string
): Promise<{
  /**
   * 同一固定快照投影出的数据
   */
  data: ReportResource;
}> => request({ url: '/report/queries/' + resource.id + '/state', method: 'post', data: { revision: resource.revision, state } });
