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

    /** 发票数据访问映射，封装发票表的增删改查。 */
    private final PayInvoiceMapper baseMapper;

    /**
     * 分页查询发票列表。
     *
     * @param bo 发票查询条件
     * @param pageQuery 分页参数
     * @return 分页后的发票视图列表
     */
    @Override
    public TableDataInfo<PayInvoiceVo> selectPageList(PayInvoiceBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PayInvoice> lqw = buildQueryWrapper(bo);
        Page<PayInvoiceVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 按条件查询全部发票列表（不分页）。
     *
     * @param bo 发票查询条件
     * @return 发票视图列表
     */
    @Override
    public List<PayInvoiceVo> selectList(PayInvoiceBo bo) {
        LambdaQueryWrapper<PayInvoice> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    /**
     * 根据发票 ID 查询发票详情。
     *
     * @param invoiceId 发票 ID
     * @return 发票视图对象
     */
    @Override
    public PayInvoiceVo selectById(Long invoiceId) {
        return baseMapper.selectVoById(invoiceId);
    }

    /**
     * 新增发票记录。
     *
     * @param payInvoice 发票实体
     * @return 受影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertPayInvoice(PayInvoice payInvoice) {
        return baseMapper.insert(payInvoice);
    }

    /**
     * 修改发票记录。
     *
     * @param payInvoice 发票实体
     * @return 受影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePayInvoice(PayInvoice payInvoice) {
        return baseMapper.updateById(payInvoice);
    }

    /**
     * 批量删除发票记录。
     *
     * @param invoiceIds 待删除的发票 ID 数组
     * @return 受影响行数
     */
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
