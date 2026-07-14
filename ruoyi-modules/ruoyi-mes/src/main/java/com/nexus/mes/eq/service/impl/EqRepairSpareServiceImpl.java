package com.nexus.mes.eq.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.eq.domain.EqRepairSpare;
import com.nexus.mes.eq.domain.EqSparePart;
import com.nexus.mes.eq.domain.bo.EqRepairSpareBo;
import com.nexus.mes.eq.domain.bo.EqSpareStockBo;
import com.nexus.mes.eq.domain.vo.EqRepairSpareVo;
import com.nexus.mes.eq.domain.vo.EqSparePartVo;
import com.nexus.mes.eq.mapper.EqRepairSpareMapper;
import com.nexus.mes.eq.mapper.EqSparePartMapper;
import com.nexus.mes.eq.service.IEqRepairSpareService;
import com.nexus.mes.eq.service.IEqSparePartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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
    private final IEqSparePartService sparePartService;

    @Override
    public List<EqRepairSpareVo> selectByRepair(Long repairId) {
        List<EqRepairSpareVo> list = baseMapper.selectVoList(Wrappers.<EqRepairSpare>lambdaQuery()
            .eq(EqRepairSpare::getRepairId, repairId)
            .orderByDesc(EqRepairSpare::getCreateTime));
        fillSpareInfo(list);
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<EqRepairSpareVo> batchAdd(List<EqRepairSpareBo> bos) {
        if (bos == null || bos.isEmpty()) {
            return List.of();
        }
        List<EqRepairSpareVo> result = new ArrayList<>(bos.size());
        for (EqRepairSpareBo bo : bos) {
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        // 删除前回滚库存: 每条领用明细归还对应备件数量(IN 入库)
        List<EqRepairSpare> records = baseMapper.selectList(Wrappers.<EqRepairSpare>lambdaQuery()
            .in(EqRepairSpare::getId, Arrays.asList(ids)));
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
