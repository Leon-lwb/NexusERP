package org.dromara.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.WmTransfer;
import org.dromara.erp.wm.domain.WmTransferLine;
import org.dromara.erp.wm.domain.bo.WmTransferBo;
import org.dromara.erp.wm.domain.vo.WmTransferVo;
import org.dromara.erp.wm.mapper.WmTransferLineMapper;
import org.dromara.erp.wm.mapper.WmTransferMapper;
import org.dromara.erp.wm.service.IWmTransferService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.dromara.system.strategy.AutoCodeUtil;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmTransferServiceImpl implements IWmTransferService {

    private final WmTransferMapper baseMapper;
    private final WmTransferLineMapper lineMapper;
    private final WmStockOperationServiceImpl stockOperationService;
    private final AutoCodeUtil autoCodeUtil;

    @Override
    public TableDataInfo<WmTransferVo> selectPageList(WmTransferBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmTransfer> lqw = buildQueryWrapper(bo);
        Page<WmTransferVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmTransferVo> selectList(WmTransferBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmTransferVo selectById(Long id) {
        WmTransferVo vo = baseMapper.selectVoById(id);
        if (vo != null && vo.getTransferId() != null) {
            LambdaQueryWrapper<WmTransferLine> lineLqw = Wrappers.lambdaQuery();
            lineLqw.eq(WmTransferLine::getTransferId, vo.getTransferId());
            vo.setWmTransferLineList(lineMapper.selectList(lineLqw));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmTransferBo bo) {
        WmTransfer entity = MapstructUtils.convert(bo, WmTransfer.class);
        entity.setTransferNo(autoCodeUtil.genSerialCode("WM_TRANSFER_CODE", null));
        int rows = baseMapper.insert(entity);
        if (bo.getWmTransferLineList() != null) {
            for (WmTransferLine line : bo.getWmTransferLineList()) {
                line.setTransferId(entity.getTransferId());
                lineMapper.insert(line);
            }
        }
        return rows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmTransferBo bo) {
        WmTransfer entity = MapstructUtils.convert(bo, WmTransfer.class);
        deleteLinesByTransferId(entity.getTransferId());
        if (bo.getWmTransferLineList() != null) {
            for (WmTransferLine line : bo.getWmTransferLineList()) {
                line.setTransferId(entity.getTransferId());
                lineMapper.insert(line);
            }
        }
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        for (Long id : ids) {
            deleteLinesByTransferId(id);
        }
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long transferId) {
        WmTransfer transfer = baseMapper.selectById(transferId);
        if (transfer == null) {
            throw new RuntimeException("调拨单不存在");
        }
        if ("EXECUTED".equals(transfer.getStatus())) {
            throw new RuntimeException("调拨单已执行");
        }

        LambdaQueryWrapper<WmTransferLine> lineLqw = Wrappers.lambdaQuery();
        lineLqw.eq(WmTransferLine::getTransferId, transferId);
        List<WmTransferLine> lines = lineMapper.selectList(lineLqw);

        if (lines == null || lines.isEmpty()) {
            throw new RuntimeException("调拨单无明细行");
        }

        for (WmTransferLine line : lines) {
            stockOperationService.postTransfer(line);
        }

        transfer.setStatus("EXECUTED");
        transfer.setExecuteDate(new Date());
        baseMapper.updateById(transfer);
        log.info("调拨单执行完成, transferId={}", transferId);
    }

    private void deleteLinesByTransferId(Long transferId) {
        if (transferId != null) {
            LambdaQueryWrapper<WmTransferLine> lqw = Wrappers.lambdaQuery();
            lqw.eq(WmTransferLine::getTransferId, transferId);
            lineMapper.delete(lqw);
        }
    }

    private LambdaQueryWrapper<WmTransfer> buildQueryWrapper(WmTransferBo bo) {
        LambdaQueryWrapper<WmTransfer> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getTransferNo()), WmTransfer::getTransferNo, bo.getTransferNo());
        lqw.eq(bo.getFromWarehouseId() != null, WmTransfer::getFromWarehouseId, bo.getFromWarehouseId());
        lqw.eq(bo.getToWarehouseId() != null, WmTransfer::getToWarehouseId, bo.getToWarehouseId());
        lqw.eq(StringUtils.isNotBlank(bo.getTransferType()), WmTransfer::getTransferType, bo.getTransferType());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmTransfer::getStatus, bo.getStatus());
        lqw.orderByDesc(WmTransfer::getCreateTime);
        return lqw;
    }
}
