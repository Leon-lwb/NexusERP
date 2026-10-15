import type { ValueType } from '@/types/report';

/** 仅包含逻辑目录字段和受控聚合的列编辑状态。 */
export interface QueryColumn {
  /**
   * 已批准的逻辑字段
   */
  field: string;
  /**
   * 当前契约的alias配置或状态
   */
  alias: string;
  /**
   * 当前契约的aggregate配置或状态
   */
  aggregate: string | null;
  /**
   * 当前契约的group配置或状态
   */
  group: boolean;
}
/** 筛选编辑状态将值声明为参数，不保存SQL或原始条件片段。 */
export interface QueryFilter {
  /**
   * 已批准的逻辑字段
   */
  field: string;
  /**
   * 当前契约的operator配置或状态
   */
  operator: string;
  /**
   * 当前契约的parameter配置或状态
   */
  parameter: string | null;
  /**
   * 受控类型
   */
  type: ValueType;
  /**
   * 是否必须提供该参数
   */
  required: boolean;
}
/** 生成查询模型一版，编辑器状态不进入持久协议。 */
export function queryModel(
  catalogVersion: number,
  entity: string,
  joins: string[],
  columns: QueryColumn[],
  filters: QueryFilter[],
  sort: {
    /**
     * 已批准的逻辑字段
     */
    field: string; /**
     * 当前契约的descending配置或状态
     */
    descending: boolean;
  }[]
) {
  const parameters = new Map<
    string,
    {
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
    }
  >();
  for (const filter of filters)
    if (filter.parameter) {
      const prior = parameters.get(filter.parameter);
      if (prior && (prior.type !== filter.type || prior.required !== filter.required)) throw Error('相同参数的类型与必填约束必须一致');
      parameters.set(filter.parameter, { name: filter.parameter, type: filter.type, required: filter.required });
    }
  return {
    formatVersion: 1,
    catalogVersion,
    entity,
    joins: [...joins],
    fields: columns.map(({ field, alias, aggregate }) => ({ field, alias, aggregate })),
    filters: filters.map(({ field, operator, parameter }) => ({ field, operator, parameter })),
    parameters: [...parameters.values()],
    groupBy: columns.filter((column) => column.group).map((column) => column.field),
    sort: [...sort]
  };
}
/** 参数保持字符串，空值交由服务端按声明解析，避免ID和十进制丢失精度。 */
export function queryParameters(values: Record<string, string>): Record<string, string> {
  return Object.fromEntries(Object.entries(values).filter(([, value]) => value !== ''));
}
