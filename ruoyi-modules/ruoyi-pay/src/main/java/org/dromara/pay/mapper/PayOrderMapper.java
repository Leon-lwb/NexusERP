package org.dromara.pay.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.pay.domain.PayOrder;
import org.dromara.pay.domain.vo.PayOrderVo;

/**
 * 订单Mapper接口
 *
 * @author Dftre
 */
public interface PayOrderMapper extends BaseMapperPlus<PayOrder, PayOrderVo> {

    /**
     * 更新订单状态
     */
    int updateStatus(String orderNumber, String orderStatus);

}
