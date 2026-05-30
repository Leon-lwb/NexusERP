package com.nexus.erp.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.SchPlan;
import com.nexus.erp.sch.domain.bo.SchPlanBo;
import com.nexus.erp.sch.domain.vo.SchPlanVo;
import com.nexus.erp.sch.mapper.SchPlanMapper;
import com.nexus.erp.sch.service.ISchPlanService;
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

    /**
     * 分页查询排班计划列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 排班计划分页列表
     */
    @Override
    public TableDataInfo<SchPlanVo> selectPageList(SchPlanBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SchPlan> lqw = buildQueryWrapper(bo);
        Page<SchPlanVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询排班计划列表
     *
     * @param bo 查询参数
     * @return 排班计划列表
     */
    @Override
    public List<SchPlanVo> selectList(SchPlanBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID获取排班计划详情
     *
     * @param id 排班计划ID
     * @return 排班计划详情
     */
    @Override
    public SchPlanVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增排班计划
     *
     * @param bo 排班计划业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SchPlanBo bo) {
        SchPlan entity = MapstructUtils.convert(bo, SchPlan.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改排班计划
     *
     * @param bo 排班计划业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SchPlanBo bo) {
        SchPlan entity = MapstructUtils.convert(bo, SchPlan.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除排班计划
     *
     * @param ids 排班计划ID数组
     * @return 影响行数
     */
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
