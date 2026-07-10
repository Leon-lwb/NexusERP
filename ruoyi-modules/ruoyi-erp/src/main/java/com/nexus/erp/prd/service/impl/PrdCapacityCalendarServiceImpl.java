package com.nexus.erp.prd.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.prd.domain.PrdCapacityCalendar;
import com.nexus.erp.prd.domain.bo.PrdCapacityCalendarBo;
import com.nexus.erp.prd.domain.vo.PrdCapacityCalendarVo;
import com.nexus.erp.prd.mapper.PrdCapacityCalendarMapper;
import com.nexus.erp.prd.service.IPrdCapacityCalendarService;
import com.nexus.erp.sch.domain.SchPlan;
import com.nexus.erp.sch.domain.SchShift;
import com.nexus.erp.sch.mapper.SchPlanMapper;
import com.nexus.erp.sch.mapper.SchShiftMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 产能日历 Service 业务层实现
 * <p>
 * 实现产能日历相关的业务逻辑，包括产能日历的查询、生成、占用与释放等操作。
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class PrdCapacityCalendarServiceImpl implements IPrdCapacityCalendarService {

    private final PrdCapacityCalendarMapper baseMapper;
    private final SchPlanMapper schPlanMapper;
    private final SchShiftMapper schShiftMapper;

    /**
     * 分页查询产能日历列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 产能日历分页列表
     */
    @Override
    public TableDataInfo<PrdCapacityCalendarVo> selectPageList(PrdCapacityCalendarBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PrdCapacityCalendar> lqw = buildQueryWrapper(bo);
        IPage<PrdCapacityCalendarVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询产能日历列表（不分页）
     *
     * @param bo 查询参数
     * @return 产能日历列表
     */
    @Override
    public List<PrdCapacityCalendarVo> selectList(PrdCapacityCalendarBo bo) {
        LambdaQueryWrapper<PrdCapacityCalendar> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    /**
     * 根据 ID 查询产能日历详情
     *
     * @param calendarId 产能日历 ID
     * @return 产能日历详细信息
     */
    @Override
    public PrdCapacityCalendarVo selectById(Long calendarId) {
        return baseMapper.selectVoById(calendarId);
    }

    /**
     * 新增产能日历
     * <p>
     * 计算有效产能和剩余产能后保存。
     *
     * @param bo 产能日历业务对象
     * @return 是否新增成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(PrdCapacityCalendarBo bo) {
        PrdCapacityCalendar entity = MapstructUtils.convert(bo, PrdCapacityCalendar.class);
        // 计算有效产能和剩余产能
        calculateEffectiveHours(entity);
        entity.setRemainingHours(entity.getEffectiveHours());
        validEntityBeforeSave(entity);
        return baseMapper.insert(entity) > 0;
    }

    /**
     * 修改产能日历
     * <p>
     * 重新计算有效产能后更新。
     *
     * @param bo 产能日历业务对象
     * @return 是否更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(PrdCapacityCalendarBo bo) {
        PrdCapacityCalendar entity = MapstructUtils.convert(bo, PrdCapacityCalendar.class);
        calculateEffectiveHours(entity);
        validEntityBeforeSave(entity);
        return baseMapper.updateById(entity) > 0;
    }

    /**
     * 批量删除产能日历
     *
     * @param ids     要删除的产能日历 ID 集合
     * @param isValid 是否进行业务校验
     * @return 是否删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            // 可添加业务校验
        }
        return baseMapper.deleteByIds(ids) > 0;
    }

    /**
     * 根据排班计划生成产能日历
     * <p>
     * 遍历日期范围内的每一天，根据排班计划的班次定义生成产能日历记录。
     *
     * @param planId     排班计划 ID
     * @param startDate  开始日期
     * @param endDate    结束日期
     * @param objectType 对象类型
     * @param objectId   对象 ID
     * @param objectCode 对象编码
     * @param objectName 对象名称
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateFromSchedule(Long planId, Date startDate, Date endDate, String objectType, Long objectId, String objectCode, String objectName) {
        SchPlan plan = schPlanMapper.selectById(planId);
        if (plan == null) {
            throw new ServiceException("排班计划不存在");
        }

        LambdaQueryWrapper<SchShift> shiftLqw = Wrappers.lambdaQuery();
        shiftLqw.eq(SchShift::getPlanId, planId);
        List<SchShift> shifts = schShiftMapper.selectList(shiftLqw);
        if (shifts.isEmpty()) {
            throw new ServiceException("排班计划下无班次定义");
        }

        Calendar cal = Calendar.getInstance();
        cal.setTime(startDate);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        Calendar endCal = Calendar.getInstance();
        endCal.setTime(endDate);
        endCal.set(Calendar.HOUR_OF_DAY, 0);
        endCal.set(Calendar.MINUTE, 0);
        endCal.set(Calendar.SECOND, 0);
        endCal.set(Calendar.MILLISECOND, 0);

        int generatedCount = 0;
        while (!cal.after(endCal)) {
            Date currentDate = cal.getTime();
            for (SchShift shift : shifts) {
                // 计算可用工时 = 班次总工时 - 休息工时
                BigDecimal availableHours = calculateShiftAvailableHours(shift);

                PrdCapacityCalendar calendar = new PrdCapacityCalendar();
                calendar.setCalendarDate(currentDate);
                calendar.setShiftId(shift.getShiftId());
                calendar.setShiftCode(shift.getShiftCode());
                calendar.setShiftName(shift.getShiftName());
                calendar.setAvailableHours(availableHours);
                calendar.setEfficiency(new BigDecimal("100"));
                calendar.setEffectiveHours(availableHours);
                calendar.setRemainingHours(availableHours);
                calendar.setPlannedHours(BigDecimal.ZERO);
                calendar.setActualHours(BigDecimal.ZERO);
                calendar.setObjectType(objectType);
                calendar.setObjectId(objectId);
                calendar.setObjectCode(objectCode);
                calendar.setObjectName(objectName);
                calendar.setStatus("ACTIVE");

                try {
                    baseMapper.insert(calendar);
                    generatedCount++;
                } catch (Exception e) {
                    log.warn("产能日历重复，日期:{}，班次:{}", currentDate, shift.getShiftCode());
                }
            }
            cal.add(Calendar.DATE, 1);
        }

        log.info("排班计划[{}]生成产能日历完成，共生成{}条记录", plan.getPlanName(), generatedCount);
    }

    /**
     * 查询指定对象的可用产能列表
     *
     * @param objectType 对象类型
     * @param objectId   对象 ID
     * @param startDate  开始日期
     * @param endDate    结束日期
     * @return 可用产能日历列表
     */
    @Override
    public List<PrdCapacityCalendarVo> queryAvailableCapacity(String objectType, Long objectId,
                                                               Date startDate, Date endDate) {
        LambdaQueryWrapper<PrdCapacityCalendar> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(objectType), PrdCapacityCalendar::getObjectType, objectType);
        lqw.eq(objectId != null, PrdCapacityCalendar::getObjectId, objectId);
        lqw.ge(startDate != null, PrdCapacityCalendar::getCalendarDate, startDate);
        lqw.le(endDate != null, PrdCapacityCalendar::getCalendarDate, endDate);
        lqw.eq(PrdCapacityCalendar::getStatus, "ACTIVE");
        lqw.gt(PrdCapacityCalendar::getRemainingHours, BigDecimal.ZERO);
        lqw.orderByAsc(PrdCapacityCalendar::getCalendarDate);
        lqw.orderByAsc(PrdCapacityCalendar::getShiftId);
        return baseMapper.selectVoList(lqw);
    }

    /**
     * 占用产能
     * <p>
     * 校验剩余产能是否充足，更新已计划工时和剩余工时。
     *
     * @param calendarId 产能日历 ID
     * @param hours      占用工时数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void occupyCapacity(Long calendarId, BigDecimal hours) {
        if (calendarId == null || hours == null || hours.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        PrdCapacityCalendar calendar = baseMapper.selectById(calendarId);
        if (calendar == null) {
            throw new ServiceException("产能日历记录不存在");
        }
        if (calendar.getRemainingHours() == null || calendar.getRemainingHours().compareTo(hours) < 0) {
            throw new ServiceException("产能不足，剩余" + calendar.getRemainingHours() + "小时，需要" + hours + "小时");
        }

        calendar.setPlannedHours(
            (calendar.getPlannedHours() != null ? calendar.getPlannedHours() : BigDecimal.ZERO).add(hours));
        calendar.setRemainingHours(calendar.getEffectiveHours().subtract(calendar.getPlannedHours()));
        baseMapper.updateById(calendar);
    }

    /**
     * 释放产能
     * <p>
     * 将已占用的工时释放回剩余产能，已计划工时不会小于零。
     *
     * @param calendarId 产能日历 ID
     * @param hours      释放工时数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseCapacity(Long calendarId, BigDecimal hours) {
        if (calendarId == null || hours == null || hours.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        PrdCapacityCalendar calendar = baseMapper.selectById(calendarId);
        if (calendar == null) {
            return;
        }
        calendar.setPlannedHours(
            (calendar.getPlannedHours() != null ? calendar.getPlannedHours() : BigDecimal.ZERO)
                .subtract(hours).max(BigDecimal.ZERO));
        calendar.setRemainingHours(calendar.getEffectiveHours().subtract(calendar.getPlannedHours()));
        baseMapper.updateById(calendar);
    }

    private LambdaQueryWrapper<PrdCapacityCalendar> buildQueryWrapper(PrdCapacityCalendarBo bo) {
        LambdaQueryWrapper<PrdCapacityCalendar> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getCalendarDate() != null, PrdCapacityCalendar::getCalendarDate, bo.getCalendarDate());
        lqw.eq(bo.getObjectType() != null, PrdCapacityCalendar::getObjectType, bo.getObjectType());
        lqw.eq(bo.getObjectId() != null, PrdCapacityCalendar::getObjectId, bo.getObjectId());
        lqw.eq(bo.getShiftId() != null, PrdCapacityCalendar::getShiftId, bo.getShiftId());
        lqw.eq(bo.getStatus() != null, PrdCapacityCalendar::getStatus, bo.getStatus());
        return lqw;
    }

    private void calculateEffectiveHours(PrdCapacityCalendar entity) {
        BigDecimal available = entity.getAvailableHours() != null ? entity.getAvailableHours() : BigDecimal.ZERO;
        BigDecimal efficiency = entity.getEfficiency() != null ? entity.getEfficiency() : new BigDecimal("100");
        BigDecimal effective = available.multiply(efficiency).divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);
        entity.setEffectiveHours(effective);
        if (entity.getRemainingHours() == null) {
            entity.setRemainingHours(effective);
        }
    }

    private void validEntityBeforeSave(PrdCapacityCalendar entity) {
        // 可扩展校验逻辑
    }

    private BigDecimal calculateShiftAvailableHours(SchShift shift) {
        if (shift.getStartTime() == null || shift.getEndTime() == null) {
            return BigDecimal.ZERO;
        }
        long diffMillis = shift.getEndTime().getTime() - shift.getStartTime().getTime();
        if (diffMillis < 0) {
            diffMillis += 24 * 60 * 60 * 1000; // 跨天
        }
        BigDecimal totalHours = BigDecimal.valueOf(diffMillis)
            .divide(BigDecimal.valueOf(1000 * 60 * 60), 2, BigDecimal.ROUND_HALF_UP);

        // 扣除休息时间
        if (shift.getRestStartTime() != null && shift.getRestEndTime() != null) {
            long restMillis = shift.getRestEndTime().getTime() - shift.getRestStartTime().getTime();
            if (restMillis > 0) {
                BigDecimal restHours = BigDecimal.valueOf(restMillis)
                    .divide(BigDecimal.valueOf(1000 * 60 * 60), 2, BigDecimal.ROUND_HALF_UP);
                totalHours = totalHours.subtract(restHours);
            }
        }
        return totalHours.max(BigDecimal.ZERO);
    }
}
