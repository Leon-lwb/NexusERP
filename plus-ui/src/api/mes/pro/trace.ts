import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import type { ProWorkOrderBomLineVO } from './types';

/**
 * 追溯结果中的 SN 信息（后端 TraceabilityResult.SnInfo，FE-MES-03）
 */
export interface TraceabilitySnInfo {
  snId: number;
  snCode?: string;
  mesWoId?: number;
  woNo?: string;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  snStatus?: string;
  parentSnId?: number;
  parentSnCode?: string;
}

/**
 * 组件追溯结果（后端 TraceabilityResult）
 * <p>backward：成品 SN/工单 → 所需组件清单（MBOM 快照）+ 实际装配组件 SN；
 * forward：组件物料/SN → 消费该组件的成品工单 + 父 SN 链。</p>
 */
export interface TraceabilityResult {
  mesWoId?: number;
  woNo?: string;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  woStatus?: string;
  /** 反向追溯：MBOM 快照要求的组件清单 */
  requiredComponents?: ProWorkOrderBomLineVO[];
  /** 反向追溯：实际装配的组件 SN 列表 */
  assembledComponentSns?: TraceabilitySnInfo[];
  /** 正向追溯：消费该组件的工单列表 */
  consumingWorkOrders?: TraceabilityResult[];
  /** 正向追溯：父 SN 链 */
  parentSnChain?: TraceabilitySnInfo[];
}

/**
 * 反向追溯：成品工单/SN → 组件清单 + 实际装配组件 SN
 * <p>后端：`GET /mes/pro/trace/backward`，权限码 `mes:pro:trace:query`。</p>
 */
export const traceBackward = (mesWoId: number | string, snCode?: string): AxiosPromise<TraceabilityResult> =>
  request({ url: '/mes/pro/trace/backward', method: 'get', params: { mesWoId, snCode } });

/**
 * 正向追溯：组件物料/SN → 消费该组件的成品工单
 * <p>后端：`GET /mes/pro/trace/forward`，权限码 `mes:pro:trace:query`。
 * 两个入参均为可选，至少给一个，否则后端返回空列表。</p>
 */
export const traceForward = (params: { componentMaterialId?: number | string; componentSnCode?: string }): AxiosPromise<TraceabilityResult[]> =>
  request({ url: '/mes/pro/trace/forward', method: 'get', params });
