package com.nexus.mes.lt.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.lt.domain.bo.LtWoConsumptionBo;
import com.nexus.mes.lt.domain.bo.LtWoPickBo;
import com.nexus.mes.lt.domain.vo.LtLotVo;
import com.nexus.mes.lt.domain.vo.LtWoConsumptionVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * MES工单批次消耗 Service 业务层接口
 *
 * @author Nexus
 */
public interface ILtWoConsumptionService {

    /**
     * 分页查询工单批次消耗列表
     */
    TableDataInfo<LtWoConsumptionVo> selectPageList(LtWoConsumptionBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取消耗记录详情
     */
    LtWoConsumptionVo selectById(Long consumptionId);

    /**
     * 新增工单批次消耗(联动扣减批次数量)
     * <p>事务性: 写消耗记录 + 扣减 lt_lot.consumed_qty + 状态变更</p>
     */
    Boolean insertByBo(LtWoConsumptionBo bo);

    /**
     * 批量删除消耗记录(不回滚已扣减数量,仅删记录)
     */
    Boolean deleteByIds(Long[] ids);

    /**
     * 按工单ID查询所有批次消耗
     */
    List<LtWoConsumptionVo> selectByWoId(Long woId);

    /**
     * 按批次ID查询所有消耗记录(追溯用)
     */
    List<LtWoConsumptionVo> selectByLotId(Long lotId);

    /**
     * 工单 FEFO 推荐: 按到期升序返回该物料的可用批次(IN_STOCK + QC PASS + 剩余>0)
     * <p>woId 仅作上下文/日志(FEFO 按 materialId 推荐); 两步领料第一步。</p>
     *
     * @param woId        工单ID
     * @param materialId  物料ID
     * @param requiredQty 需求数量(可空, 传值则累加直到满足)
     * @return 推荐批次列表
     */
    List<LtLotVo> recommendForWo(Long woId, Long materialId, BigDecimal requiredQty);

    /**
     * 工单 FEFO 领料确认: 逐条扣减批次数量 + 写消耗记录(+可选 SN↔批次绑定)
     * <p>两步领料第二步; 事务性, 任一条失败回滚全部。作为未来 P0-1 物料拉动的调用入口。</p>
     *
     * @param pickBo 领料请求(woId/materialId/snId + picks)
     * @return 成功返回 true
     */
    Boolean confirmPick(LtWoPickBo pickBo);
}
