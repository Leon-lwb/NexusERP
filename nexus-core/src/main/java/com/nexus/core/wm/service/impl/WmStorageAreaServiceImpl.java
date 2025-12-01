package com.nexus.core.wm.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.wm.mapper.WmStorageAreaMapper;
import com.nexus.core.wm.domain.WmStorageArea;
import com.nexus.core.wm.service.IWmStorageAreaService;

/**
 * 库区Service业务层处理
 *
 * @author nexus
 * @date 2025-12-01
 */
@Service
public class WmStorageAreaServiceImpl extends ServiceImpl<WmStorageAreaMapper, WmStorageArea> implements IWmStorageAreaService {

    @Autowired
    private WmStorageAreaMapper wmStorageAreaMapper;

    /**
     * 查询库区
     *
     * @param areaId 库区主键
     * @return 库区
     */
    @Override
    public WmStorageArea selectWmStorageAreaByAreaId(Long areaId) {
        return wmStorageAreaMapper.selectWmStorageAreaByAreaId(areaId);
    }

    /**
     * 查询库区列表
     *
     * @param wmStorageArea 库区
     * @return 库区
     */
    @Override
    public List<WmStorageArea> selectWmStorageAreaList(WmStorageArea wmStorageArea) {
        return wmStorageAreaMapper.selectWmStorageAreaList(wmStorageArea);
    }

    /**
     * 新增库区
     *
     * @param wmStorageArea 库区
     * @return 结果
     */
    @Override
    public int insertWmStorageArea(WmStorageArea wmStorageArea) {
                wmStorageArea.setCreateTime(DateUtils.getNowDate());
            return wmStorageAreaMapper.insertWmStorageArea(wmStorageArea);
    }

    /**
     * 修改库区
     *
     * @param wmStorageArea 库区
     * @return 结果
     */
    @Override
    public int updateWmStorageArea(WmStorageArea wmStorageArea) {
                wmStorageArea.setUpdateTime(DateUtils.getNowDate());
        return wmStorageAreaMapper.updateWmStorageArea(wmStorageArea);
    }

    /**
     * 批量删除库区
     *
     * @param areaIds 需要删除的库区主键
     * @return 结果
     */
    @Override
    public int deleteWmStorageAreaByAreaIds(Long[] areaIds) {
        return wmStorageAreaMapper.deleteWmStorageAreaByAreaIds(areaIds);
    }

    /**
     * 删除库区信息
     *
     * @param areaId 库区主键
     * @return 结果
     */
    @Override
    public int deleteWmStorageAreaByAreaId(Long areaId) {
        return wmStorageAreaMapper.deleteWmStorageAreaByAreaId(areaId);
    }
}
