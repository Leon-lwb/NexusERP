import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/**
 * 维修领用备件明细视图对象（eq_repair_spare，FE-MES-03）
 * <p>后端契约：EqRepairSpareVo（EqRepairSpareController `/mes/eq/repair-spare`）。</p>
 */
export interface EqRepairSpareVO {
  id: number;
  repairId: number;
  spareId: number;
  spareCode?: string;
  spareName?: string;
  spec?: string;
  unit?: string;
  quantity?: number;
  remark?: string;
}

/**
 * 查询某维修单的备件领用明细
 * <p>权限码 `mes:eq:repair:query`（与维修列表查看同码，无需新增种子）。</p>
 */
export const listRepairSpareByRepair = (repairId: number | string): AxiosPromise<EqRepairSpareVO[]> =>
  request({ url: `/mes/eq/repair-spare/repair/${repairId}`, method: 'get' });

/**
 * 删除维修领用明细（录入纠错用，权限码 `mes:eq:repair:complete`）
 * <p>注意：删除不会自动回补已扣减的备件库存，纠错后需在备件台账核对。</p>
 */
export const delRepairSpare = (ids: number | number[]) => request({ url: `/mes/eq/repair-spare/${ids}`, method: 'delete' });
