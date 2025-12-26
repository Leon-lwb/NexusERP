package com.nexus.core.sch.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.sch.mapper.SchPlanTeamMapper;
import com.nexus.core.sch.domain.SchPlanTeam;
import com.nexus.core.sch.service.ISchPlanTeamService;

/**
 * 计划班组关联Service业务层处理
 *
 * @author nexus
 * @date 2025-12-26
 */
@Service
public class SchPlanTeamServiceImpl extends ServiceImpl<SchPlanTeamMapper, SchPlanTeam> implements ISchPlanTeamService {

    @Autowired
    private SchPlanTeamMapper schPlanTeamMapper;

    /**
     * 查询计划班组关联
     *
     * @param recordId 计划班组关联主键
     * @return 计划班组关联
     */
    @Override
    public SchPlanTeam selectSchPlanTeamByRecordId(Long recordId) {
        return schPlanTeamMapper.selectSchPlanTeamByRecordId(recordId);
    }

    /**
     * 查询计划班组关联列表
     *
     * @param schPlanTeam 计划班组关联
     * @return 计划班组关联
     */
    @Override
    public List<SchPlanTeam> selectSchPlanTeamList(SchPlanTeam schPlanTeam) {
        return schPlanTeamMapper.selectSchPlanTeamList(schPlanTeam);
    }

    /**
     * 新增计划班组关联
     *
     * @param schPlanTeam 计划班组关联
     * @return 结果
     */
    @Override
    public int insertSchPlanTeam(SchPlanTeam schPlanTeam) {
                schPlanTeam.setCreateTime(DateUtils.getNowDate());
            return schPlanTeamMapper.insertSchPlanTeam(schPlanTeam);
    }

    /**
     * 修改计划班组关联
     *
     * @param schPlanTeam 计划班组关联
     * @return 结果
     */
    @Override
    public int updateSchPlanTeam(SchPlanTeam schPlanTeam) {
                schPlanTeam.setUpdateTime(DateUtils.getNowDate());
        return schPlanTeamMapper.updateSchPlanTeam(schPlanTeam);
    }

    /**
     * 批量删除计划班组关联
     *
     * @param recordIds 需要删除的计划班组关联主键
     * @return 结果
     */
    @Override
    public int deleteSchPlanTeamByRecordIds(Long[] recordIds) {
        return schPlanTeamMapper.deleteSchPlanTeamByRecordIds(recordIds);
    }

    /**
     * 删除计划班组关联信息
     *
     * @param recordId 计划班组关联主键
     * @return 结果
     */
    @Override
    public int deleteSchPlanTeamByRecordId(Long recordId) {
        return schPlanTeamMapper.deleteSchPlanTeamByRecordId(recordId);
    }
}
