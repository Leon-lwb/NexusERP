package com.nexus.erp.pu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.pu.domain.PuPurchaseReturn;
import com.nexus.erp.pu.domain.vo.PuPurchaseReturnVo;

/**
 * 采购退货单 Mapper 接口
 * <p>
 * 提供采购退货单主表的基础增删改查功能，继承自 {@link BaseMapperPlus} 自动获得通用 CRUD 方法。
 *
 * @author ruoyi
 */
@Mapper
public interface PuPurchaseReturnMapper extends BaseMapperPlus<PuPurchaseReturn, PuPurchaseReturnVo>, BaseMapper<PuPurchaseReturn> {
}
