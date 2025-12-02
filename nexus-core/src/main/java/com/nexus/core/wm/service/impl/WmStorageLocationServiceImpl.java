package com.nexus.core.wm.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.wm.mapper.WmStorageLocationMapper;
import com.nexus.core.wm.domain.WmStorageLocation;
import com.nexus.core.wm.service.IWmStorageLocationService;

/**
 * 库位Service业务层处理
 *
 * @author nexus
 * @date 2025-12-02
 */
@Service
public class WmStorageLocationServiceImpl extends ServiceImpl<WmStorageLocationMapper, WmStorageLocation> implements IWmStorageLocationService {

    @Autowired
    private WmStorageLocationMapper wmStorageLocationMapper;

    /**
     * 查询库位
     *
     * @param locationId 库位主键
     * @return 库位
     */
    @Override
    public WmStorageLocation selectWmStorageLocationByLocationId(Long locationId) {
        return wmStorageLocationMapper.selectWmStorageLocationByLocationId(locationId);
    }

    /**
     * 查询库位列表
     *
     * @param wmStorageLocation 库位
     * @return 库位
     */
    @Override
    public List<WmStorageLocation> selectWmStorageLocationList(WmStorageLocation wmStorageLocation) {
        return wmStorageLocationMapper.selectWmStorageLocationList(wmStorageLocation);
    }

    /**
     * 新增库位
     *
     * @param wmStorageLocation 库位
     * @return 结果
     */
    @Override
    public int insertWmStorageLocation(WmStorageLocation wmStorageLocation) {
                wmStorageLocation.setCreateTime(DateUtils.getNowDate());
            return wmStorageLocationMapper.insertWmStorageLocation(wmStorageLocation);
    }

    /**
     * 修改库位
     *
     * @param wmStorageLocation 库位
     * @return 结果
     */
    @Override
    public int updateWmStorageLocation(WmStorageLocation wmStorageLocation) {
                wmStorageLocation.setUpdateTime(DateUtils.getNowDate());
        return wmStorageLocationMapper.updateWmStorageLocation(wmStorageLocation);
    }

    /**
     * 批量删除库位
     *
     * @param locationIds 需要删除的库位主键
     * @return 结果
     */
    @Override
    public int deleteWmStorageLocationByLocationIds(Long[] locationIds) {
        return wmStorageLocationMapper.deleteWmStorageLocationByLocationIds(locationIds);
    }

    /**
     * 删除库位信息
     *
     * @param locationId 库位主键
     * @return 结果
     */
    @Override
    public int deleteWmStorageLocationByLocationId(Long locationId) {
        return wmStorageLocationMapper.deleteWmStorageLocationByLocationId(locationId);
    }
}
