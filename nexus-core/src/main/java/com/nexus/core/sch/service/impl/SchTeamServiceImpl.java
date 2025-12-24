package com.nexus.core.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.List;

import com.nexus.common.constant.NexusConstants;
import com.nexus.common.exception.CustomException;
import com.nexus.common.utils.DateUtils;
import com.nexus.core.sch.mapper.SchTeamMemberMapper;
import com.nexus.system.strategy.AutoCodeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

import com.nexus.common.utils.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import com.nexus.core.sch.domain.SchTeamMember;
import com.nexus.core.sch.mapper.SchTeamMapper;
import com.nexus.core.sch.domain.SchTeam;
import com.nexus.core.sch.service.ISchTeamService;


/**
 * 班组设置Service业务层处理
 *
 * @author nexus
 * @date 2025-12-23
 */
@Service
public class SchTeamServiceImpl extends ServiceImpl<SchTeamMapper, SchTeam> implements ISchTeamService {

    @Autowired
    private SchTeamMapper schTeamMapper;

    @Autowired
    private AutoCodeUtil autoCodeUtil;

    @Autowired
    private SchTeamMemberMapper schTeamMemberMapper;

    /**
     * 查询班组设置
     *
     * @param teamId 班组设置主键
     * @return 班组设置
     */
    @Override
    public SchTeam selectSchTeamByTeamId(Long teamId) {
        return schTeamMapper.selectSchTeamByTeamId(teamId);
    }

    /**
     * 查询班组设置列表
     *
     * @param schTeam 班组设置
     * @return 班组设置
     */
    @Override
    public List<SchTeam> selectSchTeamList(SchTeam schTeam) {
        return schTeamMapper.selectSchTeamList(schTeam);
    }

    /**
     * 新增班组设置
     *
     * @param schTeam 班组设置
     * @return 结果
     */
    @Transactional
    @Override
    public int insertSchTeam(SchTeam schTeam) {
        schTeam.setCreateTime(DateUtils.getNowDate());
        // 自动生成班组编码，如果班组类型是NexusConstants.TEAM_CATEGORY_PRODUCTION，则生成SC开头的编码，否则生成FZ开头的编码
        // 如果都不在这两个类型中，抛出异常
        String code = generateTeamCode(schTeam.getTeamCategory());
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("无效的班组类型: " + schTeam.getTeamCategory());
        }
        schTeam.setTeamCode(code);
        int rows = schTeamMapper.insertSchTeam(schTeam);
        // 在插入班组后，schTeam 对象会自动填充生成的 team_id
        insertSchTeamMember(schTeam);
        return rows;
    }

    /**
     * 根据班组类型生成对应的编码
     *
     * @param teamCategory 班组类型
     * @return 生成的编码，如果类型无效则返回null
     */
    private String generateTeamCode(String teamCategory) {
        if (NexusConstants.TEAM_CATEGORY_PRODUCTION_LIST.contains(teamCategory)) {
            return autoCodeUtil.genSerialCode("SCH_TEAM_CODE", "SC");
        } else if (NexusConstants.TEAM_CATEGORY_AUXILIARY_LIST.contains(teamCategory)) {
            return autoCodeUtil.genSerialCode("SCH_TEAM_CODE", "FZ");
        }
        return null;
    }

    /**
     * 修改班组设置
     *
     * @param schTeam 班组设置
     * @return 结果
     */
    @Transactional
    @Override
    public int updateSchTeam(SchTeam schTeam) {
        schTeam.setUpdateTime(DateUtils.getNowDate());
        schTeamMapper.deleteSchTeamMemberByTeamId(schTeam.getTeamId());
        insertSchTeamMember(schTeam);
        return schTeamMapper.updateSchTeam(schTeam);
    }

    /**
     * 批量删除班组设置
     *
     * @param teamIds 需要删除的班组设置主键
     * @return 结果
     */
    @Transactional
    @Override
    public int deleteSchTeamByTeamIds(Long[] teamIds) {
        schTeamMapper.deleteSchTeamMemberByTeamIds(teamIds);
        return schTeamMapper.deleteSchTeamByTeamIds(teamIds);
    }

    /**
     * 删除班组设置信息
     *
     * @param teamId 班组设置主键
     * @return 结果
     */
    @Transactional
    @Override
    public int deleteSchTeamByTeamId(Long teamId) {
        schTeamMapper.deleteSchTeamMemberByTeamId(teamId);
        return schTeamMapper.deleteSchTeamByTeamId(teamId);
    }

    /**
     * 新增班组成员信息
     *
     * @param schTeam 班组设置对象
     */
    public void insertSchTeamMember(SchTeam schTeam) {
        List<SchTeamMember> schTeamMemberList = schTeam.getSchTeamMemberList();
        Long teamId = schTeam.getTeamId();
        if (StringUtils.isNotNull(schTeamMemberList)) {
            List<SchTeamMember> list = new ArrayList<SchTeamMember>();
            for (SchTeamMember schTeamMember : schTeamMemberList) {
                // 检查该工人是否已在当前班组中存在
                Long workerId = schTeamMember.getWorkerId();
                String workerName = schTeamMember.getWorkerName();
                if (workerId != null) {
                    // 检查该工人是否已在当前班组中存在
                    SchTeamMember existingMember = schTeamMapper.checkTeamMemberExists(teamId, workerId);
                    if (existingMember != null) {
                        // 如果工人已存在，抛出业务异常
                        throw new CustomException("工人: " + workerName + " 已存在于当前班组中，不能重复添加");
                    }

                    // 检查该工人是否被其他班组使用
                    LambdaQueryWrapper<SchTeamMember> usedQueryWrapper = new LambdaQueryWrapper<SchTeamMember>()
                            .eq(SchTeamMember::getWorkerId, workerId)
                            .ne(SchTeamMember::getTeamId, teamId);  // 排除当前班组
                    long usedCount = schTeamMemberMapper.selectCount(usedQueryWrapper);
                    if (usedCount > 0) {
                        throw new CustomException("工人: " + workerName + " 已被其他班组使用，不能重复添加");
                    }
                }
                schTeamMember.setTeamId(teamId);
                list.add(schTeamMember);
            }
            if (list.size() > 0) {
                schTeamMapper.batchSchTeamMember(list);

                // 验证班组长唯一性
                validateOnlyOneActiveLeader(teamId);
            }
        }
    }

    /**
     * 验证班组中是否只有一个启用的班组长
     *
     * @param teamId 班组ID
     */
    private void validateOnlyOneActiveLeader(Long teamId) {
        List<SchTeamMember> activeLeaders = schTeamMemberMapper.selectList(
                new LambdaQueryWrapper<SchTeamMember>()
                        .eq(SchTeamMember::getTeamId, teamId)
                        .eq(SchTeamMember::getRole, "leader")
                        .eq(SchTeamMember::getStatus, "1")
        );

        if (activeLeaders.size() > 1) {
            StringBuilder leaderNames = new StringBuilder();
            for (int i = 0; i < activeLeaders.size(); i++) {
                if (i > 0) {
                    leaderNames.append("、");
                }
                leaderNames.append(activeLeaders.get(i).getWorkerName());
            }
            throw new CustomException("启用的班组长只能为一人，当前有多人：" + leaderNames.toString());
        }
    }
}
