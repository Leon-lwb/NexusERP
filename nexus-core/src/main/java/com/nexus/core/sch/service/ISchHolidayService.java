package com.nexus.core.sch.service;

import java.util.List;
import com.nexus.core.sch.domain.SchHoliday;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 节假日设置Service接口
 *
 * @author nexus
 * @date 2025-12-17
 */
public interface ISchHolidayService extends IService<SchHoliday> {
    /**
     * 查询节假日设置
     *
     * @param holidayId 节假日设置主键
     * @return 节假日设置
     */
    public SchHoliday selectSchHolidayByHolidayId(Long holidayId);

    /**
     * 查询节假日设置列表
     *
     * @param schHoliday 节假日设置
     * @return 节假日设置集合
     */
    public List<SchHoliday> selectSchHolidayList(SchHoliday schHoliday);

    /**
     * 新增节假日设置
     *
     * @param schHoliday 节假日设置
     * @return 结果
     */
    public int insertSchHoliday(SchHoliday schHoliday);

    /**
     * 修改节假日设置
     *
     * @param schHoliday 节假日设置
     * @return 结果
     */
    public int updateSchHoliday(SchHoliday schHoliday);

    /**
     * 批量删除节假日设置
     *
     * @param holidayIds 需要删除的节假日设置主键集合
     * @return 结果
     */
    public int deleteSchHolidayByHolidayIds(Long[] holidayIds);

    /**
     * 删除节假日设置信息
     *
     * @param holidayId 节假日设置主键
     * @return 结果
     */
    public int deleteSchHolidayByHolidayId(Long holidayId);
}
