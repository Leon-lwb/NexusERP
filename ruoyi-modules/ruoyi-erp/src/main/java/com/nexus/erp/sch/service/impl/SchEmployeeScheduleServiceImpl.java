package com.nexus.erp.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.*;
import com.nexus.erp.sch.domain.bo.SchEmployeeScheduleBo;
import com.nexus.erp.sch.domain.vo.SchEmployeeScheduleVo;
import com.nexus.erp.sch.mapper.*;
import com.nexus.erp.sch.service.ISchEmployeeScheduleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
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
@Service
public class SchEmployeeScheduleServiceImpl implements ISchEmployeeScheduleService {

    private final SchEmployeeScheduleMapper baseMapper;
    private final SchTeamMapper schTeamMapper;
    private final SchTeamMemberMapper schTeamMemberMapper;
    private final SchPlanMapper schPlanMapper;
    private final SchPlanTeamMapper schPlanTeamMapper;
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
     * <p>根据排班计划、班组排班模板及班组成员自动生成所有员工的排班明细记录。</p>
     * <p>涉及数据关联：</p>
     * <ul>
     *   <li>sch_plan_team：关联计划和班组，确定哪些班组参与计划</li>
     *   <li>sch_team_schedule：关联班组和班次，确定班组上哪个班次</li>
     *   <li>sch_shift：提供班次的开始和结束时间</li>
     *   <li>sch_plan：提供排班计划的开始和结束日期</li>
     * </ul>
     *
     * @return 生成记录数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generate() {
        // 清空现有的员工排班明细
        baseMapper.delete(Wrappers.lambdaQuery());

        // 获取所有有效的班组成员（状态为1的员工）
        List<SchTeamMember> teamMembers = getValidTeamMembers();

        if (teamMembers.isEmpty()) {
            return 0;
        }

        // 获取排班计划
        List<SchPlan> plans = getValidPlans();
        if (plans.isEmpty()) {
            return 0;
        }

        int generatedCount = 0;
        List<SchEmployeeSchedule> schedulesToInsert = new ArrayList<>();

        for (SchPlan plan : plans) {
            List<SchPlanTeam> planTeams = getPlanTeamsByPlanId(plan.getPlanId());
            if (planTeams.isEmpty()) {
                continue;
            }

            List<SchTeamSchedule> allTeamSchedules = getTeamSchedulesByPlanId(plan.getPlanId());

            Map<Long, List<SchTeamSchedule>> shiftTeamsMap = allTeamSchedules.stream()
                    .collect(Collectors.groupingBy(SchTeamSchedule::getShiftId));

            List<Long> shiftIds = new ArrayList<>(shiftTeamsMap.keySet());
            if (shiftIds.isEmpty()) {
                continue;
            }

            List<Long> teamIds = planTeams.stream()
                    .map(SchPlanTeam::getTeamId)
                    .collect(Collectors.toList());

            List<Date> scheduleDates = generateScheduleDates(plan.getStartDate(), plan.getEndDate(), plan.getPeriodType());

            Map<Integer, List<Date>> periodDatesMap = groupDatesByPeriod(scheduleDates, plan.getStartDate(), plan.getPeriodType());

            for (Map.Entry<Integer, List<Date>> periodEntry : periodDatesMap.entrySet()) {
                int periodIndex = periodEntry.getKey();
                List<Date> periodDates = periodEntry.getValue();

                Map<Long, Long> teamShiftAssignment = calculateShiftAssignment(teamIds, shiftIds, periodIndex);

                for (Date scheduleDate : periodDates) {
                    boolean isHoliday = isHoliday(scheduleDate, periodDates);

                    if (isHoliday) {
                        for (SchPlanTeam planTeam : planTeams) {
                            Long teamId = planTeam.getTeamId();

                            List<SchTeamMember> membersInTeam = teamMembers.stream()
                                    .filter(member -> member.getTeamId().equals(teamId))
                                    .collect(Collectors.toList());

                            for (SchTeamMember member : membersInTeam) {
                                SchEmployeeSchedule schedule = new SchEmployeeSchedule();
                                schedule.setPlanId(plan.getPlanId());
                                schedule.setWorkerId(member.getWorkerId());
                                schedule.setWorkerName(member.getWorkerName());
                                schedule.setTeamId(member.getTeamId());
                                schedule.setTeamName(getTeamNameById(member.getTeamId()));
                                schedule.setScheduledDate(scheduleDate);
                                schedule.setScheduleStatus("rest");
                                schedule.setIsWorkingDay(0);
                                schedule.setShiftId(-1L);
                                schedule.setShiftName(null);
                                schedule.setStartTime(null);
                                schedule.setEndTime(null);

                                schedulesToInsert.add(schedule);
                            }
                        }
                    } else {
                        for (Map.Entry<Long, Long> assignment : teamShiftAssignment.entrySet()) {
                            Long teamId = assignment.getKey();
                            Long shiftId = assignment.getValue();

                            SchShift shift = getShiftById(shiftId);
                            if (shift == null) {
                                continue;
                            }

                            List<SchTeamMember> membersInTeam = teamMembers.stream()
                                    .filter(member -> member.getTeamId().equals(teamId))
                                    .collect(Collectors.toList());

                            for (SchTeamMember member : membersInTeam) {
                                SchEmployeeSchedule schedule = new SchEmployeeSchedule();
                                schedule.setPlanId(plan.getPlanId());
                                schedule.setWorkerId(member.getWorkerId());
                                schedule.setWorkerName(member.getWorkerName());
                                schedule.setTeamId(member.getTeamId());
                                schedule.setTeamName(getTeamNameById(member.getTeamId()));
                                schedule.setShiftId(shift.getShiftId());
                                schedule.setActualShiftId(shiftId);
                                schedule.setShiftName(shift.getShiftName());
                                schedule.setScheduledDate(scheduleDate);

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

                                schedulesToInsert.add(schedule);
                            }
                        }
                    }
                }
            }
        }

        for (SchEmployeeSchedule schedule : schedulesToInsert) {
            int result = baseMapper.insert(schedule);
            if (result > 0) {
                generatedCount++;
            }
        }

        return generatedCount;
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

    private Map<Long, Long> calculateShiftAssignment(List<Long> teamIds, List<Long> shiftIds, int periodIndex) {
        Map<Long, Long> assignment = new HashMap<>();

        if (teamIds.isEmpty() || shiftIds.isEmpty()) {
            return assignment;
        }

        int teamCount = teamIds.size();
        int shiftCount = shiftIds.size();

        for (int i = 0; i < Math.min(teamCount, shiftCount); i++) {
            Long teamId = teamIds.get(i);
            int shiftIndex = (i + periodIndex) % shiftCount;
            Long shiftId = shiftIds.get(shiftIndex);
            assignment.put(teamId, shiftId);
        }

        for (int i = shiftCount; i < teamCount; i++) {
            Long teamId = teamIds.get(i);
            int shiftIndex = (i + periodIndex) % shiftCount;
            Long shiftId = shiftIds.get(shiftIndex);
            assignment.put(teamId, shiftId);
        }

        return assignment;
    }

    private Map<Integer, List<Date>> groupDatesByPeriod(List<Date> dates, Date startDate, String periodType) {
        Map<Integer, List<Date>> periodMap = new HashMap<>();

        for (Date date : dates) {
            int periodIndex = calculatePeriodIndex(date, startDate, periodType);
            periodMap.computeIfAbsent(periodIndex, k -> new ArrayList<>()).add(date);
        }

        return periodMap;
    }

    private int calculatePeriodIndex(Date date, Date startDate, String periodType) {
        Calendar dateCal = Calendar.getInstance();
        dateCal.setTime(date);
        Calendar startCal = Calendar.getInstance();
        startCal.setTime(startDate);

        if ("week".equals(periodType)) {
            long diffInDays = (dateCal.getTimeInMillis() - startCal.getTimeInMillis()) / (24 * 60 * 60 * 1000);
            return (int) (diffInDays / 7);
        } else if ("month".equals(periodType)) {
            int yearDiff = dateCal.get(Calendar.YEAR) - startCal.get(Calendar.YEAR);
            int monthDiff = dateCal.get(Calendar.MONTH) - startCal.get(Calendar.MONTH);
            return yearDiff * 12 + monthDiff;
        } else if ("quarter".equals(periodType)) {
            int yearDiff = dateCal.get(Calendar.YEAR) - startCal.get(Calendar.YEAR);
            int quarterDiff = (dateCal.get(Calendar.MONTH) / 3) - (startCal.get(Calendar.MONTH) / 3);
            return yearDiff * 4 + quarterDiff;
        } else {
            long diffInDays = (dateCal.getTimeInMillis() - startCal.getTimeInMillis()) / (24 * 60 * 60 * 1000);
            return (int) (diffInDays / 7);
        }
    }

    private List<SchTeamMember> getValidTeamMembers() {
        LambdaQueryWrapper<SchTeamMember> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(SchTeamMember::getStatus, 1);
        return schTeamMemberMapper.selectList(queryWrapper);
    }

    private List<SchPlan> getValidPlans() {
        return schPlanMapper.selectList(Wrappers.lambdaQuery());
    }

    private List<SchPlanTeam> getPlanTeamsByPlanId(Long planId) {
        LambdaQueryWrapper<SchPlanTeam> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(SchPlanTeam::getPlanId, planId);
        return schPlanTeamMapper.selectList(queryWrapper);
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

    private List<Date> generateScheduleDates(Date startDate, Date endDate, String periodType) {
        List<Date> dates = new ArrayList<>();
        Calendar startCal = Calendar.getInstance();
        startCal.setTime(startDate);
        Calendar endCal = Calendar.getInstance();
        endCal.setTime(endDate);
        endCal.add(Calendar.DAY_OF_MONTH, 1);

        Calendar currentCal = (Calendar) startCal.clone();
        while (currentCal.before(endCal)) {
            dates.add(currentCal.getTime());
            currentCal.add(Calendar.DAY_OF_MONTH, 1);
        }

        return dates;
    }

    private boolean isHoliday(Date date, List<Date> periodDates) {
        if (periodDates.isEmpty()) {
            return false;
        }

        Date lastDate = periodDates.get(periodDates.size() - 1);

        Calendar dateCal = Calendar.getInstance();
        dateCal.setTime(date);
        Calendar lastDateCal = Calendar.getInstance();
        lastDateCal.setTime(lastDate);

        return dateCal.get(Calendar.YEAR) == lastDateCal.get(Calendar.YEAR) &&
                dateCal.get(Calendar.MONTH) == lastDateCal.get(Calendar.MONTH) &&
                dateCal.get(Calendar.DAY_OF_MONTH) == lastDateCal.get(Calendar.DAY_OF_MONTH);
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
