package com.nexus.erp.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.SchEmployeeSchedule;
import com.nexus.erp.sch.domain.SchPlan;
import com.nexus.erp.sch.domain.SchPlanTeam;
import com.nexus.erp.sch.domain.SchShift;
import com.nexus.erp.sch.domain.SchTeamSchedule;
import com.nexus.erp.sch.domain.bo.SchPlanBo;
import com.nexus.erp.sch.domain.vo.SchPlanVo;
import com.nexus.erp.sch.mapper.SchEmployeeScheduleMapper;
import com.nexus.erp.sch.mapper.SchPlanMapper;
import com.nexus.erp.sch.mapper.SchPlanTeamMapper;
import com.nexus.erp.sch.mapper.SchShiftMapper;
import com.nexus.erp.sch.mapper.SchTeamScheduleMapper;
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
    private final SchShiftMapper schShiftMapper;
    private final SchTeamScheduleMapper schTeamScheduleMapper;
    private final SchEmployeeScheduleMapper schEmployeeScheduleMapper;
    private final SchPlanTeamMapper schPlanTeamMapper;
    private final com.nexus.system.strategy.AutoCodeUtil autoCodeUtil;

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
        // 编码留空按 SCH_PLAN_CODE 规则生成; 传入则唯一性预检(原实现撞 uk_plan_code 裸抛500)
        if (StringUtils.isBlank(entity.getPlanCode())) {
            entity.setPlanCode(autoCodeUtil.genSerialCode("SCH_PLAN_CODE", null));
        }
        Long exist = baseMapper.selectCount(Wrappers.<SchPlan>lambdaQuery()
            .eq(SchPlan::getPlanCode, entity.getPlanCode()));
        if (exist != null && exist > 0) {
            throw new com.nexus.common.core.exception.ServiceException("计划编码已存在: " + entity.getPlanCode());
        }
        normalizeDictValues(entity);
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
        normalizeDictValues(entity);
        return baseMapper.updateById(entity);
    }

    /**
     * 字典字段统一小写入库: sch_plan_status(draft/published/closed)与
     * sch_plan_period_type(week/month/quarter/year)均为小写字典值,
     * 传入大写会造成前端字典标签与按钮状态判断失配
     */
    private void normalizeDictValues(SchPlan entity) {
        if (StringUtils.isNotBlank(entity.getStatus())) {
            entity.setStatus(entity.getStatus().toLowerCase());
        }
        if (StringUtils.isNotBlank(entity.getPeriodType())) {
            entity.setPeriodType(entity.getPeriodType().toLowerCase());
        }
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
        // 级联清理关联数据(原实现只删主表, 班次/排班模板/员工排班/计划班组全部悬挂)
        List<Long> idList = List.of(ids);
        schShiftMapper.delete(Wrappers.<SchShift>lambdaQuery().in(SchShift::getPlanId, idList));
        schTeamScheduleMapper.delete(Wrappers.<SchTeamSchedule>lambdaQuery().in(SchTeamSchedule::getPlanId, idList));
        schEmployeeScheduleMapper.delete(Wrappers.<SchEmployeeSchedule>lambdaQuery().in(SchEmployeeSchedule::getPlanId, idList));
        schPlanTeamMapper.delete(Wrappers.<SchPlanTeam>lambdaQuery().in(SchPlanTeam::getPlanId, idList));
        return baseMapper.deleteBatchIds(idList);
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
