package com.nexus.core.sch.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.List;

import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.sch.mapper.SchEmployeeScheduleMapper;
import com.nexus.core.sch.domain.SchEmployeeSchedule;
import com.nexus.core.sch.service.ISchEmployeeScheduleService;

/**
 * 员工排班明细Service业务层处理
 *
 * @author nexus
 * @date 2025-12-30
 */
@Service
public class SchEmployeeScheduleServiceImpl extends ServiceImpl<SchEmployeeScheduleMapper, SchEmployeeSchedule> implements ISchEmployeeScheduleService {

    @Autowired
    private SchEmployeeScheduleMapper schEmployeeScheduleMapper;

    /**
     * 查询员工排班明细
     *
     * @param scheduleId 员工排班明细主键
     * @return 员工排班明细
     */
    @Override
    public SchEmployeeSchedule selectSchEmployeeScheduleByScheduleId(Long scheduleId) {
        return schEmployeeScheduleMapper.selectSchEmployeeScheduleByScheduleId(scheduleId);
    }

    /**
     * 查询员工排班明细列表
     *
     * @param schEmployeeSchedule 员工排班明细
     * @return 员工排班明细
     */
    @Override
    public List<SchEmployeeSchedule> selectSchEmployeeScheduleList(SchEmployeeSchedule schEmployeeSchedule) {
        return schEmployeeScheduleMapper.selectSchEmployeeScheduleList(schEmployeeSchedule);
    }

    /**
     * 新增员工排班明细
     *
     * @param schEmployeeSchedule 员工排班明细
     * @return 结果
     */
    @Override
    public int insertSchEmployeeSchedule(SchEmployeeSchedule schEmployeeSchedule) {
        schEmployeeSchedule.setCreateTime(DateUtils.getNowDate());
        return schEmployeeScheduleMapper.insertSchEmployeeSchedule(schEmployeeSchedule);
    }

    /**
     * 修改员工排班明细
     *
     * @param schEmployeeSchedule 员工排班明细
     * @return 结果
     */
    @Override
    public int updateSchEmployeeSchedule(SchEmployeeSchedule schEmployeeSchedule) {
        schEmployeeSchedule.setUpdateTime(DateUtils.getNowDate());
        return schEmployeeScheduleMapper.updateSchEmployeeSchedule(schEmployeeSchedule);
    }

    /**
     * 批量删除员工排班明细
     *
     * @param scheduleIds 需要删除的员工排班明细主键
     * @return 结果
     */
    @Override
    public int deleteSchEmployeeScheduleByScheduleIds(Long[] scheduleIds) {
        return schEmployeeScheduleMapper.deleteSchEmployeeScheduleByScheduleIds(scheduleIds);
    }

    /**
     * 删除员工排班明细信息
     *
     * @param scheduleId 员工排班明细主键
     * @return 结果
     */
    @Override
    public int deleteSchEmployeeScheduleByScheduleId(Long scheduleId) {
        return schEmployeeScheduleMapper.deleteSchEmployeeScheduleByScheduleId(scheduleId);
    }
}
