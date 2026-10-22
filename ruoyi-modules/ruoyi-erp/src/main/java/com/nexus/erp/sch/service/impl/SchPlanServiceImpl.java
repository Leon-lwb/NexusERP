package com.nexus.erp.sch.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.SchAttendance;
import com.nexus.erp.sch.domain.SchEmployeeSchedule;
import com.nexus.erp.sch.domain.SchPlan;
import com.nexus.erp.sch.domain.SchPlanTeam;
import com.nexus.erp.sch.domain.SchShift;
import com.nexus.erp.sch.domain.SchTeamSchedule;
import com.nexus.erp.sch.domain.bo.SchPlanBo;
import com.nexus.erp.sch.domain.vo.SchPlanVo;
import com.nexus.erp.sch.mapper.SchAttendanceMapper;
import com.nexus.erp.sch.mapper.SchEmployeeScheduleMapper;
import com.nexus.erp.sch.mapper.SchPlanMapper;
import com.nexus.erp.sch.mapper.SchPlanTeamMapper;
import com.nexus.erp.sch.mapper.SchShiftMapper;
import com.nexus.erp.sch.mapper.SchTeamScheduleMapper;
import com.nexus.erp.sch.service.ISchPlanService;
import com.nexus.system.strategy.AutoCodeUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

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
    private final SchAttendanceMapper schAttendanceMapper;
    private final AutoCodeUtil autoCodeUtil;

    /** 排班计划状态机(ERP-SCH-12): draft 草稿 → published 已发布 → closed 已关闭 */
    private static final Set<String> ALLOWED_STATUS = Set.of("draft", "published", "closed");

    /** 周期类型字典 sch_plan_period_type */
    private static final Set<String> ALLOWED_PERIOD_TYPES =
            Set.of("week", "month", "quarter", "year");

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
            throw new ServiceException("计划编码已存在: " + entity.getPlanCode());
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
     * 字典字段统一小写入库并做枚举校验(ERP-SCH-12):
     * sch_plan_status(draft/published/closed)与 sch_plan_period_type(week/month/quarter/year)
     * 均为小写字典值。原实现只做小写归一化、不校验，任意字符串都能落库，导致前端字典标签失配、
     * {@code generate()} 的 published 过滤失效。现在非法值直接抛业务异常。
     */
    private void normalizeDictValues(SchPlan entity) {
        if (StringUtils.isNotBlank(entity.getStatus())) {
            entity.setStatus(entity.getStatus().toLowerCase());
            if (!ALLOWED_STATUS.contains(entity.getStatus())) {
                throw new ServiceException("排班计划状态非法：" + entity.getStatus()
                    + "（仅支持 draft/published/closed）");
            }
        }
        if (StringUtils.isNotBlank(entity.getPeriodType())) {
            entity.setPeriodType(entity.getPeriodType().toLowerCase());
            if (!ALLOWED_PERIOD_TYPES.contains(entity.getPeriodType())) {
                throw new ServiceException("排班周期类型非法：" + entity.getPeriodType()
                    + "（仅支持 week/month/quarter/year）");
            }
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
        // ERP-SCH-13: 排班明细一旦被考勤引用, 级联删除会让考勤 schedule_id 悬空、历史班次不可复现。
        // 删除前先拦截"已有考勤引用"的计划, 交由人工先处理考勤(或改为 closed 关闭而非删除)。
        List<Long> scheduleIds =
                schEmployeeScheduleMapper
                        .selectList(
                                Wrappers.<SchEmployeeSchedule>lambdaQuery()
                                        .in(SchEmployeeSchedule::getPlanId, idList))
                        .stream()
                        .map(SchEmployeeSchedule::getScheduleId)
                        .filter(Objects::nonNull)
                        .toList();
        if (!scheduleIds.isEmpty()) {
            Long referenced = schAttendanceMapper.selectCount(
                Wrappers.<SchAttendance>lambdaQuery().in(SchAttendance::getScheduleId, scheduleIds));
            if (referenced != null && referenced > 0) {
                throw new ServiceException("排班计划下已有 " + referenced
                    + " 条考勤记录引用了排班明细，不能删除（请先处理考勤，或改为关闭计划）");
            }
        }
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
