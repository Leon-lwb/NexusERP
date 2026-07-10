package com.nexus.erp.wm.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.wm.domain.WmStorageLocation;
import com.nexus.erp.wm.domain.vo.WmStorageLocation3dVo;
import com.nexus.erp.wm.domain.vo.WmStorageLocationTreeVo;
import com.nexus.erp.wm.domain.vo.WmStorageLocationVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 库位Mapper接口
 *
 * @author nexus
 */
public interface WmStorageLocationMapper extends BaseMapperPlus<WmStorageLocation, WmStorageLocationVo> {

    /**
     * 查询库位树形列表（仅包含树所需字段）
     *
     * @return 库位树形数据列表
     */
    @Select("SELECT location_id, location_code, location_name, area_id, location_type, enable_flag, status, occupancy_rate " +
            "FROM wm_storage_location " +
            "WHERE del_flag = '0' " +
            "ORDER BY area_id, location_id")
    List<WmStorageLocationTreeVo> selectLocationTreeList();

    /**
     * 查询指定仓库的库位 3D 可视化列表（含 3D 字段）
     *
     * @param warehouseId 仓库ID
     * @return 库位 3D 视图列表
     */
    @Select("SELECT location_id, location_code, location_name, area_id, " +
            "pos_x, pos_y, pos_z, width, height, location_depth, " +
            "rotation_x, rotation_y, rotation_z, " +
            "color_empty, color_occupied, color_locked, color_warning, opacity, " +
            "show_border, border_color, border_width, show_label, label_position, label_size, " +
            "frozen_flag, enable_flag, rack_id, layer, col, row_num, status, occupancy_rate " +
            "FROM wm_storage_location " +
            "WHERE warehouse_id = #{warehouseId} AND del_flag = '0' " +
            "ORDER BY area_id, location_id")
    List<WmStorageLocation3dVo> selectLocation3dByWarehouseId(@Param("warehouseId") Long warehouseId);
}