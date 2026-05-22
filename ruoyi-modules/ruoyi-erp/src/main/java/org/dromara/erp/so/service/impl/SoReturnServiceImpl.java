package org.dromara.erp.so.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.so.domain.SoReturn;
import org.dromara.erp.so.domain.SoReturnLine;
import org.dromara.erp.so.domain.bo.SoReturnBo;
import org.dromara.erp.so.domain.vo.SoReturnVo;
import org.dromara.erp.so.mapper.SoReturnLineMapper;
import org.dromara.erp.so.mapper.SoReturnMapper;
import org.dromara.erp.so.service.ISoReturnService;
import org.dromara.erp.wm.domain.WmMaterialStock;
import org.dromara.erp.wm.domain.WmMaterialStockTx;
import org.dromara.erp.wm.mapper.WmMaterialStockMapper;
import org.dromara.erp.wm.mapper.WmMaterialStockTxMapper;
import org.dromara.system.strategy.AutoCodeUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class SoReturnServiceImpl implements ISoReturnService {

    private final SoReturnMapper baseMapper;
    private final SoReturnLineMapper lineMapper;
    private final WmMaterialStockMapper stockMapper;
    private final WmMaterialStockTxMapper stockTxMapper;
    private final AutoCodeUtil autoCodeUtil;

    @Override
    public TableDataInfo<SoReturnVo> selectPageList(SoReturnBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SoReturn> lqw = buildQueryWrapper(bo);
        Page<SoReturnVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<SoReturnVo> selectList(SoReturnBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public SoReturnVo selectById(Long id) {
        SoReturnVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getReturnId() != null) {
            LambdaQueryWrapper<SoReturnLine> lineLqw = Wrappers.lambdaQuery();
            lineLqw.eq(SoReturnLine::getReturnId, vo.getReturnId());
            lineLqw.orderByAsc(SoReturnLine::getSortOrder);
            vo.setSoReturnLineList(lineMapper.selectList(lineLqw));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SoReturnBo bo) {
        SoReturn entity = MapstructUtils.convert(bo, SoReturn.class);
        entity.setReturnNo(autoCodeUtil.genSerialCode("SO_RETURN_CODE", null));
        int rows = baseMapper.insert(entity);
        insertOrUpdateLines(entity.getReturnId(), bo.getSoReturnLineList());
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SoReturnBo bo) {
        SoReturn entity = MapstructUtils.convert(bo, SoReturn.class);
        deleteLinesByReturnId(entity.getReturnId());
        insertOrUpdateLines(entity.getReturnId(), bo.getSoReturnLineList());
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            deleteLinesByReturnId(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long returnId) {
        SoReturn soReturn = baseMapper.selectById(returnId);
        if (soReturn == null) {
            throw new RuntimeException("退货单不存在");
        }
        if (!STATUS_DRAFT.equals(soReturn.getStatus())) {
            throw new RuntimeException("只有草稿状态的退货单才能确认");
        }
        soReturn.setStatus(STATUS_CONFIRMED);
        baseMapper.updateById(soReturn);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long returnId) {
        SoReturn soReturn = baseMapper.selectById(returnId);
        if (soReturn == null) {
            throw new RuntimeException("退货单不存在");
        }
        if (STATUS_RECEIVED.equals(soReturn.getStatus())) {
            throw new RuntimeException("退货单已收货");
        }

        List<SoReturnLine> lines = lineMapper.selectList(
            Wrappers.lambdaQuery(SoReturnLine.class)
                .eq(SoReturnLine::getReturnId, returnId));

        if (lines == null || lines.isEmpty()) {
            throw new RuntimeException("退货单无明细行");
        }

        for (SoReturnLine line : lines) {
            postReturnStock(soReturn, line, new Date());
        }

        soReturn.setStatus(STATUS_RECEIVED);
        baseMapper.updateById(soReturn);
        log.info("退货单执行完成, returnId={}", returnId);
    }

    private void postReturnStock(SoReturn soReturn, SoReturnLine line, Date now) {
        if (line.getReturnQuantity() == null || line.getReturnQuantity().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("退货数量必须大于0");
        }
        // 查找匹配库存
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WmMaterialStock> lqw =
            Wrappers.lambdaQuery();
        lqw.eq(WmMaterialStock::getMaterialId, line.getMaterialId());
        lqw.eq(soReturn.getWarehouseId() != null, WmMaterialStock::getWarehouseId, soReturn.getWarehouseId());
        lqw.eq(WmMaterialStock::getFrozenFlag, 0L);
        WmMaterialStock stock = stockMapper.selectOne(lqw);

        java.math.BigDecimal beforeQuantity = stock == null || stock.getStockQuantity() == null
            ? java.math.BigDecimal.ZERO : stock.getStockQuantity();
        java.math.BigDecimal afterQuantity = beforeQuantity.add(line.getReturnQuantity());

        if (stock == null) {
            stock = new WmMaterialStock();
            stock.setMaterialId(line.getMaterialId());
            stock.setMaterialCode(line.getMaterialCode());
            stock.setMaterialName(line.getMaterialName());
            stock.setMaterialSpec(line.getMaterialSpec());
            stock.setBaseUomId(line.getBaseUomId());
            stock.setBaseUomName(line.getBaseUomName());
            stock.setWarehouseId(soReturn.getWarehouseId());
            stock.setWarehouseCode(soReturn.getWarehouseCode());
            stock.setWarehouseName(soReturn.getWarehouseName());
            stock.setStockQuantity(afterQuantity);
            stock.setRecptDate(now);
            stock.setFrozenFlag(0L);
            stockMapper.insert(stock);
        } else {
            stock.setStockQuantity(afterQuantity);
            stock.setRecptDate(now);
            stockMapper.updateById(stock);
        }

        // 生成库存交易记录
        WmMaterialStockTx tx = new WmMaterialStockTx();
        tx.setTxType("RETURN_IN");
        tx.setMaterialId(stock.getMaterialId());
        tx.setMaterialCode(stock.getMaterialCode());
        tx.setMaterialName(stock.getMaterialName());
        tx.setMaterialSpec(stock.getMaterialSpec());
        tx.setBaseUomId(stock.getBaseUomId());
        tx.setBaseUomName(stock.getBaseUomName());
        tx.setBatchId(stock.getBatchId());
        tx.setBatchCode(stock.getBatchCode());
        tx.setWarehouseId(stock.getWarehouseId());
        tx.setWarehouseCode(stock.getWarehouseCode());
        tx.setWarehouseName(stock.getWarehouseName());
        tx.setLocationId(stock.getLocationId());
        tx.setLocationCode(stock.getLocationCode());
        tx.setLocationName(stock.getLocationName());
        tx.setMaterialStockId(stock.getStockId());
        tx.setTxFlag(1);
        tx.setTxQuantity(line.getReturnQuantity());
        tx.setBeforeQuantity(beforeQuantity);
        tx.setAfterQuantity(afterQuantity);
        tx.setTxDate(now);
        tx.setSourceDocType("SO_RETURN");
        tx.setSourceDocId(soReturn.getReturnId());
        tx.setSourceDocCode(soReturn.getReturnNo());
        tx.setSourceDocLineId(line.getLineId());
        tx.setRemark("销售退货入库，原订单号：" + soReturn.getSoNo());
        stockTxMapper.insert(tx);
    }

    private void insertOrUpdateLines(Long returnId, List<SoReturnLine> lineList) {
        if (lineList != null && !lineList.isEmpty()) {
            for (SoReturnLine line : lineList) {
                line.setReturnId(returnId);
                lineMapper.insert(line);
            }
        }
    }

    private void deleteLinesByReturnId(Long returnId) {
        if (returnId != null) {
            LambdaQueryWrapper<SoReturnLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(SoReturnLine::getReturnId, returnId);
            lineMapper.delete(lqw);
        }
    }

    private LambdaQueryWrapper<SoReturn> buildQueryWrapper(SoReturnBo bo) {
        LambdaQueryWrapper<SoReturn> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getReturnNo()), SoReturn::getReturnNo, bo.getReturnNo());
        lqw.eq(StringUtils.isNotBlank(bo.getSoNo()), SoReturn::getSoNo, bo.getSoNo());
        lqw.eq(bo.getClientId() != null, SoReturn::getClientId, bo.getClientId());
        lqw.like(StringUtils.isNotBlank(bo.getClientName()), SoReturn::getClientName, bo.getClientName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), SoReturn::getStatus, bo.getStatus());
        lqw.orderByDesc(SoReturn::getCreateTime);
        return lqw;
    }
}
