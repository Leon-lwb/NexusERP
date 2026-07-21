package com.nexus.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.pay.domain.PayOrder;
import com.nexus.pay.domain.bo.PayOrderBo;
import com.nexus.pay.domain.vo.PayOrderVo;
import com.nexus.pay.mapper.PayOrderMapper;
import com.nexus.pay.service.IPayOrderService;
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

    /** 订单数据访问映射，封装订单表的增删改查。 */
    private final PayOrderMapper baseMapper;

    /**
     * 分页查询支付订单列表。
     *
     * @param bo 订单查询条件
     * @param pageQuery 分页参数
     * @return 分页后的订单视图列表
     */
    @Override
    public TableDataInfo<PayOrderVo> selectPageList(PayOrderBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PayOrder> lqw = buildQueryWrapper(bo);
        Page<PayOrderVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 按条件查询全部支付订单列表（不分页）。
     *
     * @param bo 订单查询条件
     * @return 订单视图列表
     */
    @Override
    public List<PayOrderVo> selectList(PayOrderBo bo) {
        LambdaQueryWrapper<PayOrder> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    /**
     * 根据订单 ID 查询订单详情。
     *
     * @param orderId 订单 ID
     * @return 订单视图对象
     */
    @Override
    public PayOrderVo selectById(Long orderId) {
        return baseMapper.selectVoById(orderId);
    }

    /**
     * 根据商户订单号查询支付订单；订单不存在时抛出业务异常。
     *
     * @param orderNumber 商户订单号
     * @return 支付订单实体
     */
    @Override
    public PayOrder selectPayOrderByOrderNumber(String orderNumber) {
        PayOrder payOrder = baseMapper.selectOne(
                Wrappers.<PayOrder>lambdaQuery().eq(PayOrder::getOrderNumber, orderNumber));
        if (payOrder == null) {
            throw new ServiceException("订单不存在，请检查订单号是否正确");
        }
        return payOrder;
    }

    /**
     * 新增支付订单。
     *
     * @param payOrder 订单实体
     * @return 受影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertPayOrder(PayOrder payOrder) {
        return baseMapper.insert(payOrder);
    }

    /**
     * 修改支付订单信息。
     *
     * @param payOrder 订单实体
     * @return 受影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePayOrder(PayOrder payOrder) {
        return baseMapper.updateById(payOrder);
    }

    /**
     * 批量删除支付订单。
     *
     * @param orderIds 待删除的订单 ID 数组
     * @return 受影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] orderIds) {
        return baseMapper.deleteBatchIds(List.of(orderIds));
    }

    /**
     * 根据商户订单号删除支付订单。
     *
     * @param orderNumber 商户订单号
     * @return 受影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByOrderNumber(String orderNumber) {
        return baseMapper.delete(
                Wrappers.<PayOrder>lambdaQuery().eq(PayOrder::getOrderNumber, orderNumber));
    }

    /**
     * 根据商户订单号更新订单状态。
     *
     * @param orderNumber 商户订单号
     * @param orderStatus 目标订单状态
     * @return 受影响行数
     */
    @Override
    public int updateStatus(String orderNumber, String orderStatus) {
        return baseMapper.update(null,
                Wrappers.<PayOrder>lambdaUpdate()
                        .set(PayOrder::getOrderStatus, orderStatus)
                        .eq(PayOrder::getOrderNumber, orderNumber));
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
