package org.dromara.erp.pu.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.erp.pu.domain.PuPurchaseOrder;
import org.dromara.erp.pu.domain.vo.PuPurchaseOrderVo;

/**
 * 采购订单 Mapper 接口
 * <p>
 * 提供采购订单主表的基础增删改查功能，继承自 {@link BaseMapperPlus} 自动获得通用 CRUD 方法。
 *
 * @author ruoyi
 */
public interface PuPurchaseOrderMapper extends BaseMapperPlus<PuPurchaseOrder, PuPurchaseOrderVo> {
}
