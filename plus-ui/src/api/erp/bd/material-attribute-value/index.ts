import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { MaterialAttributeValueVO, MaterialAttributeValueForm } from './types';

/** 按物料查询属性值列表 */
export const listMaterialAttrValue = (materialId: string | number): AxiosPromise<MaterialAttributeValueVO[]> => {
  return request({
    url: '/erp/bd/material-attr-value/' + materialId,
    method: 'get'
  });
};

/** 按物料整体保存属性值（替换语义，含必填校验） */
export const saveMaterialAttrValue = (materialId: string | number, values: MaterialAttributeValueForm[]) => {
  return request({
    url: '/erp/bd/material-attr-value/' + materialId,
    method: 'post',
    data: values
  });
};

/** 按物料删除全部属性值 */
export const delMaterialAttrValue = (materialId: string | number) => {
  return request({
    url: '/erp/bd/material-attr-value/' + materialId,
    method: 'delete'
  });
};
