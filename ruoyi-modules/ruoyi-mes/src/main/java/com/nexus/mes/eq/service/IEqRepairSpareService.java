package com.nexus.mes.eq.service;

import com.nexus.mes.eq.domain.bo.EqRepairSpareBo;
import com.nexus.mes.eq.domain.vo.EqRepairSpareVo;

import java.util.List;

/**
 * MES维修领用备件明细 Service 业务层接口
 * <p>
 * 维修完成时录入更换备件, 每条明细扣减对应备件库存(REPAIR_USE 流水)。
 *
 * @author Nexus
 */
public interface IEqRepairSpareService {

    /**
     * 查询某维修单的备件领用明细
     *
     * @param repairId 维修记录ID
     * @return 结果列表
     */
    List<EqRepairSpareVo> selectByRepair(Long repairId);

    /**
     * 批量新增维修领用明细(每条扣减备件库存, 原子事务)
     *
     * @param bos 参数 bos
     * @return 结果列表
     */
    List<EqRepairSpareVo> batchAdd(List<EqRepairSpareBo> bos);

    /**
     * 批量删除维修领用明细(不回滚库存, 库存变更以流水为准)
     *
     * @param ids ID数组
     * @return 是否成功
     */
    Boolean deleteByIds(Long[] ids);
}
