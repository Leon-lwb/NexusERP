package org.dromara.erp.pu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.erp.pu.domain.PuPurchaseReturnLine;

/**
 * 采购退货单明细 Mapper 接口
 * <p>
 * 提供采购退货明细行的基础增删改查功能，继承自 {@link BaseMapperPlus} 自动获得通用 CRUD 方法。
 *
 * @author ruoyi
 */
@Mapper
public interface PuPurchaseReturnLineMapper extends BaseMapperPlus<PuPurchaseReturnLine, PuPurchaseReturnLine>, BaseMapper<PuPurchaseReturnLine> {
}
