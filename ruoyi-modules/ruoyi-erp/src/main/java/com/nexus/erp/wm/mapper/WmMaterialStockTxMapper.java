package com.nexus.erp.wm.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.vo.WmMaterialStockTxVo;

/**
 * 库存事务Mapper接口
 *
 * @author nexus
 * @date 2025-12-12
 */
@Mapper
public interface WmMaterialStockTxMapper extends BaseMapperPlus<WmMaterialStockTx, WmMaterialStockTxVo> {

}
