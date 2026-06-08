package com.nexus.erp.wm.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.domain.vo.WmWarehouseTreeVo;
import com.nexus.erp.wm.domain.vo.WmWarehouseVo;

import java.util.List;

/**
 * 仓库Mapper接口
 *
 * @author nexus
 */
public interface WmWarehouseMapper extends BaseMapperPlus<WmWarehouse, WmWarehouseVo> {

    /**
     * 查询仓库树形列表（仅包含树所需字段，含库区数量）
     *
     * @return 仓库树形数据列表
     */
    List<WmWarehouseTreeVo> selectWarehouseTreeList();
}
