package com.nexus.core.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.sch.mapper.SchEmployeeScheduleMapper;
import com.nexus.core.sch.domain.SchEmployeeSchedule;
import com.nexus.core.sch.service.ISchEmployeeScheduleService;
import com.nexus.core.sch.mapper.SchTeamMemberMapper;
import com.nexus.core.sch.domain.SchTeamMember;
import com.nexus.core.sch.mapper.SchPlanMapper;
import com.nexus.core.sch.domain.SchPlan;
import com.nexus.core.sch.mapper.SchPlanTeamMapper;
import com.nexus.core.sch.domain.SchPlanTeam;
import com.nexus.core.sch.mapper.SchShiftMapper;
import com.nexus.core.sch.domain.SchShift;
import com.nexus.core.sch.mapper.SchTeamMapper;
import com.nexus.core.sch.domain.SchTeam;
import com.nexus.core.sch.mapper.SchTeamScheduleMapper;
import com.nexus.core.sch.domain.SchTeamSchedule;

/**
 * 员工排班明细Service业务层处理
 *
 * @author nexus
 * @date 2025-12-30
 */
@Service
public class SchEmployeeScheduleServiceImpl extends ServiceImpl<SchEmployeeScheduleMapper, SchEmployeeSchedule> implements ISchEmployeeScheduleService {

    @Autowired
    private SchEmployeeScheduleMapper schEmployeeScheduleMapper;

    @Autowired
    private SchTeamMapper schTeamMapper;

    @Autowired
    private SchTeamMemberMapper schTeamMemberMapper;

    @Autowired
    private SchPlanMapper schPlanMapper;

    @Autowired
    private SchPlanTeamMapper schPlanTeamMapper;

    @Autowired
    private SchTeamScheduleMapper schTeamScheduleMapper;

    @Autowired
    private SchShiftMapper schShiftMapper;

    /**
     * 查询员工排班明细
     *
     * @param scheduleId 员工排班明细主键
     * @return 员工排班明细
     */
    @Override
    public SchEmployeeSchedule selectSchEmployeeScheduleByScheduleId(Long scheduleId) {
        return schEmployeeScheduleMapper.selectSchEmployeeScheduleByScheduleId(scheduleId);
    }

    /**
     * 查询员工排班明细列表
     *
     * @param schEmployeeSchedule 员工排班明细
     * @return 员工排班明细
     */
    @Override
    public List<SchEmployeeSchedule> selectSchEmployeeScheduleList(SchEmployeeSchedule schEmployeeSchedule) {
        return schEmployeeScheduleMapper.selectSchEmployeeScheduleList(schEmployeeSchedule);
    }

    /**
     * 新增员工排班明细
     *
     * @param schEmployeeSchedule 员工排班明细
     * @return 结果
     */
    @Override
    public int insertSchEmployeeSchedule(SchEmployeeSchedule schEmployeeSchedule) {
        schEmployeeSchedule.setCreateTime(DateUtils.getNowDate());
        return schEmployeeScheduleMapper.insertSchEmployeeSchedule(schEmployeeSchedule);
    }

    /**
     * 修改员工排班明细
     *
     * @param schEmployeeSchedule 员工排班明细
     * @return 结果
     */
    @Override
    public int updateSchEmployeeSchedule(SchEmployeeSchedule schEmployeeSchedule) {
        schEmployeeSchedule.setUpdateTime(DateUtils.getNowDate());
        return schEmployeeScheduleMapper.updateSchEmployeeSchedule(schEmployeeSchedule);
    }

    /**
     * 批量删除员工排班明细
     *
     * @param scheduleIds 需要删除的员工排班明细主键
     * @return 结果
     */
    @Override
    public int deleteSchEmployeeScheduleByScheduleIds(Long[] scheduleIds) {
        return schEmployeeScheduleMapper.deleteSchEmployeeScheduleByScheduleIds(scheduleIds);
    }

