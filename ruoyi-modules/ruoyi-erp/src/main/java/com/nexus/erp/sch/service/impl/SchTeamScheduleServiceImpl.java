package com.nexus.erp.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.SchTeamSchedule;
import com.nexus.erp.sch.domain.bo.SchTeamScheduleBo;
import com.nexus.erp.sch.domain.vo.SchTeamScheduleVo;
import com.nexus.erp.sch.mapper.SchTeamScheduleMapper;
import com.nexus.erp.sch.service.ISchTeamScheduleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 班组排班（模板）Service业务层处理
 *
 * @author nexus
 * @date 2025-12-30
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class SchTeamScheduleServiceImpl implements ISchTeamScheduleService {

    private final SchTeamScheduleMapper baseMapper;

    @Override
    public TableDataInfo<SchTeamScheduleVo> selectPageList(SchTeamScheduleBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SchTeamSchedule> lqw = buildQueryWrapper(bo);
        Page<SchTeamScheduleVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<SchTeamScheduleVo> selectList(SchTeamScheduleBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public SchTeamScheduleVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SchTeamScheduleBo bo) {
        SchTeamSchedule entity = MapstructUtils.convert(bo, SchTeamSchedule.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SchTeamScheduleBo bo) {
        SchTeamSchedule entity = MapstructUtils.convert(bo, SchTeamSchedule.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<SchTeamSchedule> buildQueryWrapper(SchTeamScheduleBo bo) {
        LambdaQueryWrapper<SchTeamSchedule> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.eq(bo.getPlanId() != null, SchTeamSchedule::getPlanId, bo.getPlanId());
            lqw.eq(bo.getTeamId() != null, SchTeamSchedule::getTeamId, bo.getTeamId());
            lqw.eq(bo.getShiftId() != null, SchTeamSchedule::getShiftId, bo.getShiftId());
        }
        return lqw;
    }
}
