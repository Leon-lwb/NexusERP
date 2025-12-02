package com.nexus.core.wm.service;

import java.util.List;
import com.nexus.core.wm.domain.WmStorageLocation;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 库位Service接口
 *
 * @author nexus
 * @date 2025-12-02
 */
public interface IWmStorageLocationService extends IService<WmStorageLocation> {
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
     * 批量删除库位
     *
     * @param locationIds 需要删除的库位主键集合
     * @return 结果
     */
    public int deleteWmStorageLocationByLocationIds(Long[] locationIds);

    /**
     * 删除库位信息
     *
     * @param locationId 库位主键
     * @return 结果
     */
    public int deleteWmStorageLocationByLocationId(Long locationId);
}
