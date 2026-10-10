import request from '@/utils/request';
import type { AxiosPromise } from 'axios';
import type { SpareReturnForm, SpareReturnQuery, SpareReturnVo } from './types';

/**
 * MES 备件退库单 API（MES-EQ-04 前端契约冻结）
 *
 * <p>权限码：list / query / add / edit / remove / confirm 分别对应
 * {@code mes:eq:spare:return:list|query|add|edit|remove|confirm}。</p>
 */

/** 分页查询备件退库单；响应雪花 ID 保持字符串，禁止 Number() 转换 */
export const listSpareReturn = (query: SpareReturnQuery): AxiosPromise<SpareReturnVo[]> =>
  request({ url: '/mes/eq/spare-return/list', method: 'get', params: query });

/** 获取备件退库单详情（含明细行） */
export const getSpareReturn = (returnId: string | number): AxiosPromise<SpareReturnVo> =>
  request({ url: `/mes/eq/spare-return/${returnId}`, method: 'get' });

/** 新增备件退库单（草稿） */
export const addSpareReturn = (data: SpareReturnForm): AxiosPromise<SpareReturnVo> => request({ url: '/mes/eq/spare-return', method: 'post', data });

/** 修改备件退库单（仅 DRAFT） */
export const updateSpareReturn = (data: SpareReturnForm): AxiosPromise<void> => request({ url: '/mes/eq/spare-return', method: 'put', data });

/** 删除备件退库单（仅 DRAFT） */
export const delSpareReturn = (returnIds: string | number | (string | number)[]) =>
  request({ url: `/mes/eq/spare-return/${returnIds}`, method: 'delete' });

/**
 * 确认退库（仅 DRAFT，确认后不可撤销，按入库方向过账到 ERP 库存）
 * <p>关闭统一错误 toast，由调用方原样展示后端拒绝原因（避免重复提示）。</p>
 */
export const confirmSpareReturn = (returnId: string | number): AxiosPromise<boolean> =>
  request({ url: `/mes/eq/spare-return/${returnId}/confirm`, method: 'put', headers: { ignoreErrorMsg: 'true' } });
