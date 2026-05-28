package com.nexus.erp.pu.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.pu.domain.PuPurchaseOrder;
import com.nexus.erp.pu.domain.vo.PuPurchaseOrderVo;

/**
 * 采购订单 Mapper 接口
 * <p>
 * 提供采购订单主表的基础增删改查功能，继承自 {@link BaseMapperPlus} 自动获得通用 CRUD 方法。
 *
 * @author ruoyi
 */
public interface PuPurchaseOrderMapper extends BaseMapperPlus<PuPurchaseOrder, PuPurchaseOrderVo> {
}
