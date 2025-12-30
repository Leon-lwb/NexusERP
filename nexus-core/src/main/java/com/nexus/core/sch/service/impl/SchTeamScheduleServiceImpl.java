package com.nexus.core.sch.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.List;

import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.sch.mapper.SchTeamScheduleMapper;
import com.nexus.core.sch.domain.SchTeamSchedule;
import com.nexus.core.sch.service.ISchTeamScheduleService;

/**
 * 班组排班（模板）Service业务层处理
 *
 * @author nexus
 * @date 2025-12-30
 */
@Service
public class SchTeamScheduleServiceImpl extends ServiceImpl<SchTeamScheduleMapper, SchTeamSchedule> implements ISchTeamScheduleService {

    @Autowired
    private SchTeamScheduleMapper schTeamScheduleMapper;

    /**
     * 查询班组排班（模板）
     *
     * @param teamScheduleId 班组排班（模板）主键
     * @return 班组排班（模板）
     */
    @Override
    public SchTeamSchedule selectSchTeamScheduleByTeamScheduleId(Long teamScheduleId) {
        return schTeamScheduleMapper.selectSchTeamScheduleByTeamScheduleId(teamScheduleId);
    }

    /**
     * 查询班组排班（模板）列表
     *
     * @param schTeamSchedule 班组排班（模板）
     * @return 班组排班（模板）
     */
    @Override
    public List<SchTeamSchedule> selectSchTeamScheduleList(SchTeamSchedule schTeamSchedule) {
        return schTeamScheduleMapper.selectSchTeamScheduleList(schTeamSchedule);
    }

    /**
     * 新增班组排班（模板）
     *
     * @param schTeamSchedule 班组排班（模板）
     * @return 结果
     */
    @Override
    public int insertSchTeamSchedule(SchTeamSchedule schTeamSchedule) {
        schTeamSchedule.setCreateTime(DateUtils.getNowDate());
        return schTeamScheduleMapper.insertSchTeamSchedule(schTeamSchedule);
    }

    /**
     * 修改班组排班（模板）
     *
     * @param schTeamSchedule 班组排班（模板）
     * @return 结果
     */
    @Override
    public int updateSchTeamSchedule(SchTeamSchedule schTeamSchedule) {
        schTeamSchedule.setUpdateTime(DateUtils.getNowDate());
        return schTeamScheduleMapper.updateSchTeamSchedule(schTeamSchedule);
    }

    /**
     * 批量删除班组排班（模板）
     *
     * @param teamScheduleIds 需要删除的班组排班（模板）主键
     * @return 结果
     */
    @Override
    public int deleteSchTeamScheduleByTeamScheduleIds(Long[] teamScheduleIds) {
        return schTeamScheduleMapper.deleteSchTeamScheduleByTeamScheduleIds(teamScheduleIds);
    }

    /**
     * 删除班组排班（模板）信息
     *
     * @param teamScheduleId 班组排班（模板）主键
     * @return 结果
     */
    @Override
    public int deleteSchTeamScheduleByTeamScheduleId(Long teamScheduleId) {
        return schTeamScheduleMapper.deleteSchTeamScheduleByTeamScheduleId(teamScheduleId);
    }
}
