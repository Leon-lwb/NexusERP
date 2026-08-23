package com.nexus.erp.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.SchTeam;
import com.nexus.erp.sch.domain.SchTeamMember;
import com.nexus.erp.sch.domain.bo.SchTeamMemberBo;
import com.nexus.erp.sch.domain.vo.SchTeamMemberVo;
import com.nexus.erp.sch.mapper.SchTeamMapper;
import com.nexus.erp.sch.mapper.SchTeamMemberMapper;
import com.nexus.erp.sch.service.ISchTeamMemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 班组成员Service业务层处理
 *
 * @author nexus
 * @date 2025-12-24
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class SchTeamMemberServiceImpl implements ISchTeamMemberService {

    private final SchTeamMemberMapper baseMapper;
    private final SchTeamMapper schTeamMapper;

    /**
     * 分页查询班组成员列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 班组成员分页列表
     */
    @Override
    public TableDataInfo<SchTeamMemberVo> selectPageList(SchTeamMemberBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SchTeamMember> lqw = buildQueryWrapper(bo);
        Page<SchTeamMemberVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询班组成员列表
     *
     * @param bo 查询参数
     * @return 班组成员列表
     */
    @Override
    public List<SchTeamMemberVo> selectList(SchTeamMemberBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID获取班组成员详情
     *
     * @param id 成员ID
     * @return 班组成员详情
     */
    @Override
    public SchTeamMemberVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增班组成员
     *
     * @param bo 班组成员业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SchTeamMemberBo bo) {
        SchTeamMember entity = MapstructUtils.convert(bo, SchTeamMember.class);
        int rows = baseMapper.insert(entity);
        if (rows > 0 && entity.getTeamId() != null) {
            syncMemberCount(entity.getTeamId());
        }
        return rows;
    }

    /**
     * 修改班组成员
     *
     * @param bo 班组成员业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SchTeamMemberBo bo) {
        SchTeamMember entity = MapstructUtils.convert(bo, SchTeamMember.class);
        int rows = baseMapper.updateById(entity);
        if (rows > 0 && entity.getTeamId() != null) {
            syncMemberCount(entity.getTeamId());
        }
        return rows;
    }

    /**
     * 批量删除班组成员
     *
     * @param ids 成员ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        // 先取所属班组(删除后无法回查), 删除后同步各班组 memberCount
        List<SchTeamMember> members = baseMapper.selectByIds(List.of(ids));
        int rows = baseMapper.deleteBatchIds(List.of(ids));
        members.stream().map(SchTeamMember::getTeamId).filter(java.util.Objects::nonNull)
            .distinct().forEach(this::syncMemberCount);
        return rows;
    }

    /**
     * 按成员实际行数回写班组 memberCount(与 SchTeamServiceImpl.saveTeamMembers 口径统一)
     */
    private void syncMemberCount(Long teamId) {
        Long count = baseMapper.selectCount(Wrappers.<SchTeamMember>lambdaQuery()
            .eq(SchTeamMember::getTeamId, teamId));
        schTeamMapper.update(null, Wrappers.<SchTeam>lambdaUpdate()
            .set(SchTeam::getMemberCount, count == null ? 0L : count)
            .eq(SchTeam::getTeamId, teamId));
    }

    private LambdaQueryWrapper<SchTeamMember> buildQueryWrapper(SchTeamMemberBo bo) {
        LambdaQueryWrapper<SchTeamMember> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.eq(bo.getTeamId() != null, SchTeamMember::getTeamId, bo.getTeamId());
            lqw.eq(bo.getWorkerId() != null, SchTeamMember::getWorkerId, bo.getWorkerId());
            lqw.eq(bo.getStatus() != null, SchTeamMember::getStatus, bo.getStatus());
        }
        return lqw;
    }
}
