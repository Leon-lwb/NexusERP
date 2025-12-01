package com.nexus.core.wm.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.wm.domain.WmStorageArea;

/**
 * 库区Mapper接口
 *
 * @author nexus
 * @date 2025-12-01
 */
public interface WmStorageAreaMapper extends BaseMapper<WmStorageArea> {
    /**
     * 查询库区
     *
     * @param areaId 库区主键
     * @return 库区
     */
    public WmStorageArea selectWmStorageAreaByAreaId(Long areaId);

    /**
     * 查询库区列表
     *
     * @param wmStorageArea 库区
     * @return 库区集合
     */
    public List<WmStorageArea> selectWmStorageAreaList(WmStorageArea wmStorageArea);

    /**
     * 新增库区
     *
     * @param wmStorageArea 库区
     * @return 结果
     */
    public int insertWmStorageArea(WmStorageArea wmStorageArea);

    /**
     * 修改库区
     *
     * @param wmStorageArea 库区
     * @return 结果
     */
    public int updateWmStorageArea(WmStorageArea wmStorageArea);

    /**
     * 删除库区
     *
     * @param areaId 库区主键
     * @return 结果
     */
    public int deleteWmStorageAreaByAreaId(Long areaId);

    /**
     * 批量删除库区
     *
     * @param areaIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteWmStorageAreaByAreaIds(Long[] areaIds);
}
