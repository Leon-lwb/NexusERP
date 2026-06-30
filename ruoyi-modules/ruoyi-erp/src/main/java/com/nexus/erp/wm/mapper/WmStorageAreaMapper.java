package com.nexus.erp.wm.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.wm.domain.WmStorageArea;
import com.nexus.erp.wm.domain.vo.WmStorageArea3dVo;
import com.nexus.erp.wm.domain.vo.WmStorageAreaTreeVo;
import com.nexus.erp.wm.domain.vo.WmStorageAreaVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 库区Mapper接口
 *
 * @author nexus
 */
public interface WmStorageAreaMapper extends BaseMapperPlus<WmStorageArea, WmStorageAreaVo> {

    /**
     * 分页查询库区列表（含仓库名称）
     *
     * @param page    分页参数
     * @param wrapper 查询条件
     * @return 库区分页列表
     */
    IPage<WmStorageAreaVo> selectVoPageWithWarehouse(IPage<WmStorageArea> page, @Param("ew") Wrapper<WmStorageArea> wrapper);

    /**
     * 查询库区列表（含仓库名称）
     *
     * @param wrapper 查询条件
     * @return 库区列表
     */
    List<WmStorageAreaVo> selectVoListWithWarehouse(@Param("ew") Wrapper<WmStorageArea> wrapper);

    /**
     * 查询库区树形列表（仅包含树所需字段）
     *
     * @return 库区树形数据列表
     */
    List<WmStorageAreaTreeVo> selectAreaTreeList();

    /**
     * 查询指定仓库的库区 3D 可视化列表（含 3D 字段）
     *
     * @param warehouseId 仓库ID
     * @return 库区 3D 视图列表
     */
    List<WmStorageArea3dVo> selectArea3dByWarehouseId(@Param("warehouseId") Long warehouseId);
}
