package com.nexus.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.pay.domain.PayInvoice;
import com.nexus.pay.domain.bo.PayInvoiceBo;
import com.nexus.pay.domain.vo.PayInvoiceVo;
import com.nexus.pay.mapper.PayInvoiceMapper;
import com.nexus.pay.service.IPayInvoiceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 发票Service业务层处理
 *
 * @author geek
 */
@RequiredArgsConstructor
@Service
public class PayInvoiceServiceImpl implements IPayInvoiceService {

    private final PayInvoiceMapper baseMapper;

    @Override
    public TableDataInfo<PayInvoiceVo> selectPageList(PayInvoiceBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PayInvoice> lqw = buildQueryWrapper(bo);
        Page<PayInvoiceVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<PayInvoiceVo> selectList(PayInvoiceBo bo) {
        LambdaQueryWrapper<PayInvoice> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    @Override
    public PayInvoiceVo selectById(Long invoiceId) {
        return baseMapper.selectVoById(invoiceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertPayInvoice(PayInvoice payInvoice) {
        return baseMapper.insert(payInvoice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePayInvoice(PayInvoice payInvoice) {
        return baseMapper.updateById(payInvoice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] invoiceIds) {
        return baseMapper.deleteBatchIds(List.of(invoiceIds));
    }

    private LambdaQueryWrapper<PayInvoice> buildQueryWrapper(PayInvoiceBo bo) {
        LambdaQueryWrapper<PayInvoice> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getOrderNumber()), PayInvoice::getOrderNumber, bo.getOrderNumber());
        lqw.eq(StringUtils.isNotBlank(bo.getInvoiceType()), PayInvoice::getInvoiceType, bo.getInvoiceType());
        return lqw;
    }

}
