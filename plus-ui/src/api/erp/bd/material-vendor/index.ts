import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { MaterialVendorQuery, MaterialVendorVO, MaterialVendorForm } from './types';

/**
 * 分页查询物料-供应商关系列表
 *
 * @param query 查询参数
 * @returns 物料-供应商关系分页列表
 */
export const listMaterialVendor = (query: MaterialVendorQuery): AxiosPromise<MaterialVendorVO[]> => {
  return request({
    url: '/erp/bd/materialVendor/list',
    method: 'get',
    params: query
  });
};

/**
 * 根据ID查询物料-供应商关系详情
 *
 * @param relationId 关系ID
 * @returns 物料-供应商关系详情
 */
export const getMaterialVendor = (relationId: string | number): AxiosPromise<MaterialVendorVO> => {
  return request({
    url: '/erp/bd/materialVendor/' + relationId,
    method: 'get'
  });
};

/**
 * 新增物料-供应商关系
 *
 * @param data 物料-供应商关系表单数据
 * @returns 操作结果
 */
export const addMaterialVendor = (data: MaterialVendorForm) => {
  return request({
    url: '/erp/bd/materialVendor',
    method: 'post',
    data
  });
};

/**
 * 修改物料-供应商关系
 *
 * @param data 物料-供应商关系表单数据
 * @returns 操作结果
 */
export const updateMaterialVendor = (data: MaterialVendorForm) => {
  return request({
    url: '/erp/bd/materialVendor',
    method: 'put',
    data
  });
};

/**
 * 删除物料-供应商关系
 *
 * @param relationId 关系ID（支持批量删除）
 * @returns 操作结果
 */
export const delMaterialVendor = (relationId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/bd/materialVendor/' + relationId,
    method: 'delete'
  });
};
