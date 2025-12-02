package com.nexus.core.wm.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.wm.domain.WmStorageLocation;

/**
 * 库位Mapper接口
 *
 * @author nexus
 * @date 2025-12-02
 */
public interface WmStorageLocationMapper extends BaseMapper<WmStorageLocation> {
    /**
     * 查询库位
     *
     * @param locationId 库位主键
     * @return 库位
     */
    public WmStorageLocation selectWmStorageLocationByLocationId(Long locationId);

    /**
     * 查询库位列表
     *
     * @param wmStorageLocation 库位
     * @return 库位集合
     */
    public List<WmStorageLocation> selectWmStorageLocationList(WmStorageLocation wmStorageLocation);

    /**
     * 新增库位
     *
     * @param wmStorageLocation 库位
     * @return 结果
     */
    public int insertWmStorageLocation(WmStorageLocation wmStorageLocation);

    /**
     * 修改库位
     *
     * @param wmStorageLocation 库位
     * @return 结果
     */
    public int updateWmStorageLocation(WmStorageLocation wmStorageLocation);

    /**
     * 删除库位
     *
     * @param locationId 库位主键
     * @return 结果
     */
    public int deleteWmStorageLocationByLocationId(Long locationId);

    /**
     * 批量删除库位
     *
     * @param locationIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteWmStorageLocationByLocationIds(Long[] locationIds);
}
