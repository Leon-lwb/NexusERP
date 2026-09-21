package com.nexus.mes.eq.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.eq.domain.EqRepair;
import com.nexus.mes.eq.domain.EqRepairSpare;
import com.nexus.mes.eq.domain.EqSparePart;
import com.nexus.mes.eq.domain.bo.EqRepairSpareBo;
import com.nexus.mes.eq.domain.bo.EqSpareStockBo;
import com.nexus.mes.eq.domain.vo.EqRepairSpareVo;
import com.nexus.mes.eq.domain.vo.EqSparePartVo;
import com.nexus.mes.eq.mapper.EqRepairMapper;
import com.nexus.mes.eq.mapper.EqRepairSpareMapper;
import com.nexus.mes.eq.mapper.EqSparePartMapper;
import com.nexus.mes.eq.service.IEqRepairSpareService;
import com.nexus.mes.eq.service.IEqRepairService;
import com.nexus.mes.eq.service.IEqSparePartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * MES维修领用备件明细 Service 业务层实现
 * <p>
 * 数据源: ry-mes 库。batchAdd 内循环调 {@link IEqSparePartService#stockTransaction} 扣减库存(REPAIR_USE)。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("eqRepairSpareService")
public class EqRepairSpareServiceImpl implements IEqRepairSpareService {

    private final EqRepairSpareMapper baseMapper;
    private final EqSparePartMapper sparePartMapper;
    private final EqRepairMapper repairMapper;
    private final IEqSparePartService sparePartService;

    /**
     * 按条件查询维修
     *
     * @param repairId 维修记录ID
     * @return 结果列表
     */
    @Override
    public List<EqRepairSpareVo> selectByRepair(Long repairId) {
        List<EqRepairSpareVo> list = baseMapper.selectVoList(Wrappers.<EqRepairSpare>lambdaQuery()
            .eq(EqRepairSpare::getRepairId, repairId)
            .orderByDesc(EqRepairSpare::getCreateTime));
        fillSpareInfo(list);
        return list;
    }

    /**
     * 新增维修备件
     *
     * @param bos 参数 bos
     * @return 结果列表
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<EqRepairSpareVo> batchAdd(List<EqRepairSpareBo> bos) {
        if (bos == null || bos.isEmpty()) {
            return List.of();
        }
        List<EqRepairSpareVo> result = new ArrayList<>(bos.size());
        for (EqRepairSpareBo bo : bos) {
            // 校验先于库存变更: 数量合法性 -> 维修单存在 -> 维修单处于维修中(避免无效领用扣减备件库存)
            if (bo.getQuantity() == null || bo.getQuantity().signum() <= 0) {
                throw new ServiceException("领用数量必须大于0");
            }
            EqRepair repair = repairMapper.selectById(bo.getRepairId());
            if (repair == null) {
                throw new ServiceException("维修单不存在: " + bo.getRepairId());
            }
            if (!IEqRepairService.STATUS_REPAIRING.equals(repair.getStatus())) {
                throw new ServiceException("维修单不在维修中状态, 无法领用备件: " + bo.getRepairId());
            }
            EqSparePartVo spare = sparePartMapper.selectVoById(bo.getSpareId());
            if (spare == null) {
                throw new ServiceException("备件不存在: " + bo.getSpareId());
            }
            // 扣减库存(REPAIR_USE), refId=repairId
            EqSpareStockBo stockBo = new EqSpareStockBo();
            stockBo.setSpareId(bo.getSpareId());
            stockBo.setTransactionType(IEqSparePartService.TXN_REPAIR_USE);
            stockBo.setQuantity(bo.getQuantity());
            stockBo.setRefType("REPAIR");
            stockBo.setRefId(bo.getRepairId());
            stockBo.setRemark(bo.getRemark());
            sparePartService.stockTransaction(stockBo);

            // 写维修领用明细
            EqRepairSpare rs = new EqRepairSpare();
            rs.setRepairId(bo.getRepairId());
            rs.setSpareId(bo.getSpareId());
            rs.setSpareCode(spare.getSpareCode());
            rs.setSpareName(spare.getSpareName());
            rs.setSpec(spare.getSpec());
            rs.setUnit(spare.getUnit());
            rs.setQuantity(bo.getQuantity());
            rs.setRemark(bo.getRemark());
            baseMapper.insert(rs);
            result.add(baseMapper.selectVoById(rs.getId()));
            log.info("维修领用备件: repairId={}, spareId={}, qty={}", bo.getRepairId(), bo.getSpareId(), bo.getQuantity());
        }
        return result;
    }

    /**
     * 获取维修备件详情
     *
     * @param ids ID数组
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        List<EqRepairSpare> records = baseMapper.selectList(Wrappers.<EqRepairSpare>lambdaQuery()
            .in(EqRepairSpare::getId, Arrays.asList(ids)));
        if (records.isEmpty()) {
            return false;
        }
        // 守卫(先校验后动库存, 与 batchAdd 同等口径): 领用明细只允许在维修单"维修中"时撤销。
        // 维修单已完工/已作废后删除明细并回滚库存 = 抹除已发生的备件消耗与成本, 属旁路(MES-EQ-08)。
        // 去重后逐个校验维修单, 避免同一维修单被反复查询。
        Set<Long> repairIds = new LinkedHashSet<>();
        for (EqRepairSpare rs : records) {
            if (rs.getRepairId() == null) {
                throw new ServiceException("领用明细缺少维修单关联, 无法删除: id=" + rs.getId());
            }
            repairIds.add(rs.getRepairId());
        }
        for (Long repairId : repairIds) {
            EqRepair repair = repairMapper.selectById(repairId);
            if (repair == null) {
                throw new ServiceException("维修单不存在: " + repairId);
            }
            if (!IEqRepairService.STATUS_REPAIRING.equals(repair.getStatus())) {
                throw new ServiceException("维修单不在维修中状态, 无法删除领用明细(已完工/已作废的消耗不可回滚): "
                    + repairId + ", 当前状态: " + repair.getStatus());
            }
        }
        // 校验通过后才回滚库存: 每条领用明细归还对应备件数量(IN 入库)
        for (EqRepairSpare rs : records) {
            if (rs.getSpareId() != null && rs.getQuantity() != null && rs.getQuantity().signum() > 0) {
                EqSpareStockBo stockBo = new EqSpareStockBo();
                stockBo.setSpareId(rs.getSpareId());
                stockBo.setTransactionType(IEqSparePartService.TXN_IN);
                stockBo.setQuantity(rs.getQuantity());
                stockBo.setRefType("REPAIR_CANCEL");
                stockBo.setRefId(rs.getRepairId());
                stockBo.setRemark("维修领用删除, 回滚库存");
                sparePartService.stockTransaction(stockBo);
                log.info("维修领用删除回滚库存: id={}, spareId={}, qty={}", rs.getId(), rs.getSpareId(), rs.getQuantity());
            }
        }
        return baseMapper.deleteByIds(Arrays.asList(ids)) > 0;
    }

    private void fillSpareInfo(List<EqRepairSpareVo> list) {
        // 冗余字段已在写入时填充, 此处保留扩展点
    }
}
