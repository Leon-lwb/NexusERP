package com.nexus.mes.eq.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.eq.domain.EqSparePart;
import com.nexus.mes.eq.domain.EqSpareStockLog;
import com.nexus.mes.eq.domain.bo.EqSparePartBo;
import com.nexus.mes.eq.domain.bo.EqSpareStockBo;
import com.nexus.mes.eq.domain.vo.EqSparePartVo;
import com.nexus.mes.eq.domain.vo.EqSpareStockLogVo;
import com.nexus.mes.eq.mapper.EqSparePartMapper;
import com.nexus.mes.eq.mapper.EqSpareStockLogMapper;
import com.nexus.mes.eq.service.IEqSparePartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * MES备件主数据 Service 业务层实现
 * <p>
 * 数据源: ry-mes 库。出入库用条件更新 current_stock 防超卖(OUT/REPAIR_USE)。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("eqSparePartService")
public class EqSparePartServiceImpl implements IEqSparePartService {

    private final EqSparePartMapper baseMapper;
    private final EqSpareStockLogMapper stockLogMapper;

    @Override
    public TableDataInfo<EqSparePartVo> selectPageList(EqSparePartBo bo, PageQuery pageQuery) {
        Page<EqSparePartVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(bo));
        fillLowStock(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public EqSparePartVo selectById(Long spareId) {
        EqSparePartVo vo = baseMapper.selectVoById(spareId);
        if (vo != null) {
            fillLowStock(List.of(vo));
        }
        return vo;
    }

    @Override
    public List<EqSparePartVo> selectList(EqSparePartBo bo) {
        List<EqSparePartVo> list = baseMapper.selectVoList(buildQueryWrapper(bo));
        fillLowStock(list);
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EqSparePartVo insertByBo(EqSparePartBo bo) {
        // 校验编码唯一
        Long cnt = baseMapper.selectCount(Wrappers.<EqSparePart>lambdaQuery()
            .eq(EqSparePart::getSpareCode, bo.getSpareCode()));
        if (cnt != null && cnt > 0) {
            throw new ServiceException("备件编码已存在: " + bo.getSpareCode());
        }
        EqSparePart spare = new EqSparePart();
        spare.setSpareCode(bo.getSpareCode());
        spare.setSpareName(bo.getSpareName());
        spare.setSpec(bo.getSpec());
        spare.setModel(bo.getModel());
        spare.setUnit(bo.getUnit());
        spare.setCategory(bo.getCategory());
        spare.setSafetyStock(bo.getSafetyStock() != null ? bo.getSafetyStock() : BigDecimal.ZERO);
        spare.setCurrentStock(BigDecimal.ZERO);
        spare.setRemark(bo.getRemark());
        baseMapper.insert(spare);
        log.info("新增备件: spareId={}, code={}", spare.getSpareId(), spare.getSpareCode());
        return baseMapper.selectVoById(spare.getSpareId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(EqSparePartBo bo) {
        EqSparePart spare = new EqSparePart();
        spare.setSpareId(bo.getSpareId());
        spare.setSpareCode(bo.getSpareCode());
        spare.setSpareName(bo.getSpareName());
        spare.setSpec(bo.getSpec());
        spare.setModel(bo.getModel());
        spare.setUnit(bo.getUnit());
        spare.setCategory(bo.getCategory());
        spare.setSafetyStock(bo.getSafetyStock());
        spare.setRemark(bo.getRemark());
        return baseMapper.updateById(spare) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        // 校验库存为0才允许删除
        List<EqSparePartVo> list = baseMapper.selectVoList(Wrappers.<EqSparePart>lambdaQuery()
            .in(EqSparePart::getSpareId, Arrays.asList(ids)));
        for (EqSparePartVo v : list) {
            if (v.getCurrentStock() != null && v.getCurrentStock().compareTo(BigDecimal.ZERO) != 0) {
                throw new ServiceException("备件[" + v.getSpareCode() + "]库存不为0, 无法删除");
            }
        }
        return baseMapper.deleteByIds(Arrays.asList(ids)) > 0;
    }

    /**
     * 出入库: 原子更新 current_stock + 写流水。
     * IN: current_stock = current_stock + qty
     * OUT/REPAIR_USE: current_stock = current_stock - qty (WHERE current_stock >= qty, 失败抛库存不足)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal stockTransaction(EqSpareStockBo bo) {
        String type = bo.getTransactionType();
        if (!TXN_IN.equals(type) && !TXN_OUT.equals(type) && !TXN_REPAIR_USE.equals(type)) {
            throw new ServiceException("非法流水类型: " + type);
        }
        EqSparePartVo spare = baseMapper.selectVoById(bo.getSpareId());
        if (spare == null) {
            throw new ServiceException("备件不存在: " + bo.getSpareId());
        }
        BigDecimal before = spare.getCurrentStock() == null ? BigDecimal.ZERO : spare.getCurrentStock();
        BigDecimal qty = bo.getQuantity();
        BigDecimal after;
        int rows;
        if (TXN_IN.equals(type)) {
            after = before.add(qty);
            rows = baseMapper.update(null, Wrappers.<EqSparePart>lambdaUpdate()
                .setSql("current_stock = current_stock + {0}", qty.toPlainString())
                .eq(EqSparePart::getSpareId, bo.getSpareId()));
        } else {
            // OUT / REPAIR_USE: 防超卖
            after = before.subtract(qty);
            rows = baseMapper.update(null, Wrappers.<EqSparePart>lambdaUpdate()
                .setSql("current_stock = current_stock - {0}", qty.toPlainString())
                .eq(EqSparePart::getSpareId, bo.getSpareId())
                .ge(EqSparePart::getCurrentStock, qty));
        }
        if (rows == 0) {
            throw new ServiceException("库存不足, 当前库存: " + before + ", 需扣减: " + qty);
        }
        // 写流水
        EqSpareStockLog logEntry = new EqSpareStockLog();
        logEntry.setSpareId(bo.getSpareId());
        logEntry.setSpareCode(spare.getSpareCode());
        logEntry.setSpareName(spare.getSpareName());
        logEntry.setTransactionType(type);
        logEntry.setQuantity(qty);
        logEntry.setBeforeQty(before);
        logEntry.setAfterQty(after);
        logEntry.setRefType(StringUtils.isNotBlank(bo.getRefType()) ? bo.getRefType() : "MANUAL");
        logEntry.setRefId(bo.getRefId());
        logEntry.setOperatorId(LoginHelper.getUserId());
        logEntry.setOperatorName(LoginHelper.getUsername());
        logEntry.setOperateTime(new Date());
        logEntry.setRemark(bo.getRemark());
        stockLogMapper.insert(logEntry);
        log.info("备件出入库: spareId={}, type={}, qty={}, before={}, after={}", bo.getSpareId(), type, qty, before, after);
        return after;
    }

    @Override
    public TableDataInfo<EqSpareStockLogVo> selectStockLogPage(EqSpareStockBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EqSpareStockLog> lqw = Wrappers.<EqSpareStockLog>lambdaQuery()
            .eq(bo.getSpareId() != null, EqSpareStockLog::getSpareId, bo.getSpareId())
            .eq(StringUtils.isNotBlank(bo.getTransactionType()), EqSpareStockLog::getTransactionType, bo.getTransactionType())
            .eq(StringUtils.isNotBlank(bo.getRefType()), EqSpareStockLog::getRefType, bo.getRefType())
            .orderByDesc(EqSpareStockLog::getOperateTime);
        Page<EqSpareStockLogVo> page = stockLogMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<EqSparePartVo> selectLowStock() {
        List<EqSparePartVo> list = baseMapper.selectVoList(Wrappers.<EqSparePart>lambdaQuery()
            .isNotNull(EqSparePart::getSafetyStock)
            .apply("current_stock < safety_stock"));
        fillLowStock(list);
        return list;
    }

    // ==================== 私有辅助 ====================

    private LambdaQueryWrapper<EqSparePart> buildQueryWrapper(EqSparePartBo bo) {
        return Wrappers.<EqSparePart>lambdaQuery()
            .like(StringUtils.isNotBlank(bo.getSpareCode()), EqSparePart::getSpareCode, bo.getSpareCode())
            .like(StringUtils.isNotBlank(bo.getSpareName()), EqSparePart::getSpareName, bo.getSpareName())
            .eq(StringUtils.isNotBlank(bo.getCategory()), EqSparePart::getCategory, bo.getCategory())
            .orderByDesc(EqSparePart::getCreateTime);
    }

    /** 填充 lowStock 标记: current_stock < safety_stock */
    private void fillLowStock(List<EqSparePartVo> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        list.forEach(v -> {
            BigDecimal cur = v.getCurrentStock() == null ? BigDecimal.ZERO : v.getCurrentStock();
            BigDecimal safe = v.getSafetyStock() == null ? BigDecimal.ZERO : v.getSafetyStock();
            v.setLowStock(cur.compareTo(safe) < 0);
        });
    }
}
