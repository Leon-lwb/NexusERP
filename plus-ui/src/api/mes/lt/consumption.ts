import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import type { LtLotVO } from './lot';

/** 工单批次消耗视图对象 */
export interface LtWoConsumptionVO {
  consumptionId: number;
  woId: number;
  woNo?: string;
  lotId: number;
  lotCode?: string;
  materialId: number;
  materialCode?: string;
  materialName?: string;
  consumedQty: number;
  consumedTime: string;
  operatorId?: number;
  operatorName?: string;
  remark?: string;
}

/** 工单批次消耗查询条件 */
export interface LtWoConsumptionQuery extends PageQuery {
  woId?: number;
  lotId?: number;
  materialId?: number;
  operatorId?: number;
}

/** 工单批次消耗表单 */
export interface LtWoConsumptionForm {
  consumptionId?: number;
  woId: number;
  woNo?: string;
  lotId: number;
  lotCode?: string;
  materialId: number;
  materialCode?: string;
  materialName?: string;
  consumedQty: number;
  operatorId?: number;
  operatorName?: string;
  remark?: string;
}

/** 分页查询工单批次消耗列表 */
export const listConsumption = (query?: LtWoConsumptionQuery): AxiosPromise<LtWoConsumptionVO[]> =>
  request({ url: '/mes/lt/consumption/list', method: 'get', params: query });

/** 获取消耗记录详情 */
export const getConsumption = (consumptionId: number | string): AxiosPromise<LtWoConsumptionVO> =>
  request({ url: `/mes/lt/consumption/${consumptionId}`, method: 'get' });

/** 新增工单批次消耗(联动扣减批次数量) */
export const addConsumption = (data: LtWoConsumptionForm) => request({ url: '/mes/lt/consumption', method: 'post', data });

/** 批量删除消耗记录 */
export const delConsumption = (consumptionIds: number | number[]) => request({ url: `/mes/lt/consumption/${consumptionIds}`, method: 'delete' });

/** 按工单ID查询所有批次消耗 */
export const listConsumptionByWo = (woId: number | string): AxiosPromise<LtWoConsumptionVO[]> =>
  request({ url: `/mes/lt/consumption/by-wo/${woId}`, method: 'get' });

/** 按批次ID查询所有消耗记录 */
export const listConsumptionByLot = (lotId: number | string): AxiosPromise<LtWoConsumptionVO[]> =>
  request({ url: `/mes/lt/consumption/by-lot/${lotId}`, method: 'get' });

/** 工单 FEFO 领料明细行(单条领料) */
export interface LtWoPickLine {
  lotId: number;
  consumedQty: number;
  remark?: string;
}

/** 工单 FEFO 领料请求 */
export interface LtWoPickBO {
  woId: number;
  materialId: number;
  /** 可空; 传入则为该 SN 写 SN↔批次绑定(组件消费追溯) */
  snId?: number;
  picks: LtWoPickLine[];
}

/** 工单 FEFO 推荐: 按到期升序返回可用批次(两步领料第一步) */
export const recommendForWo = (woId: number | string, materialId: number | string, requiredQty?: number): AxiosPromise<LtLotVO[]> =>
  request({ url: `/mes/lt/consumption/fefo/${woId}/${materialId}`, method: 'get', params: { requiredQty } });

/** 工单 FEFO 领料确认: 逐条扣减批次数量 + 写消耗记录(+可选 SN↔批次绑定) */
export const confirmPick = (data: LtWoPickBO) => request({ url: '/mes/lt/consumption/pick', method: 'post', data });
