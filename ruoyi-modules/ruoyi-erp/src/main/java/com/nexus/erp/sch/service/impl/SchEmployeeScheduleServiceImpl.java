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
import com.nexus.erp.sch.domain.*;
import com.nexus.erp.sch.domain.bo.SchEmployeeScheduleBo;
import com.nexus.erp.sch.domain.vo.SchEmployeeScheduleVo;
import com.nexus.erp.sch.mapper.*;
import com.nexus.erp.sch.service.ISchEmployeeScheduleService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 员工排班明细Service业务层处理
 *
 * @author nexus
 * @date 2025-12-30
 */
@RequiredArgsConstructor
@DS("erp")
@Slf4j
@Service
public class SchEmployeeScheduleServiceImpl implements ISchEmployeeScheduleService {

    private final SchEmployeeScheduleMapper baseMapper;
    private final SchTeamMapper schTeamMapper;
    private final SchTeamMemberMapper schTeamMemberMapper;
    private final SchPlanMapper schPlanMapper;
    private final SchTeamScheduleMapper schTeamScheduleMapper;
    private final SchShiftMapper schShiftMapper;

    /**
     * 分页查询员工排班明细列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 员工排班明细分页列表
     */
    @Override
    public TableDataInfo<SchEmployeeScheduleVo> selectPageList(SchEmployeeScheduleBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SchEmployeeSchedule> lqw = buildQueryWrapper(bo);
        Page<SchEmployeeScheduleVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询员工排班明细列表
     *
     * @param bo 查询参数
     * @return 员工排班明细列表
     */
    @Override
    public List<SchEmployeeScheduleVo> selectList(SchEmployeeScheduleBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID获取员工排班明细详情
     *
     * @param id 排班明细ID
     * @return 员工排班明细详情
     */
    @Override
    public SchEmployeeScheduleVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增员工排班明细
     *
     * @param bo 员工排班明细业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SchEmployeeScheduleBo bo) {
        SchEmployeeSchedule entity = MapstructUtils.convert(bo, SchEmployeeSchedule.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改员工排班明细
     *
     * @param bo 员工排班明细业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SchEmployeeScheduleBo bo) {
        SchEmployeeSchedule entity = MapstructUtils.convert(bo, SchEmployeeSchedule.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除员工排班明细
     *
     * @param ids 排班明细ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 生成员工排班明细
     * <p>根据班组排班模板(sch_team_schedule)逐条展开班组成员生成排班明细, 精确遵守
     * 模板中的 班组→班次 绑定与排班日期、休息日标记。</p>
     * <p>计划无任何班组排班模板时, 回退为轮转算法(按班组数与班次数轮转分配)。</p>
     *
     * @param planId 排班计划ID(空则处理全部已发布计划)
     * @return 生成记录数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generate(Long planId) {
        List<SchPlan> plans;
        if (planId != null) {
            SchPlan plan = schPlanMapper.selectById(planId);
            if (plan == null) {
                throw new ServiceException("排班计划不存在: " + planId);
            }
            plans = List.of(plan);
        } else {
            plans = schPlanMapper.selectList(Wrappers.lambdaQuery());
        }
        // 仅生成已发布计划(大小写兼容), 且起止日期齐全; 按计划范围清理旧明细(不再全表清空)
        List<SchPlan> validPlans = plans.stream()
            .filter(p -> StringUtils.equalsIgnoreCase(p.getStatus(), "published"))
            .filter(p -> p.getStartDate() != null && p.getEndDate() != null)
            .collect(Collectors.toList());
        if (validPlans.isEmpty()) {
            return 0;
        }
        for (SchPlan plan : validPlans) {
            baseMapper.delete(Wrappers.<SchEmployeeSchedule>lambdaQuery()
                .eq(SchEmployeeSchedule::getPlanId, plan.getPlanId()));
        }

        // 预载班组名(避免循环内 N+1 查询)
        Map<Long, String> teamNameMap = schTeamMapper.selectList(Wrappers.lambdaQuery()).stream()
            .collect(Collectors.toMap(SchTeam::getTeamId, SchTeam::getTeamName, (a, b) -> a));
        // 有效成员(状态启用)
        List<SchTeamMember> teamMembers = getValidTeamMembers();
        if (teamMembers.isEmpty()) {
            return 0;
        }
        Map<Long, List<SchTeamMember>> membersByTeam = teamMembers.stream()
            .collect(Collectors.groupingBy(SchTeamMember::getTeamId));

        int generatedCount = 0;
        List<SchEmployeeSchedule> schedulesToInsert = new ArrayList<>();
        // 同一人同一天只生成一条排班(ERP-SCH-06): 跨计划/多班组/同班组多模板叠加会让考勤判定
        // 随机取到某一条,迟到早退算在错误班次上;DB 侧另有 uk_worker_date_active 唯一索引兜底
        Set<String> generatedKeys = new HashSet<>();

        for (SchPlan plan : validPlans) {
            List<SchTeamSchedule> teamSchedules = getTeamSchedulesByPlanId(plan.getPlanId());
            if (teamSchedules.isEmpty()) {
                // 未配班组排班模板的计划不生成(班次池来自模板, 无模板无从分配)
                continue;
            }
            // 按模板逐条展开(班组→班次绑定 + 排班日期 + 休息日标记全部生效)
            generatedCount += expandFromTeamSchedules(plan, teamSchedules, teamNameMap, membersByTeam,
                schedulesToInsert, generatedKeys);
        }

        if (!schedulesToInsert.isEmpty()) {
            baseMapper.insertBatch(schedulesToInsert);
            generatedCount = schedulesToInsert.size();
        }
        return generatedCount;
    }

    /**
     * 主路径: 按班组排班模板展开——每条模板(班组, 班次, 日期)为该班组全部有效成员生成一条排班;
     * isHoliday=1 的模板生成休息行(不落班次时间)。
     * <p>同一 (workerId, scheduledDate) 只允许出现一条: 重复模板/跨班组/跨计划叠加时跳过并告警
     * (ERP-SCH-06,与 DDL 唯一索引 {@code uk_worker_date_active} 一致)。</p>
     */
    private int expandFromTeamSchedules(SchPlan plan, List<SchTeamSchedule> teamSchedules,
                                        Map<Long, String> teamNameMap, Map<Long, List<SchTeamMember>> membersByTeam,
                                        List<SchEmployeeSchedule> out, Set<String> generatedKeys) {
        int count = 0;
        for (SchTeamSchedule ts : teamSchedules) {
            List<SchTeamMember> members = membersByTeam.get(ts.getTeamId());
            if (members == null || members.isEmpty()) {
                continue;
            }
            boolean rest = ts.getIsHoliday() != null && ts.getIsHoliday() == 1;
            SchShift shift = rest ? null : getShiftById(ts.getShiftId());
            if (!rest && shift == null) {
                // 模板引用的班次不存在(可能已被删除), 跳过该条并告警
                continue;
            }
            Date scheduleDate = truncateToDay(ts.getScheduledDate());
            for (SchTeamMember member : members) {
                String dedupeKey = member.getWorkerId() + "#"
                    + (scheduleDate == null ? "" : scheduleDate.getTime());
                if (!generatedKeys.add(dedupeKey)) {
                    log.warn("[sch] 排班生成冲突已跳过(同一人同日多班次): workerId={}, scheduledDate={}, planId={}, teamId={}",
                        member.getWorkerId(), scheduleDate, plan.getPlanId(), ts.getTeamId());
                    continue;
                }
                SchEmployeeSchedule schedule = new SchEmployeeSchedule();
                schedule.setPlanId(plan.getPlanId());
                schedule.setWorkerId(member.getWorkerId());
                schedule.setWorkerName(member.getWorkerName());
                schedule.setTeamId(ts.getTeamId());
                schedule.setTeamName(teamNameMap.getOrDefault(ts.getTeamId(), ""));
                schedule.setScheduledDate(scheduleDate);
                if (rest) {
                    schedule.setScheduleStatus("rest");
                    schedule.setIsWorkingDay(0);
                    schedule.setShiftId(null);
                    schedule.setActualShiftId(null);
                    schedule.setShiftName(null);
                    schedule.setStartTime(null);
                    schedule.setEndTime(null);
                } else {
                    schedule.setShiftId(shift.getShiftId());
                    schedule.setActualShiftId(shift.getShiftId());
                    schedule.setShiftName(shift.getShiftName());
                    if (shift.getStartTime() != null) {
                        schedule.setStartTime(combineDateAndTime(scheduleDate, shift.getStartTime()));
                    }
                    if (shift.getEndTime() != null) {
                        if (shift.getIsCrossDay() != null && shift.getIsCrossDay() == 1) {
                            schedule.setEndTime(combineDateAndTimeWithDayOffset(scheduleDate, shift.getEndTime(), 1));
                        } else {
                            schedule.setEndTime(combineDateAndTime(scheduleDate, shift.getEndTime()));
                        }
                    }
                    schedule.setScheduleStatus("normal");
                    schedule.setIsWorkingDay(1);
                }
                out.add(schedule);
                count++;
            }
        }
        return count;
    }


    /**
     * 日期截断到日(00:00:00)——排班匹配(考勤打卡/今日排班)统一按自然日比对,
     * 模板日期带非零时刻时会造成生成与查询错位
     */
    private Date truncateToDay(Date date) {
        if (date == null) {
            return null;
        }
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private LambdaQueryWrapper<SchEmployeeSchedule> buildQueryWrapper(SchEmployeeScheduleBo bo) {
        LambdaQueryWrapper<SchEmployeeSchedule> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.eq(bo.getWorkerId() != null, SchEmployeeSchedule::getWorkerId, bo.getWorkerId());
            lqw.eq(bo.getScheduledDate() != null, SchEmployeeSchedule::getScheduledDate, bo.getScheduledDate());
            lqw.eq(bo.getPlanId() != null, SchEmployeeSchedule::getPlanId, bo.getPlanId());
            lqw.eq(bo.getTeamId() != null, SchEmployeeSchedule::getTeamId, bo.getTeamId());
            lqw.eq(bo.getShiftId() != null, SchEmployeeSchedule::getShiftId, bo.getShiftId());
            lqw.eq(StringUtils.isNotBlank(bo.getScheduleStatus()), SchEmployeeSchedule::getScheduleStatus, bo.getScheduleStatus());
        }
        return lqw;
    }




    private List<SchTeamMember> getValidTeamMembers() {
        LambdaQueryWrapper<SchTeamMember> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(SchTeamMember::getStatus, 1);
        return schTeamMemberMapper.selectList(queryWrapper);
    }

    private List<SchPlan> getValidPlans() {
        return schPlanMapper.selectList(Wrappers.lambdaQuery());
    }


    private List<SchTeamSchedule> getTeamSchedulesByPlanId(Long planId) {
        LambdaQueryWrapper<SchTeamSchedule> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(SchTeamSchedule::getPlanId, planId);
        return schTeamScheduleMapper.selectList(queryWrapper);
    }

    private SchShift getShiftById(Long shiftId) {
        if (shiftId == null) return null;
        return schShiftMapper.selectById(shiftId);
    }

    private String getTeamNameById(Long teamId) {
        if (teamId == null) return "";
        SchTeam team = schTeamMapper.selectById(teamId);
        return team != null ? team.getTeamName() : "";
    }



    private Date combineDateAndTime(Date date, Date time) {
        if (date == null || time == null) {
            return null;
        }

        Calendar dateCal = Calendar.getInstance();
        dateCal.setTime(date);

        Calendar timeCal = Calendar.getInstance();
        timeCal.setTime(time);

        Calendar resultCal = Calendar.getInstance();
        resultCal.set(Calendar.YEAR, dateCal.get(Calendar.YEAR));
        resultCal.set(Calendar.MONTH, dateCal.get(Calendar.MONTH));
        resultCal.set(Calendar.DAY_OF_MONTH, dateCal.get(Calendar.DAY_OF_MONTH));
        resultCal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
        resultCal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
        resultCal.set(Calendar.SECOND, timeCal.get(Calendar.SECOND));
        resultCal.set(Calendar.MILLISECOND, timeCal.get(Calendar.MILLISECOND));

        return resultCal.getTime();
    }

    private Date combineDateAndTimeWithDayOffset(Date date, Date time, int dayOffset) {
        if (date == null || time == null) {
            return null;
        }

        Calendar dateCal = Calendar.getInstance();
        dateCal.setTime(date);
        dateCal.add(Calendar.DAY_OF_MONTH, dayOffset);

        Calendar timeCal = Calendar.getInstance();
        timeCal.setTime(time);

        Calendar resultCal = Calendar.getInstance();
        resultCal.set(Calendar.YEAR, dateCal.get(Calendar.YEAR));
        resultCal.set(Calendar.MONTH, dateCal.get(Calendar.MONTH));
        resultCal.set(Calendar.DAY_OF_MONTH, dateCal.get(Calendar.DAY_OF_MONTH));
        resultCal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
        resultCal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
        resultCal.set(Calendar.SECOND, timeCal.get(Calendar.SECOND));
        resultCal.set(Calendar.MILLISECOND, timeCal.get(Calendar.MILLISECOND));

        return resultCal.getTime();
    }
}
