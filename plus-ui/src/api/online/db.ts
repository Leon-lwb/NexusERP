import request from '@/utils/request';

// 表设计器相关 API - 适配到新的统一接口

/**
 * 保存表设计
 */
export function saveTableDesign(data: any) {
  return request({
    url: '/tool/gen/design',
    method: 'post',
    data
  });
}

/**
 * 生成 SQL
 */
export function generateSql(data: any) {
  return request({
    url: '/tool/gen/design/generateSql',
    method: 'post',
    data
  });
}

/**
 * 创建表
 */
export function createTable(data: any) {
  return request({
    url: '/tool/gen/design/createTable',
    method: 'post',
    data
  });
}

/**
 * 导入到代码生成(online设计)
 */
export function importToGenTable(data: any) {
  return request({
    url: '/tool/gen/design/importToGen',
    method: 'post',
    data
  });
}

/**
 * 获取设计列表
 */
export function getDesignList(params?: any) {
  return request({
    url: '/tool/gen/design/list',
    method: 'get',
    params
  });
}

/**
 * 获取设计详情
 */
export function getDesignDetail(id: string) {
  return request({
    url: `/tool/gen/design/${id}`,
    method: 'get'
  });
}

/**
 * 删除设计
 */
export function deleteDesign(id: string) {
  return request({
    url: `/tool/gen/design/${id}`,
    method: 'delete'
  });
}
