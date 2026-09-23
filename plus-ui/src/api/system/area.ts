import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 行政区划视图对象 */
export interface AreaVO {
  pkid?: string | number;
  parentAreaId?: string | number;
  areaId?: string | number;
  areaName?: string;
  layered?: number;
  sort?: number;
  lat?: string;
  lng?: string;
  address?: string;
  remark?: string;
  /** 创建时间（后端 Date，按 yyyy-MM-dd HH:mm:ss 序列化） */
  createTime?: string;
  /** 更新时间（后端 Date，按 yyyy-MM-dd HH:mm:ss 序列化） */
  updateTime?: string;
  children?: AreaVO[];
  /**
   * 是否存在下级（后端回填）。
   * <p>懒加载表格与 el-tree-select 靠它决定是否显示展开箭头；过滤结果集不完整时可能为 null，
   * 前端需回退到"层级 < 5"的启发式。</p>
   */
  hasChildren?: boolean;
}

/**
 * 行政区划查询条件
 * <p>接口返回 {@code R<List<AreaVO>>} 而非分页结构，故不继承 {@link PageQuery}。</p>
 */
export interface AreaQuery {
  areaName?: string;
  layered?: number;
  parentAreaId?: string | number;
  /** 精确匹配行政区划代码（用于按代码取单个节点，如回显上级名称） */
  areaId?: string | number;
}

/** 行政区划表单 */
export interface AreaForm {
  pkid?: string | number;
  parentAreaId?: string | number;
  areaId?: string | number;
  areaName?: string;
  layered?: number;
  sort?: number;
  lat?: string;
  lng?: string;
  address?: string;
  remark?: string;
}

// 查询行政区划列表
export const listArea = (query?: AreaQuery): AxiosPromise<AreaVO[]> => {
  return request({
    url: '/system/area/list',
    method: 'get',
    params: query
  });
};

// 查询行政区划列表（排除节点）
export const listAreaExcludeChild = (areaId: string | number): AxiosPromise<AreaVO[]> => {
  return request({
    url: '/system/area/list/exclude/' + areaId,
    method: 'get'
  });
};

/**
 * 查询指定节点及其整棵子树的行政区划代码集合。
 * <p>用于"选择上级"懒加载下拉做防环：把这些节点置为不可选，用户就选不到自己或自己的下级。
 * 只回一串 id（省份级最大约 1500 个），比 listAreaExcludeChild 返回整棵树轻得多。</p>
 */
export const getSubtreeAreaIds = (areaId: string | number): AxiosPromise<number[]> => {
  return request({
    url: '/system/area/subtreeIds/' + areaId,
    method: 'get'
  });
};

// 查询行政区划详细
export const getArea = (pkid: string | number): AxiosPromise<AreaVO> => {
  return request({
    url: '/system/area/' + pkid,
    method: 'get'
  });
};

// 新增行政区划
export const addArea = (data: AreaForm): AxiosPromise<void> => {
  return request({
    url: '/system/area',
    method: 'post',
    data: data
  });
};

// 修改行政区划
export const updateArea = (data: AreaForm): AxiosPromise<void> => {
  return request({
    url: '/system/area',
    method: 'put',
    data: data
  });
};

// 删除行政区划
export const delArea = (pkid: string | number | Array<string | number>): AxiosPromise<void> => {
  return request({
    url: '/system/area/' + pkid,
    method: 'delete'
  });
};

// 查询行政区划子节点列表
export const listChildren = (query?: AreaQuery): AxiosPromise<AreaVO[]> => {
  return request({
    url: '/system/area/listChildren',
    method: 'get',
    params: query
  });
};

// 获取所有省份
export const getProvinces = (): AxiosPromise<AreaVO[]> => {
  return request({
    url: '/system/area/provinces',
    method: 'get'
  });
};

// 获取下一级
export const getNextLevel = (parentAreaId: string | number): AxiosPromise<AreaVO[]> => {
  return request({
    url: '/system/area/nextLevel/' + parentAreaId,
    method: 'get'
  });
};
