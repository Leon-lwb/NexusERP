package com.nexus.core.bd.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.bd.domain.BdWorkshop;

/**
 * 车间设置Mapper接口
 *
 * @author nexus
 * @date 2025-11-18
 */
public interface BdWorkshopMapper extends BaseMapper<BdWorkshop> {
    /**
     * 查询车间设置
     *
     * @param workshopId 车间设置主键
     * @return 车间设置
     */
    public BdWorkshop selectBdWorkshopByWorkshopId(Long workshopId);

    /**
     * 查询车间设置列表
     *
     * @param bdWorkshop 车间设置
     * @return 车间设置集合
     */
    public List<BdWorkshop> selectBdWorkshopList(BdWorkshop bdWorkshop);

    /**
     * 新增车间设置
     *
     * @param bdWorkshop 车间设置
     * @return 结果
     */
    public int insertBdWorkshop(BdWorkshop bdWorkshop);

    /**
     * 修改车间设置
     *
     * @param bdWorkshop 车间设置
     * @return 结果
     */
    public int updateBdWorkshop(BdWorkshop bdWorkshop);

    /**
     * 删除车间设置
     *
     * @param workshopId 车间设置主键
     * @return 结果
     */
    public int deleteBdWorkshopByWorkshopId(Long workshopId);

    /**
     * 批量删除车间设置
     *
     * @param workshopIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteBdWorkshopByWorkshopIds(Long[] workshopIds);
}
