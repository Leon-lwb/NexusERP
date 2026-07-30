package com.nexus.erp.fin.arap.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.fin.arap.constant.ArapConstants;
import com.nexus.erp.fin.arap.domain.FinReceipt;
import com.nexus.erp.fin.arap.domain.bo.FinReceiptBo;
import com.nexus.erp.fin.arap.domain.vo.FinReceiptVo;
import com.nexus.erp.fin.arap.mapper.FinReceiptMapper;
import com.nexus.erp.fin.arap.service.IFinReceiptService;
import com.nexus.erp.fin.gl.constant.GlConstants;
import com.nexus.erp.fin.gl.mq.GlPostingMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 收付款单 Service 实现
 *
 * @author nexus
 * @date 2026-07-30
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class FinReceiptServiceImpl implements IFinReceiptService {

    private final FinReceiptMapper baseMapper;
    private final ReliableMessageProducer messageProducer;

    /** 分页查询,委托 buildQueryWrapper 构建条件 */
    @Override
    public TableDataInfo<FinReceiptVo> queryPageList(FinReceiptBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<FinReceipt> lqw = buildQueryWrapper(bo);
        Page<FinReceiptVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /** 列表查询,委托 buildQueryWrapper 构建条件 */
    @Override
    public List<FinReceiptVo> queryList(FinReceiptBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /** 按主键查询 */
    @Override
    public FinReceiptVo queryById(Long receiptId) {
        return baseMapper.selectVoById(receiptId);
    }

    /** 新增,自动填充单据编号/状态/来源/币种/汇率/日期/version 默认值 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(FinReceiptBo bo) {
        FinReceipt entity = MapstructUtils.convert(bo, FinReceipt.class);
        if (StringUtils.isBlank(entity.getReceiptNo())) {
            entity.setReceiptNo("RC" + IdWorker.getId());
        }
        if (StringUtils.isBlank(entity.getStatus())) {
            entity.setStatus(ArapConstants.RECEIPT_STATUS_DRAFT);
        }
        if (StringUtils.isBlank(entity.getSource())) {
            entity.setSource(ArapConstants.SOURCE_MANUAL);
        }
        if (entity.getSettledAmount() == null) {
            entity.setSettledAmount(BigDecimal.ZERO);
        }
        if (StringUtils.isBlank(entity.getCurrency())) {
            entity.setCurrency("CNY");
        }
        if (entity.getExchangeRate() == null) {
            entity.setExchangeRate(BigDecimal.ONE);
        }
        if (entity.getReceiptDate() == null) {
            entity.setReceiptDate(new Date());
        }
        if (entity.getVersion() == null) {
            entity.setVersion(0);
        }
        return baseMapper.insert(entity) > 0;
    }

    /** 修改,仅 DRAFT 可修改,乐观锁 version 防并发 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(FinReceiptBo bo) {
        FinReceipt existing = baseMapper.selectById(bo.getReceiptId());
        if (existing == null) {
            throw new ServiceException("收付款单不存在");
        }
        if (!ArapConstants.RECEIPT_STATUS_DRAFT.equals(existing.getStatus())) {
            throw new ServiceException("当前状态【{}】不允许修改", existing.getStatus());
        }
        FinReceipt entity = MapstructUtils.convert(bo, FinReceipt.class);
        entity.setVersion(existing.getVersion());
        return baseMapper.updateById(entity) > 0;
    }

    /** 批量删除,仅 DRAFT 可删除,非草稿抛异常 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        for (Long id : ids) {
            FinReceipt existing = baseMapper.selectById(id);
            if (existing != null && !ArapConstants.RECEIPT_STATUS_DRAFT.equals(existing.getStatus())) {
                throw new ServiceException("收付款单【{}】非草稿,不可删除", existing.getReceiptNo());
            }
        }
        return baseMapper.deleteBatchIds(Arrays.asList(ids)) > 0;
    }

    /** DRAFT->CONFIRMED,发送 GlPostingMessage(RECEIPT) 异步制单 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long receiptId) {
        FinReceipt receipt = baseMapper.selectById(receiptId);
        if (receipt == null) {
            throw new ServiceException("收付款单不存在");
        }
        if (!ArapConstants.RECEIPT_STATUS_DRAFT.equals(receipt.getStatus())) {
            throw new ServiceException("当前状态【{}】不允许确认", receipt.getStatus());
        }
        int rows = baseMapper.update(null, Wrappers.<FinReceipt>lambdaUpdate()
            .eq(FinReceipt::getReceiptId, receiptId)
            .eq(FinReceipt::getVersion, receipt.getVersion())
            .set(FinReceipt::getStatus, ArapConstants.RECEIPT_STATUS_CONFIRMED)
            .set(FinReceipt::getVersion, receipt.getVersion() + 1));
        if (rows == 0) {
            throw new ServiceException("收付款单数据已变更,确认失败,请重试");
        }
        // 触发 GL 异步过账(GlPostingConsumer -> ReceiptPoster 制单)
        GlPostingMessage msg = new GlPostingMessage();
        msg.setSourceType(GlConstants.SOURCE_RECEIPT);
        msg.setSourceId(receiptId);
        msg.setSourceNo(receipt.getReceiptNo());
        msg.setVoucherDate(receipt.getReceiptDate() != null ? receipt.getReceiptDate() : new Date());
        msg.setTenantIdStr(TenantHelper.getTenantId());
        msg.setSourceModule("fin");
        messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE, RoutingKeyConstants.GL_POSTING_RECEIPT, msg);
        log.info("收付款单确认,触发GL过账: receiptId={}, receiptNo={}", receiptId, receipt.getReceiptNo());
    }

    /** 仅 DRAFT 可作废,置 CANCELED;已确认单据暂不支持(需先反核销+反过账) */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long receiptId) {
        FinReceipt receipt = baseMapper.selectById(receiptId);
        if (receipt == null) {
            throw new ServiceException("收付款单不存在");
        }
        if (!ArapConstants.RECEIPT_STATUS_DRAFT.equals(receipt.getStatus())) {
            throw new ServiceException("仅草稿可作废,当前【{}】(已确认单据需先反核销+反过账,F1 暂不支持)", receipt.getStatus());
        }
        int rows = baseMapper.update(null, Wrappers.<FinReceipt>lambdaUpdate()
            .eq(FinReceipt::getReceiptId, receiptId)
            .eq(FinReceipt::getVersion, receipt.getVersion())
            .set(FinReceipt::getStatus, ArapConstants.RECEIPT_STATUS_CANCELLED)
            .set(FinReceipt::getVersion, receipt.getVersion() + 1));
        if (rows == 0) {
            throw new ServiceException("收付款单数据已变更,作废失败,请重试");
        }
    }

    /** 构建查询条件,按创建时间倒序排列 */
    private LambdaQueryWrapper<FinReceipt> buildQueryWrapper(FinReceiptBo bo) {
        LambdaQueryWrapper<FinReceipt> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.like(StringUtils.isNotBlank(bo.getReceiptNo()), FinReceipt::getReceiptNo, bo.getReceiptNo());
            lqw.eq(StringUtils.isNotBlank(bo.getDirection()), FinReceipt::getDirection, bo.getDirection());
            lqw.eq(StringUtils.isNotBlank(bo.getStatus()), FinReceipt::getStatus, bo.getStatus());
            lqw.eq(StringUtils.isNotBlank(bo.getSource()), FinReceipt::getSource, bo.getSource());
            lqw.eq(bo.getClientId() != null, FinReceipt::getClientId, bo.getClientId());
            lqw.eq(bo.getVendorId() != null, FinReceipt::getVendorId, bo.getVendorId());
        }
        lqw.orderByDesc(FinReceipt::getCreateTime);
        return lqw;
    }
}
