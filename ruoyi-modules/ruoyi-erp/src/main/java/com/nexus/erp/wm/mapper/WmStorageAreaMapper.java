package com.nexus.erp.wm.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.wm.domain.WmStorageArea;
import com.nexus.erp.wm.domain.vo.WmStorageArea3dVo;
import com.nexus.erp.wm.domain.vo.WmStorageAreaTreeVo;
import com.nexus.erp.wm.domain.vo.WmStorageAreaVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 库区Mapper接口
 *
 * @author nexus
 */
public interface WmStorageAreaMapper extends BaseMapperPlus<WmStorageArea, WmStorageAreaVo> {

    /**
     * 查询库区树形列表（仅包含树所需字段）
     *
     * @return 库区树形数据列表
     */
    @Select("SELECT area_id, area_code, area_name, warehouse_id, location_flag, enable_flag, layout_type " +
            "FROM wm_storage_area " +
            "WHERE del_flag = '0' " +
            "ORDER BY warehouse_id, area_id")
    List<WmStorageAreaTreeVo> selectAreaTreeList();

    /**
     * 查询指定仓库的库区 3D 可视化列表（含 3D 字段）
     *
     * @param warehouseId 仓库ID
     * @return 库区 3D 视图列表
     */
    @Select("SELECT area_id, area_code, area_name, warehouse_id, layout_type, " +
            "pos_x, pos_y, pos_z, area_width, area_length, area_height, " +
            "rotation_x, rotation_y, rotation_z, color, opacity, " +
            "model_type, model_url, show_walls, show_label, grid_visible, config_json " +
            "FROM wm_storage_area " +
            "WHERE warehouse_id = #{warehouseId} AND del_flag = '0' " +
            "ORDER BY area_id")
    List<WmStorageArea3dVo> selectArea3dByWarehouseId(@Param("warehouseId") Long warehouseId);
}