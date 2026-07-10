package com.nexus.erp.wm.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.wm.domain.WmWarehouse;
import com.nexus.erp.wm.domain.vo.WmWarehouse3dVo;
import com.nexus.erp.wm.domain.vo.WmWarehouseTreeVo;
import com.nexus.erp.wm.domain.vo.WmWarehouseVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
    @Select("SELECT w.warehouse_id, w.warehouse_code, w.warehouse_name, w.status, " +
            "(SELECT COUNT(*) FROM wm_storage_area a WHERE a.warehouse_id = w.warehouse_id AND a.del_flag = '0') AS area_count " +
            "FROM wm_warehouse w " +
            "WHERE w.del_flag = '0' " +
            "ORDER BY w.warehouse_id")
    List<WmWarehouseTreeVo> selectWarehouseTreeList();

    /**
     * 查询仓库 3D 可视化基础信息
     *
     * @param warehouseId 仓库ID
     * @return 仓库 3D 视图对象
     */
    @Select("SELECT warehouse_id, warehouse_code, warehouse_name, status " +
            "FROM wm_warehouse " +
            "WHERE warehouse_id = #{warehouseId} AND del_flag = '0'")
    WmWarehouse3dVo selectWarehouse3dById(@Param("warehouseId") Long warehouseId);
}