package org.dromara.pay.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.pay.domain.PayOrder;
import org.dromara.pay.domain.bo.PayOrderBo;
import org.dromara.pay.domain.vo.PayOrderVo;

import java.util.List;

/**
 * 订单Service接口
 *
 * @author geek
 */
public interface IPayOrderService {

    TableDataInfo<PayOrderVo> selectPageList(PayOrderBo bo, PageQuery pageQuery);

    List<PayOrderVo> selectList(PayOrderBo bo);

    PayOrderVo selectById(Long orderId);

    PayOrder selectPayOrderByOrderNumber(String orderNumber);

    int insertPayOrder(PayOrder payOrder);

    int updatePayOrder(PayOrder payOrder);

    int deleteByIds(Long[] orderIds);

    int deleteByOrderNumber(String orderNumber);

    int updateStatus(String orderNumber, String orderStatus);

}
