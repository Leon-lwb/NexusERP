package com.nexus.erp.wm.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.wm.domain.WmStorageLocation;
import com.nexus.erp.wm.domain.vo.WmStorageLocation3dVo;
import com.nexus.erp.wm.domain.vo.WmStorageLocationTreeVo;
import com.nexus.erp.wm.domain.vo.WmStorageLocationVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 库位Mapper接口
 *
 * @author nexus
 */
public interface WmStorageLocationMapper extends BaseMapperPlus<WmStorageLocation, WmStorageLocationVo> {

    /**
     * 分页查询库位列表（含库区和仓库名称）
     *
     * @param page    分页参数
     * @param wrapper 查询条件
     * @return 库位分页列表
     */
    IPage<WmStorageLocationVo> selectVoPageWithArea(IPage<WmStorageLocation> page, @Param("ew") Wrapper<WmStorageLocation> wrapper);

    /**
     * 查询库位列表（含库区和仓库名称）
     *
     * @param wrapper 查询条件
     * @return 库位列表
     */
    List<WmStorageLocationVo> selectVoListWithArea(@Param("ew") Wrapper<WmStorageLocation> wrapper);

    /**
     * 查询库位树形列表（仅包含树所需字段）
     *
     * @return 库位树形数据列表
     */
    List<WmStorageLocationTreeVo> selectLocationTreeList();

    /**
     * 查询指定仓库的库位 3D 可视化列表（含 3D 字段）
     *
     * @param warehouseId 仓库ID
     * @return 库位 3D 视图列表
     */
    List<WmStorageLocation3dVo> selectLocation3dByWarehouseId(@Param("warehouseId") Long warehouseId);
}
