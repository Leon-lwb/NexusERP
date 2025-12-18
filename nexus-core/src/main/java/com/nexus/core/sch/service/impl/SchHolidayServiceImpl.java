package com.nexus.core.sch.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.sch.mapper.SchHolidayMapper;
import com.nexus.core.sch.domain.SchHoliday;
import com.nexus.core.sch.service.ISchHolidayService;

/**
 * 节假日设置Service业务层处理
 *
 * @author nexus
 * @date 2025-12-17
 */
@Service
public class SchHolidayServiceImpl extends ServiceImpl<SchHolidayMapper, SchHoliday> implements ISchHolidayService {

    @Autowired
    private SchHolidayMapper schHolidayMapper;

    /**
     * 查询节假日设置
     *
     * @param holidayId 节假日设置主键
     * @return 节假日设置
     */
    @Override
    public SchHoliday selectSchHolidayByHolidayId(Long holidayId) {
        return schHolidayMapper.selectSchHolidayByHolidayId(holidayId);
    }

    /**
     * 查询节假日设置列表
     *
     * @param schHoliday 节假日设置
     * @return 节假日设置
     */
    @Override
    public List<SchHoliday> selectSchHolidayList(SchHoliday schHoliday) {
        return schHolidayMapper.selectSchHolidayList(schHoliday);
    }

    /**
     * 新增节假日设置
     *
     * @param schHoliday 节假日设置
     * @return 结果
     */
    @Override
    public int insertSchHoliday(SchHoliday schHoliday) {
                schHoliday.setCreateTime(DateUtils.getNowDate());
            return schHolidayMapper.insertSchHoliday(schHoliday);
    }

    /**
     * 修改节假日设置
     *
     * @param schHoliday 节假日设置
     * @return 结果
     */
    @Override
    public int updateSchHoliday(SchHoliday schHoliday) {
                schHoliday.setUpdateTime(DateUtils.getNowDate());
        return schHolidayMapper.updateSchHoliday(schHoliday);
    }

    /**
     * 批量删除节假日设置
     *
     * @param holidayIds 需要删除的节假日设置主键
     * @return 结果
     */
    @Override
    public int deleteSchHolidayByHolidayIds(Long[] holidayIds) {
        return schHolidayMapper.deleteSchHolidayByHolidayIds(holidayIds);
    }

    /**
     * 删除节假日设置信息
     *
     * @param holidayId 节假日设置主键
     * @return 结果
     */
    @Override
    public int deleteSchHolidayByHolidayId(Long holidayId) {
        return schHolidayMapper.deleteSchHolidayByHolidayId(holidayId);
    }
}
