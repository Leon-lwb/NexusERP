import request from '@/utils/request';

export function listPart(query) {
  return request({
    url: '/system/autocode/part/list',
    method: 'get',
    params: query
  });
}

export function getPart(partId) {
  return request({
    url: '/system/autocode/part/' + partId,
    method: 'get'
  });
}

export function addPart(data) {
  return request({
    url: '/system/autocode/part',
    method: 'post',
    data: data
  });
}

export function updatePart(data) {
  return request({
    url: '/system/autocode/part',
    method: 'put',
    data: data
  });
}

export function delPart(partIds) {
  return request({
    url: '/system/autocode/part/' + partIds,
    method: 'delete'
  });
}
