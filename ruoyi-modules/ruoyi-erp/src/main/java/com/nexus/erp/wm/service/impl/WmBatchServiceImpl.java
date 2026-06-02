package com.nexus.erp.wm.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.WmBatch;
import com.nexus.erp.wm.domain.bo.WmBatchBo;
import com.nexus.erp.wm.domain.vo.WmBatchVo;
import com.nexus.erp.wm.mapper.WmBatchMapper;
import com.nexus.erp.wm.service.IWmBatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 批次管理Service业务层处理
 *
 * @author nexus
 * @date 2026-06-02
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class WmBatchServiceImpl implements IWmBatchService {

    private final WmBatchMapper baseMapper;

    /** 当日序列号计数器 */
    private static final AtomicInteger dailySequence = new AtomicInteger(0);
    /** 上次生成编码的日期 */
    private static volatile String lastDateStr = "";

    @Override
    public TableDataInfo<WmBatchVo> selectPageList(WmBatchBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<WmBatch> lqw = buildQueryWrapper(bo);
        Page<WmBatchVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<WmBatchVo> selectList(WmBatchBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public WmBatchVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(WmBatchBo bo) {
        WmBatch entity = MapstructUtils.convert(bo, WmBatch.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(WmBatchBo bo) {
        WmBatch entity = MapstructUtils.convert(bo, WmBatch.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    public WmBatchVo selectByCode(String batchCode) {
        LambdaQueryWrapper<WmBatch> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmBatch::getBatchCode, batchCode);
        return baseMapper.selectVoOne(lqw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WmBatchVo createBatch(WmBatchBo bo) {
        // 自动生成批次编码
        String batchCode = generateBatchCode();
        bo.setBatchCode(batchCode);
        if (StringUtils.isBlank(bo.getStatus())) {
            bo.setStatus("ACTIVE");
        }
        if (bo.getQuantity() == null) {
            bo.setQuantity(BigDecimal.ZERO);
        }
        if (bo.getReceivedQty() == null) {
            bo.setReceivedQty(BigDecimal.ZERO);
        }
        if (bo.getIssuedQty() == null) {
            bo.setIssuedQty(BigDecimal.ZERO);
        }
        WmBatch entity = MapstructUtils.convert(bo, WmBatch.class);
        baseMapper.insert(entity);
        return baseMapper.selectVoById(entity.getBatchId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuantity(Long batchId, BigDecimal deltaQty, boolean isReceive) {
        WmBatch batch = baseMapper.selectById(batchId);
        if (batch == null) {
            throw new ServiceException("批次不存在");
        }
        if (isReceive) {
            batch.setReceivedQty(batch.getReceivedQty().add(deltaQty));
        } else {
            batch.setIssuedQty(batch.getIssuedQty().add(deltaQty));
        }
        baseMapper.updateById(batch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long batchId, String status) {
        WmBatch batch = baseMapper.selectById(batchId);
        if (batch == null) {
            throw new ServiceException("批次不存在");
        }
        batch.setStatus(status);
        baseMapper.updateById(batch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkExpiry() {
        log.info("开始执行批次过期检查");
        Date today = new Date();
        LambdaQueryWrapper<WmBatch> lqw = Wrappers.lambdaQuery();
        lqw.eq(WmBatch::getStatus, "ACTIVE");
        lqw.lt(WmBatch::getExpiryDate, today);
        lqw.isNotNull(WmBatch::getExpiryDate);
        List<WmBatch> expiredBatches = baseMapper.selectList(lqw);
        if (expiredBatches == null || expiredBatches.isEmpty()) {
            log.info("无过期批次需要更新");
            return;
        }
        for (WmBatch batch : expiredBatches) {
            batch.setStatus("EXPIRED");
            baseMapper.updateById(batch);
        }
        log.info("批次过期检查完成，更新{}条过期批次", expiredBatches.size());
    }

    /**
     * 生成批次编码，格式：B{yyyyMMdd}{4位序号}
     *
     * @return 批次编码
     */
    private String generateBatchCode() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String currentDateStr = sdf.format(new Date());

        synchronized (WmBatchServiceImpl.class) {
            if (!currentDateStr.equals(lastDateStr)) {
                lastDateStr = currentDateStr;
                dailySequence.set(0);
            }
            int seq = dailySequence.incrementAndGet();
            return "B" + currentDateStr + String.format("%04d", seq);
        }
    }

    private LambdaQueryWrapper<WmBatch> buildQueryWrapper(WmBatchBo bo) {
        LambdaQueryWrapper<WmBatch> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getBatchCode()), WmBatch::getBatchCode, bo.getBatchCode());
        lqw.eq(bo.getMaterialId() != null, WmBatch::getMaterialId, bo.getMaterialId());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialCode()), WmBatch::getMaterialCode, bo.getMaterialCode());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialName()), WmBatch::getMaterialName, bo.getMaterialName());
        lqw.eq(bo.getVendorId() != null, WmBatch::getVendorId, bo.getVendorId());
        lqw.like(StringUtils.isNotBlank(bo.getVendorName()), WmBatch::getVendorName, bo.getVendorName());
        lqw.eq(bo.getWarehouseId() != null, WmBatch::getWarehouseId, bo.getWarehouseId());
        lqw.like(StringUtils.isNotBlank(bo.getWarehouseName()), WmBatch::getWarehouseName, bo.getWarehouseName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), WmBatch::getStatus, bo.getStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getSourceDocType()), WmBatch::getSourceDocType, bo.getSourceDocType());
        lqw.orderByDesc(WmBatch::getCreateTime);
        return lqw;
    }
}
