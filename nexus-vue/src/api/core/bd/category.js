import request from '@/utils/request'

// 查询物料产品分类列表
export function listCategory(query) {
  return request({
    url: '/bd/category/list',
    method: 'get',
    params: query
  })
}

// 查询物料产品分类详细
export function getCategory(categoryId) {
  return request({
    url: '/bd/category/' + categoryId,
    method: 'get'
  })
}

// 新增物料产品分类
export function addCategory(data) {
  return request({
    url: '/bd/category',
    method: 'post',
    data: data
  })
}

// 修改物料产品分类
export function updateCategory(data) {
  return request({
    url: '/bd/category',
    method: 'put',
    data: data
  })
}

// 删除物料产品分类
export function delCategory(categoryId) {
  return request({
    url: '/bd/category/' + categoryId,
    method: 'delete'
  })
}
