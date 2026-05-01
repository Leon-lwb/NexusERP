package org.dromara.erp.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.sch.domain.SchPlan;
import org.dromara.erp.sch.domain.bo.SchPlanBo;
import org.dromara.erp.sch.domain.vo.SchPlanVo;
import org.dromara.erp.sch.mapper.SchPlanMapper;
import org.dromara.erp.sch.service.ISchPlanService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.util.List;

/**
 * 排班计划Service业务层处理
 *
 * @author nexus
 * @date 2025-12-25
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class SchPlanServiceImpl implements ISchPlanService {

    private final SchPlanMapper baseMapper;

    @Override
    public TableDataInfo<SchPlanVo> selectPageList(SchPlanBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SchPlan> lqw = buildQueryWrapper(bo);
        Page<SchPlanVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<SchPlanVo> selectList(SchPlanBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public SchPlanVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SchPlanBo bo) {
        SchPlan entity = MapstructUtils.convert(bo, SchPlan.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SchPlanBo bo) {
        SchPlan entity = MapstructUtils.convert(bo, SchPlan.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    private LambdaQueryWrapper<SchPlan> buildQueryWrapper(SchPlanBo bo) {
        LambdaQueryWrapper<SchPlan> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.like(StringUtils.isNotBlank(bo.getPlanCode()), SchPlan::getPlanCode, bo.getPlanCode());
            lqw.like(StringUtils.isNotBlank(bo.getPlanName()), SchPlan::getPlanName, bo.getPlanName());
            lqw.eq(StringUtils.isNotBlank(bo.getStatus()), SchPlan::getStatus, bo.getStatus());
            lqw.eq(bo.getPeriodType() != null, SchPlan::getPeriodType, bo.getPeriodType());
        }
        return lqw;
    }
}
