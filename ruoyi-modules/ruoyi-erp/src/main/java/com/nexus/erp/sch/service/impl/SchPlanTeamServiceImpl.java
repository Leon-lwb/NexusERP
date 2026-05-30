package com.nexus.erp.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.SchPlanTeam;
import com.nexus.erp.sch.domain.bo.SchPlanTeamBo;
import com.nexus.erp.sch.domain.vo.SchPlanTeamVo;
import com.nexus.erp.sch.mapper.SchPlanTeamMapper;
import com.nexus.erp.sch.service.ISchPlanTeamService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 计划班组关联Service业务层处理
 *
 * @author nexus
 * @date 2025-12-26
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class SchPlanTeamServiceImpl implements ISchPlanTeamService {

    private final SchPlanTeamMapper baseMapper;

    /**
     * 分页查询计划班组关联列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 计划班组关联分页列表
     */
    @Override
    public TableDataInfo<SchPlanTeamVo> selectPageList(SchPlanTeamBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SchPlanTeam> lqw = buildQueryWrapper(bo);
        Page<SchPlanTeamVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询计划班组关联列表
     *
     * @param bo 查询参数
     * @return 计划班组关联列表
     */
    @Override
    public List<SchPlanTeamVo> selectList(SchPlanTeamBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID获取计划班组关联详情
     *
     * @param id 关联记录ID
     * @return 计划班组关联详情
     */
    @Override
    public SchPlanTeamVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增计划班组关联
     *
     * @param bo 计划班组关联业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SchPlanTeamBo bo) {
        SchPlanTeam entity = MapstructUtils.convert(bo, SchPlanTeam.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改计划班组关联
     *
     * @param bo 计划班组关联业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SchPlanTeamBo bo) {
        SchPlanTeam entity = MapstructUtils.convert(bo, SchPlanTeam.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除计划班组关联
     *
     * @param ids 关联记录ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<SchPlanTeam> buildQueryWrapper(SchPlanTeamBo bo) {
        LambdaQueryWrapper<SchPlanTeam> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.eq(bo.getPlanId() != null, SchPlanTeam::getPlanId, bo.getPlanId());
            lqw.eq(bo.getTeamId() != null, SchPlanTeam::getTeamId, bo.getTeamId());
        }
        return lqw;
    }
}
