package org.dromara.erp.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.sch.domain.SchPlanTeam;
import org.dromara.erp.sch.domain.bo.SchPlanTeamBo;
import org.dromara.erp.sch.domain.vo.SchPlanTeamVo;
import org.dromara.erp.sch.mapper.SchPlanTeamMapper;
import org.dromara.erp.sch.service.ISchPlanTeamService;
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

    @Override
    public TableDataInfo<SchPlanTeamVo> selectPageList(SchPlanTeamBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SchPlanTeam> lqw = buildQueryWrapper(bo);
        Page<SchPlanTeamVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<SchPlanTeamVo> selectList(SchPlanTeamBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public SchPlanTeamVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SchPlanTeamBo bo) {
        SchPlanTeam entity = MapstructUtils.convert(bo, SchPlanTeam.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SchPlanTeamBo bo) {
        SchPlanTeam entity = MapstructUtils.convert(bo, SchPlanTeam.class);
        return baseMapper.updateById(entity);
    }

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