    /**
     * 删除员工排班明细信息
     *
     * @param scheduleId 员工排班明细主键
     * @return 结果
     */
    @Override
    public int deleteSchEmployeeScheduleByScheduleId(Long scheduleId) {
        return schEmployeeScheduleMapper.deleteSchEmployeeScheduleByScheduleId(scheduleId);
    }

    /**
     * 生成员工排班明细 sch_employee_schedule ts 为每个员工、每个排班日期生成一条
     * sch_plan_team 关联计划和班组，确定哪些班组参与计划
     * sch_team_schedule 关联班组和班次，确定班组上哪个班次
     * sch_shift 提供班次的开始和结束时间
     * sch_plan 提供排班计划的开始和结束日期
     */
    @Override
    public int generate() {
        // 清空现有的员工排班明细
        schEmployeeScheduleMapper.delete(null);
        
        // 获取所有有效的班组成员（状态为1的员工）
        List<SchTeamMember> teamMembers = getValidTeamMembers();
        
        if (teamMembers.isEmpty()) {
            return 0; // 没有有效班组成员，直接返回
        }
        
        // 获取排班计划
        List<SchPlan> plans = getValidPlans();
        if (plans.isEmpty()) {
            return 0; // 没有有效排班计划，直接返回
        }

        // 遍历每个排班计划
        int generatedCount = 0;
        List<SchEmployeeSchedule> schedulesToInsert = new ArrayList<>();
        
        for (SchPlan plan : plans) {
            // 获取该计划下参与的班组
            List<SchPlanTeam> planTeams = getPlanTeamsByPlanId(plan.getPlanId());
            if (planTeams.isEmpty()) {
                continue; // 如果该计划下没有参与的班组，跳过
            }

            // 获取该计划下的班组排班配置（班组与班次的关联）
            List<SchTeamSchedule> teamSchedules = getTeamSchedulesByPlanId(plan.getPlanId());
            
            // 根据计划的开始日期、结束日期和周期类型生成排班日期
            List<Date> scheduleDates = generateScheduleDates(plan.getStartDate(), plan.getEndDate(), plan.getPeriodType());

            // 为每个排班日期生成员工排班明细
            for (Date scheduleDate : scheduleDates) {
                // 检查是否为周期的最后一天（休假日）
                boolean isHoliday = isPeriodLastDay(scheduleDate, plan.getStartDate(), plan.getPeriodType());
                
                if (isHoliday) {
                    // 为所有成员生成休假记录
                    for (SchTeamMember member : teamMembers) {
                        SchEmployeeSchedule schedule = new SchEmployeeSchedule();
                        
                        schedule.setPlanId(plan.getPlanId());
                        schedule.setWorkerId(member.getWorkerId());
                        schedule.setWorkerName(member.getWorkerName());
                        schedule.setTeamId(member.getTeamId());
                        schedule.setTeamName(getTeamNameById(member.getTeamId()));
                        schedule.setScheduledDate(scheduleDate);
                        schedule.setScheduleStatus("rest"); // rest-休假
                        schedule.setIsWorkingDay(0); // 休假不是工作日
                        // 设置班次相关字段为null，避免数据库约束错误
                        schedule.setShiftId(-1L);
                        schedule.setShiftName(null);
                        schedule.setStartTime(null);
                        schedule.setEndTime(null);
                        
                        schedulesToInsert.add(schedule);
                    }
                } else {
                    // 为每个参与计划的班组生成排班记录
                    for (SchPlanTeam planTeam : planTeams) {
                        // 获取该班组的排班配置（班组上哪个班次）
                        List<SchTeamSchedule> teamSchedulesForTeam = teamSchedules.stream()
                            .filter(ts -> ts.getTeamId().equals(planTeam.getTeamId()))
                            .collect(Collectors.toList());
                        
                        // 获取该班组的所有成员
                        List<SchTeamMember> membersInTeam = teamMembers.stream()
                            .filter(member -> member.getTeamId().equals(planTeam.getTeamId()))
                            .collect(Collectors.toList());
                        
                        // 为该班组的每个成员生成排班记录
                        for (SchTeamMember member : membersInTeam) {
                            // 根据班组排班配置为该成员生成对应的班次记录
                            for (SchTeamSchedule teamSchedule : teamSchedulesForTeam) {
                                // 获取班次信息
                                SchShift shift = getShiftById(teamSchedule.getShiftId());
                                if (shift != null) {
                                    SchEmployeeSchedule schedule = new SchEmployeeSchedule();
                                    
                                    schedule.setPlanId(plan.getPlanId());
                                    schedule.setWorkerId(member.getWorkerId());
                                    schedule.setWorkerName(member.getWorkerName());
                                    schedule.setTeamId(member.getTeamId());
                                    schedule.setTeamName(getTeamNameById(member.getTeamId()));
                                    schedule.setShiftId(shift.getShiftId());
                                    schedule.setActualShiftId(teamSchedule.getShiftId());
                                    schedule.setShiftName(shift.getShiftName());
                                    schedule.setScheduledDate(scheduleDate);
                                    
                                    // 设置班次时间，需要将班次的开始时间和结束时间与排班日期结合
                                    if (shift.getStartTime() != null) {
                                        schedule.setStartTime(combineDateAndTime(scheduleDate, shift.getStartTime()));
                                    }
                                    if (shift.getEndTime() != null) {
                                        schedule.setEndTime(combineDateAndTime(scheduleDate, shift.getEndTime()));
                                    }
                                    
                                    schedule.setScheduleStatus("normal"); // normal-正常
                                    schedule.setIsWorkingDay(1); // 正常工作日
                                    
                                    schedulesToInsert.add(schedule);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // 批量插入排班明细
        for (SchEmployeeSchedule schedule : schedulesToInsert) {
            int result = insertSchEmployeeSchedule(schedule);
            if (result > 0) {
                generatedCount++;
            }
        }
        
        return generatedCount;
    }

    // 获取有效班组成员的辅助方法
    private List<SchTeamMember> getValidTeamMembers() {
        LambdaQueryWrapper<SchTeamMember> queryWrapper = new LambdaQueryWrapper<>();
        // 这里根据实际需求添加状态过滤条件，status=1为有效成员
        queryWrapper.eq(SchTeamMember::getStatus, 1);
        return schTeamMemberMapper.selectList(queryWrapper);
    }

    // 获取有效排班计划的辅助方法
    private List<SchPlan> getValidPlans() {
        return schPlanMapper.selectList(null); // 查询所有排班计划
    }

    // 根据计划ID获取计划班组关联信息的辅助方法
    private List<SchPlanTeam> getPlanTeamsByPlanId(Long planId) {
        LambdaQueryWrapper<SchPlanTeam> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SchPlanTeam::getPlanId, planId);
        return schPlanTeamMapper.selectList(queryWrapper);
    }

    // 根据计划ID获取班组排班配置的辅助方法
    private List<SchTeamSchedule> getTeamSchedulesByPlanId(Long planId) {
        LambdaQueryWrapper<SchTeamSchedule> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SchTeamSchedule::getPlanId, planId);
        return schTeamScheduleMapper.selectList(queryWrapper);
    }

    // 根据ID获取班次信息的辅助方法
    private SchShift getShiftById(Long shiftId) {
        if (shiftId == null) return null;
        return schShiftMapper.selectSchShiftByShiftId(shiftId);
    }

    // 根据ID获取班组信息的辅助方法
    private SchTeam getTeamById(Long teamId) {
        if (teamId == null) return null;
        return schTeamMapper.selectSchTeamByTeamId(teamId);
    }

    // 根据ID获取班组名称的辅助方法
    private String getTeamNameById(Long teamId) {
        if (teamId == null) return "";
        SchTeam team = schTeamMapper.selectSchTeamByTeamId(teamId);
        return team != null ? team.getTeamName() : "";
    }

    // 根据周期类型生成排班日期列表
    private List<Date> generateScheduleDates(Date startDate, Date endDate, String periodType) {
        List<Date> dates = new ArrayList<>();
        Calendar startCal = Calendar.getInstance();
        startCal.setTime(startDate);
        Calendar endCal = Calendar.getInstance();
        endCal.setTime(endDate);
        endCal.add(Calendar.DAY_OF_MONTH, 1); // 包含结束日期

        // 计算所有日期
        Calendar currentCal = (Calendar) startCal.clone();
        while (currentCal.before(endCal)) {
            dates.add(currentCal.getTime());
            currentCal.add(Calendar.DAY_OF_MONTH, 1);
        }

        return dates;
    }

    // 判断日期是否为周期的最后一天
    private boolean isPeriodLastDay(Date date, Date startDate, String periodType) {
        Calendar dateCal = Calendar.getInstance();
        dateCal.setTime(date);
        Calendar startCal = Calendar.getInstance();
        startCal.setTime(startDate);
        startCal.set(Calendar.HOUR_OF_DAY, 0);
        startCal.set(Calendar.MINUTE, 0);
        startCal.set(Calendar.SECOND, 0);
        startCal.set(Calendar.MILLISECOND, 0);

        // 计算日期属于哪个周期
        Calendar periodStart = (Calendar) startCal.clone();
        Calendar nextPeriodStart = (Calendar) startCal.clone();

        // 根据周期类型调整
        if ("week".equals(periodType)) {
            // 计算当前日期属于第几周
            long diffInDays = (dateCal.getTimeInMillis() - startCal.getTimeInMillis()) / (24 * 60 * 60 * 1000);
            long weeks = diffInDays / 7;
            periodStart.add(Calendar.WEEK_OF_YEAR, (int) weeks);
            nextPeriodStart.add(Calendar.WEEK_OF_YEAR, (int) weeks + 1);
        } else if ("month".equals(periodType)) {
            // 计算当前日期属于第几月
            int year = dateCal.get(Calendar.YEAR);
            int month = dateCal.get(Calendar.MONTH);
            nextPeriodStart.set(year, month + 1, 1); // 下个月第一天
            periodStart.set(year, month, 1); // 当月第一天
        } else if ("quarter".equals(periodType)) {
            // 计算当前日期属于第几季度
            int year = dateCal.get(Calendar.YEAR);
            int quarter = (dateCal.get(Calendar.MONTH) / 3) + 1;
            // 设置到季度的第一天
            int startMonth = (quarter - 1) * 3;
            periodStart.set(year, startMonth, 1);
            // 设置到下一季度的第一天
            nextPeriodStart.set(year, startMonth + 3, 1);
        } else {
            // 默认为周
            long diffInDays = (dateCal.getTimeInMillis() - startCal.getTimeInMillis()) / (24 * 60 * 60 * 1000);
            long weeks = diffInDays / 7;
            periodStart.add(Calendar.WEEK_OF_YEAR, (int) weeks);
            nextPeriodStart.add(Calendar.WEEK_OF_YEAR, (int) weeks + 1);
        }

        // 检查是否是周期的最后一天
        Calendar dayAfter = (Calendar) dateCal.clone();
        dayAfter.add(Calendar.DAY_OF_MONTH, 1);
        return dayAfter.get(Calendar.YEAR) == nextPeriodStart.get(Calendar.YEAR) &&
               dayAfter.get(Calendar.MONTH) == nextPeriodStart.get(Calendar.MONTH) &&
               dayAfter.get(Calendar.DAY_OF_MONTH) == nextPeriodStart.get(Calendar.DAY_OF_MONTH);
    }

    // 将日期和时间合并成一个Date对象
    private Date combineDateAndTime(Date date, Date time) {
        if (date == null || time == null) {
            return null;
        }
        
        Calendar dateCal = Calendar.getInstance();
        dateCal.setTime(date);
        
        Calendar timeCal = Calendar.getInstance();
        timeCal.setTime(time);
        
        // 设置时间为日期的年月日，但保留时分秒
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