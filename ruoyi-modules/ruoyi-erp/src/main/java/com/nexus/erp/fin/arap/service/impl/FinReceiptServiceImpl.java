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

    /**
     * 新增,自动填充单据编号/状态/来源/币种/汇率/日期/version 默认值。
     * <p><b>服务端权威字段（FIN-ARAP-01）</b>：{@code status} 强制 DRAFT、{@code source} 强制 MANUAL、
     * {@code settledAmount} 强制 0；入参携带非 DRAFT 状态或非 MANUAL 来源一律拒绝。
     * 原因：唯一会发出 {@code GlPostingMessage(RECEIPT)} 的入口是 {@link #confirm(Long)}，
     * 若允许客户端直建 CONFIRMED，则该笔收付款在 AR/AP 子账已发生却永不产生总账凭证。
     * 支付回调建单走 {@code PayReceivedConsumer -> save(entity)}（该路径自行发过账消息），不经本方法。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(FinReceiptBo bo) {
        if (StringUtils.isNotBlank(bo.getStatus())
            && !ArapConstants.RECEIPT_STATUS_DRAFT.equals(bo.getStatus())) {
            throw new ServiceException("收付款单状态由服务端管理,不接受入参【{}】;如需确认请调用确认接口(会触发总账过账)",
                bo.getStatus());
        }
        if (StringUtils.isNotBlank(bo.getSource())
            && !ArapConstants.SOURCE_MANUAL.equals(bo.getSource())) {
            throw new ServiceException("收付款单来源由服务端管理,不接受入参【{}】", bo.getSource());
        }
        FinReceipt entity = MapstructUtils.convert(bo, FinReceipt.class);
        if (StringUtils.isBlank(entity.getReceiptNo())) {
            entity.setReceiptNo("RC" + IdWorker.getId());
        }
        // 服务端权威字段:状态机起点只能是 DRAFT(确认才发 GL 过账消息)
        entity.setStatus(ArapConstants.RECEIPT_STATUS_DRAFT);
        entity.setSource(ArapConstants.SOURCE_MANUAL);
        entity.setSettledAmount(BigDecimal.ZERO);
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

    /**
     * 幂等判断:该支付订单号下是否已有收款单(与 uk_pay_order 唯一索引口径一致, 含逻辑删除数据, 重建同单号会被索引拦截)
     */
    @Override
    public boolean existsByPayOrderNumber(String payOrderNumber) {
        return baseMapper.exists(Wrappers.<FinReceipt>lambdaQuery()
            .eq(FinReceipt::getPayOrderNumber, payOrderNumber));
    }

    /**
     * 修改,仅 DRAFT 可修改,乐观锁 version 防并发。
     * <p><b>字段白名单（FIN-ARAP-01）</b>：显式拷贝可编辑字段，<b>不覆盖</b>
     * receiptNo/status/source/settledAmount/version；入参若携带与库中不一致的 status/source 直接拒绝，
     * 防止用「修改」接口把 DRAFT 直接改成 CONFIRMED 从而绕过 {@code confirm()} 的 GL 过账。</p>
     */
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
        if (StringUtils.isNotBlank(bo.getStatus()) && !existing.getStatus().equals(bo.getStatus())) {
            throw new ServiceException("收付款单状态不可通过修改接口变更(当前【{}】);如需确认请调用确认接口", existing.getStatus());
        }
        if (StringUtils.isNotBlank(bo.getSource()) && !existing.getSource().equals(bo.getSource())) {
            throw new ServiceException("收付款单来源不可修改");
        }
        FinReceipt entity = new FinReceipt();
        entity.setReceiptId(bo.getReceiptId());
        entity.setReceiptType(bo.getReceiptType());
        entity.setDirection(bo.getDirection());
        entity.setClientId(bo.getClientId());
        entity.setVendorId(bo.getVendorId());
        entity.setPayerName(bo.getPayerName());
        entity.setAmount(bo.getAmount());
        entity.setCurrency(bo.getCurrency());
        entity.setExchangeRate(bo.getExchangeRate());
        entity.setThirdPartyNo(bo.getThirdPartyNo());
        entity.setPayOrderNumber(bo.getPayOrderNumber());
        entity.setReceiptDate(bo.getReceiptDate());
        entity.setRemark(bo.getRemark());
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

    @Override
    public boolean save(FinReceipt entity) {
        return baseMapper.insert(entity) > 0;
    }
}
