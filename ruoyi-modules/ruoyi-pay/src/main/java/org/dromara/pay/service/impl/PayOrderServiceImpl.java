package org.dromara.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.pay.domain.PayOrder;
import org.dromara.pay.domain.bo.PayOrderBo;
import org.dromara.pay.domain.vo.PayOrderVo;
import org.dromara.pay.mapper.PayOrderMapper;
import org.dromara.pay.service.IPayOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 订单Service业务层处理
 *
 * @author geek
 */
@RequiredArgsConstructor
@Service
public class PayOrderServiceImpl implements IPayOrderService {

    private final PayOrderMapper baseMapper;

    @Override
    public TableDataInfo<PayOrderVo> selectPageList(PayOrderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PayOrder> lqw = buildQueryWrapper(bo);
        Page<PayOrderVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<PayOrderVo> selectList(PayOrderBo bo) {
        LambdaQueryWrapper<PayOrder> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    @Override
    public PayOrderVo selectById(Long orderId) {
        return baseMapper.selectVoById(orderId);
    }

    @Override
    public PayOrder selectPayOrderByOrderNumber(String orderNumber) {
        PayOrder payOrder = baseMapper.selectOne(
                Wrappers.<PayOrder>lambdaQuery().eq(PayOrder::getOrderNumber, orderNumber));
        if (payOrder == null) {
            throw new ServiceException("订单不存在，请检查订单号是否正确");
        }
        return payOrder;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertPayOrder(PayOrder payOrder) {
        return baseMapper.insert(payOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePayOrder(PayOrder payOrder) {
        return baseMapper.updateById(payOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] orderIds) {
        return baseMapper.deleteBatchIds(List.of(orderIds));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByOrderNumber(String orderNumber) {
        return baseMapper.delete(
                Wrappers.<PayOrder>lambdaQuery().eq(PayOrder::getOrderNumber, orderNumber));
    }

    @Override
    public int updateStatus(String orderNumber, String orderStatus) {
        return baseMapper.updateStatus(orderNumber, orderStatus);
    }

    private LambdaQueryWrapper<PayOrder> buildQueryWrapper(PayOrderBo bo) {
        LambdaQueryWrapper<PayOrder> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getOrderNumber()), PayOrder::getOrderNumber, bo.getOrderNumber());
        lqw.eq(StringUtils.isNotBlank(bo.getThirdNumber()), PayOrder::getThirdNumber, bo.getThirdNumber());
        lqw.eq(StringUtils.isNotBlank(bo.getOrderStatus()), PayOrder::getOrderStatus, bo.getOrderStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getPayType()), PayOrder::getPayType, bo.getPayType());
        return lqw;
    }

}
