/**
 * 分页数据响应
 */
export interface TableDataInfo<T = any> {
  /** 总记录数 */
  total: number
  /** 列表数据 */
  rows: T[]
  /** 消息状态码 */
  code: number
  /** 消息内容 */
  msg: string
  /** 其他数据 */
  map?: any
}

/**
 * 基础分页查询参数
 */
export interface PageQuery {
  /** 页码 */
  pageNum: number
  /** 每页数量 */
  pageSize: number
}

/**
 * 基础实体
 */
export interface BaseEntity {
  /** 创建者 */
  createBy?: string
  /** 创建时间 */
  createTime?: string
  /** 更新者 */
  updateBy?: string
  /** 更新时间 */
  updateTime?: string
  /** 备注 */
  remark?: string
  /** 请求参数 */
  params?: any
}
