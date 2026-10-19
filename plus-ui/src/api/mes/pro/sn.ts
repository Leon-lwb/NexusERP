import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** SN视图对象 */
export interface ProSnVO {
  identityId?: string;
  identityVersion?: string;
  handoffStatus?: string;
  canonicalSnCode?: string | null;
  localSnCode?: string | null;
  snId: number;
  snCode: string;
  mesWoId: number;
  woNo?: string;
  cardId?: number;
  cardNo?: string;
  materialId: number;
  materialCode?: string;
  materialName?: string;
  currentStepSeq?: number;
  snStatus: string;
  parentSnId?: number;
  parentSnCode?: string;
  source?: string;
  productionDate?: string;
  remark?: string;
  /** 追溯辅助(transient) */
  children?: ProSnVO[];
  steps?: any[];
}

/** SN查询条件 */
export interface ProSnQuery extends PageQuery {
  snCode?: string;
  snStatus?: string;
  source?: string;
  mesWoId?: number | string;
  cardId?: number;
  materialId?: number;
  parentSnId?: number;
}

/** 追溯工序节点 */
export interface SnTraceStep {
  stepRecordId: number;
  sequenceNum: number;
  processCode?: string;
  processName?: string;
  workcenterCode?: string;
  stepStatus: string;
  operatorId?: number;
  operatorName?: string;
  goodQty?: number;
  defectQty?: number;
  scrapQty?: number;
  actualStartTime?: string;
  actualEndTime?: string;
}

/** 追溯结果 */
export interface SnTraceResult {
  /** 不可变消耗原分录；缺字段不当作零消耗。 */
  componentConsumptions?: Record<string, any>[];
  sn: ProSnVO;
  forwardSteps: SnTraceStep[];
  children: ProSnVO[];
  parentChain: ProSnVO[];
}

/** 分页查询SN列表 */
export const listSn = (query?: ProSnQuery): AxiosPromise<ProSnVO[]> => request({ url: '/mes/pro/sn/list', method: 'get', params: query });

/** 获取SN详情 */
export const getSn = (snId: string | number): AxiosPromise<ProSnVO> => request({ url: `/mes/pro/sn/${snId}`, method: 'get' });

/** 根据SN编码查询 */
export const getSnByCode = (snCode: string): AxiosPromise<ProSnVO> => request({ url: `/mes/pro/sn/by-code/${snCode}`, method: 'get' });

/** 追溯树(前向+后向) */
export const traceSn = (snId: string | number): AxiosPromise<SnTraceResult> => request({ url: `/mes/pro/sn/trace/${snId}`, method: 'get' });

/** 从流转卡批量生成SN */
export const generateSnForCard = (cardId: number, quantity: number, initialStatus?: string) =>
  request({ url: '/mes/pro/sn/generate', method: 'post', params: { cardId, quantity, initialStatus } });

/** 手动创建单个SN */
export const createSn = (
  mesWoId: number | undefined,
  cardId: number | undefined,
  materialId: number,
  snCode: string,
  parentSnId?: number,
  remark?: string
) => request({ url: '/mes/pro/sn', method: 'post', params: { mesWoId, cardId, materialId, snCode, parentSnId, remark } });

/** SN完工 */
export const completeSn = (snId: string | number) => request({ url: `/mes/pro/sn/complete/${snId}`, method: 'put' });

/** SN报废 */
export const scrapSn = (snId: string | number, remark?: string) => request({ url: `/mes/pro/sn/scrap/${snId}`, method: 'put', params: { remark } });

/** SN发货 */
export const shipSn = (snId: string | number) => request({ url: `/mes/pro/sn/ship/${snId}`, method: 'put' });
