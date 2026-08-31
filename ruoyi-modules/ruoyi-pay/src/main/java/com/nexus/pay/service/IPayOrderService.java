package com.nexus.pay.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.pay.domain.PayOrder;
import com.nexus.pay.domain.bo.PayOrderBo;
import com.nexus.pay.domain.vo.PayOrderVo;

import java.util.List;

/**
 * 订单Service接口
 *
 * @author geek
 */
public interface IPayOrderService {

    /**
     * 分页查询支付订单列表。
     *
     * @param bo 订单查询条件（订单号、第三方订单号、状态、支付方式等）
     * @param pageQuery 分页参数
     * @return 分页后的订单视图列表
     */
    TableDataInfo<PayOrderVo> selectPageList(PayOrderBo bo, PageQuery pageQuery);

    /**
     * 按条件查询全部支付订单列表（不分页）。
     *
     * @param bo 订单查询条件
     * @return 订单视图列表
     */
    List<PayOrderVo> selectList(PayOrderBo bo);

    /**
     * 根据订单 ID 查询订单详情。
     *
     * @param orderId 订单 ID
     * @return 订单视图对象
     */
    PayOrderVo selectById(Long orderId);

    /**
     * 根据商户订单号查询支付订单；订单不存在时抛出异常。
     *
     * @param orderNumber 商户订单号
     * @return 支付订单实体
     */
    PayOrder selectPayOrderByOrderNumber(String orderNumber);

    /**
     * 新增支付订单。
     *
     * @param payOrder 订单实体
     * @return 受影响行数
     */
    int insertPayOrder(PayOrder payOrder);

    /**
     * 修改支付订单信息。
     *
     * @param payOrder 订单实体
     * @return 受影响行数
     */
    int updatePayOrder(PayOrder payOrder);

    /**
     * 批量删除支付订单。
     *
     * @param orderIds 待删除的订单 ID 数组
     * @return 受影响行数
     */
    int deleteByIds(Long[] orderIds);

    /**
     * 根据商户订单号删除支付订单。
     *
     * @param orderNumber 商户订单号
     * @return 受影响行数
     */
    int deleteByOrderNumber(String orderNumber);

    /**
     * 根据商户订单号更新订单状态。
     *
     * @param orderNumber 商户订单号
     * @param orderStatus 目标订单状态
     * @return 受影响行数
     */
    int updateStatus(String orderNumber, String orderStatus);

}
