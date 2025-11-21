package com.nexus.core.bd.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.bd.mapper.BdWorkshopMapper;
import com.nexus.core.bd.domain.BdWorkshop;
import com.nexus.core.bd.service.IBdWorkshopService;

/**
 * 车间设置Service业务层处理
 *
 * @author nexus
 * @date 2025-11-18
 */
@Service
public class BdWorkshopServiceImpl extends ServiceImpl<BdWorkshopMapper, BdWorkshop> implements IBdWorkshopService {

    @Autowired
    private BdWorkshopMapper bdWorkshopMapper;

    /**
     * 查询车间设置
     *
     * @param workshopId 车间设置主键
     * @return 车间设置
     */
    @Override
    public BdWorkshop selectBdWorkshopByWorkshopId(Long workshopId) {
        return bdWorkshopMapper.selectBdWorkshopByWorkshopId(workshopId);
    }

    /**
     * 查询车间设置列表
     *
     * @param bdWorkshop 车间设置
     * @return 车间设置
     */
    @Override
    public List<BdWorkshop> selectBdWorkshopList(BdWorkshop bdWorkshop) {
        return bdWorkshopMapper.selectBdWorkshopList(bdWorkshop);
    }

    /**
     * 新增车间设置
     *
     * @param bdWorkshop 车间设置
     * @return 结果
     */
    @Override
    public int insertBdWorkshop(BdWorkshop bdWorkshop) {
                bdWorkshop.setCreateTime(DateUtils.getNowDate());
            return bdWorkshopMapper.insertBdWorkshop(bdWorkshop);
    }

    /**
     * 修改车间设置
     *
     * @param bdWorkshop 车间设置
     * @return 结果
     */
    @Override
    public int updateBdWorkshop(BdWorkshop bdWorkshop) {
                bdWorkshop.setUpdateTime(DateUtils.getNowDate());
        return bdWorkshopMapper.updateBdWorkshop(bdWorkshop);
    }

    /**
     * 批量删除车间设置
     *
     * @param workshopIds 需要删除的车间设置主键
     * @return 结果
     */
    @Override
    public int deleteBdWorkshopByWorkshopIds(Long[] workshopIds) {
        return bdWorkshopMapper.deleteBdWorkshopByWorkshopIds(workshopIds);
    }

    /**
     * 删除车间设置信息
     *
     * @param workshopId 车间设置主键
     * @return 结果
     */
    @Override
    public int deleteBdWorkshopByWorkshopId(Long workshopId) {
        return bdWorkshopMapper.deleteBdWorkshopByWorkshopId(workshopId);
    }
}
