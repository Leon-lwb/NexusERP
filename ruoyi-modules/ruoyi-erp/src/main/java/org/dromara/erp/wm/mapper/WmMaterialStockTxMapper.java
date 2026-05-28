package org.dromara.erp.wm.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.erp.wm.domain.WmMaterialStockTx;
import org.dromara.erp.wm.domain.vo.WmMaterialStockTxVo;

/**
 * 库存事务Mapper接口
 *
 * @author ruoyi
 * @date 2025-12-12
 */
@Mapper
public interface WmMaterialStockTxMapper extends BaseMapperPlus<WmMaterialStockTx, WmMaterialStockTxVo> {

}
