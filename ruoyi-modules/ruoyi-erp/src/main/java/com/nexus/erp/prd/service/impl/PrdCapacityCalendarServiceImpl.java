package com.nexus.erp.prd.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
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
 * 产能日历Service实现
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

    @Override
    public TableDataInfo<PrdCapacityCalendarVo> selectPageList(PrdCapacityCalendarBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PrdCapacityCalendar> lqw = buildQueryWrapper(bo);
        IPage<PrdCapacityCalendarVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<PrdCapacityCalendarVo> selectList(PrdCapacityCalendarBo bo) {
        LambdaQueryWrapper<PrdCapacityCalendar> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    @Override
    public PrdCapacityCalendarVo selectById(Long calendarId) {
        return baseMapper.selectVoById(calendarId);
    }

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(PrdCapacityCalendarBo bo) {
        PrdCapacityCalendar entity = MapstructUtils.convert(bo, PrdCapacityCalendar.class);
        calculateEffectiveHours(entity);
        validEntityBeforeSave(entity);
        return baseMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (isValid) {
            // 可添加业务校验
        }
        return baseMapper.deleteByIds(ids) > 0;
    }

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

    @Override
    public List<PrdCapacityCalendarVo> queryAvailableCapacity(String objectType, Long objectId,
                                                               Date startDate, Date endDate) {
        return baseMapper.selectAvailableCapacity(objectType, objectId, startDate, endDate);
    }

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
