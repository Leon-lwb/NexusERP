package org.dromara.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.pay.domain.PayInvoice;
import org.dromara.pay.domain.bo.PayInvoiceBo;
import org.dromara.pay.domain.vo.PayInvoiceVo;
import org.dromara.pay.mapper.PayInvoiceMapper;
import org.dromara.pay.service.IPayInvoiceService;
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
