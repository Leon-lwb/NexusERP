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
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.prd.domain.PrdCapacityCalendar;
import com.nexus.erp.prd.domain.PrdScheduleCapacityBooking;
import com.nexus.erp.prd.domain.bo.PrdCapacityCalendarBo;
import com.nexus.erp.prd.domain.vo.PrdCapacityCalendarVo;
import com.nexus.erp.prd.mapper.PrdCapacityCalendarMapper;
import com.nexus.erp.prd.mapper.PrdScheduleCapacityBookingMapper;
import com.nexus.erp.prd.service.IPrdCapacityCalendarService;
import com.nexus.erp.sch.domain.SchPlan;
import com.nexus.erp.sch.domain.SchShift;
import com.nexus.erp.sch.mapper.SchPlanMapper;
import com.nexus.erp.sch.mapper.SchShiftMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Time;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 产能日历服务业务层实现
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
    private final PrdScheduleCapacityBookingMapper bookingMapper;
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
        /** 计算有效产能和剩余产能 */
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
        if (bo == null || bo.getCalendarId() == null || bo.getCalendarId() <= 0)
            throw new ServiceException("产能日历ID无效");
        /**
         * 班次删除先锁班次再查日历。先只读取得未修改字段，然后按相同顺序锁班次、锁日历，
         * 防止编辑与班次删除形成反向锁顺序，锁定日历后复核快照。
         */
        PrdCapacityCalendar snapshot = baseMapper.selectOne(Wrappers.lambdaQuery(PrdCapacityCalendar.class)
            .eq(PrdCapacityCalendar::getCalendarId, bo.getCalendarId()));
        String tenant = currentTenantId();
        if (snapshot == null || !bo.getCalendarId().equals(snapshot.getCalendarId())
            || !tenant.equals(snapshot.getTenantId())) throw new ServiceException("产能日历不存在或租户不一致");
        PrdCapacityCalendar entity = MapstructUtils.convert(bo, PrdCapacityCalendar.class);
        if (entity.getStatus() == null) entity.setStatus(snapshot.getStatus());
        if (entity.getShiftId() == null) entity.setShiftId(snapshot.getShiftId());
        if (entity.getAvailableHours() == null) entity.setAvailableHours(snapshot.getAvailableHours());
        if (entity.getEfficiency() == null) entity.setEfficiency(snapshot.getEfficiency());
        calculateEffectiveHours(entity);
        validEntityBeforeSave(entity);
        PrdCapacityCalendar existing = baseMapper.selectOne(Wrappers.lambdaQuery(PrdCapacityCalendar.class)
            .eq(PrdCapacityCalendar::getCalendarId, bo.getCalendarId()).last("FOR UPDATE"));
        ensureCalendarEditable(existing, tenant);
        if (!Objects.equals(snapshot.getShiftId(), existing.getShiftId())
            || !Objects.equals(snapshot.getStatus(), existing.getStatus())
            || (bo.getAvailableHours() == null && !Objects.equals(snapshot.getAvailableHours(), existing.getAvailableHours()))
            || (bo.getEfficiency() == null && !Objects.equals(snapshot.getEfficiency(), existing.getEfficiency())))
            throw new ServiceException("产能日历并发变更，请重试");
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
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw new ServiceException("产能日历ID无效");
        /** 业务安全校验不可由 isValid=false 绕开。与排程发布按相同日历行加锁。 */
        List<Long> orderedIds = ids.stream().distinct().sorted().toList();
        String tenantId = currentTenantId();
        /** 与发布相同：逐 PK 升序锁行，而不是依赖 IN + ORDER BY 的实际索引扫描顺序。 */
        List<PrdCapacityCalendar> locked = new ArrayList<>();
        for (Long id : orderedIds) {
            PrdCapacityCalendar calendar = baseMapper.selectOne(Wrappers.lambdaQuery(PrdCapacityCalendar.class)
                .eq(PrdCapacityCalendar::getCalendarId, id).last("FOR UPDATE"));
            if (calendar == null || !id.equals(calendar.getCalendarId()))
                throw new ServiceException("产能日历不存在或不可访问");
            locked.add(calendar);
        }
        for (PrdCapacityCalendar calendar : locked) ensureCalendarEditable(calendar, tenantId);
        return baseMapper.deleteByIds(orderedIds) > 0;
    }

    private String currentTenantId() {
        String tenant = TenantHelper.isEnable() ? TenantHelper.getTenantId() : "000000";
        if (StringUtils.isBlank(tenant)) throw new ServiceException("产能日历租户上下文缺失");
        return tenant;
    }

    private void ensureCalendarEditable(PrdCapacityCalendar calendar, String tenantId) {
        if (calendar == null || calendar.getCalendarId() == null
            || !tenantId.equals(calendar.getTenantId()))
            throw new ServiceException("产能日历不存在或租户不一致");
        if (calendar.getPlannedHours() == null || calendar.getPlannedHours().signum() < 0
            || calendar.getPlannedHours().signum() > 0)
            throw new ServiceException("产能日历已有计划工时或数据无效，不能修改/删除");
        Long bookings = bookingMapper.selectCount(Wrappers.lambdaQuery(PrdScheduleCapacityBooking.class)
            .eq(PrdScheduleCapacityBooking::getTenantId, tenantId)
            .eq(PrdScheduleCapacityBooking::getCalendarId, calendar.getCalendarId()));
        if (bookings == null || bookings > 0)
            throw new ServiceException("产能日历已有预约凭据，不能修改/删除");
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
        shiftLqw.eq(SchShift::getPlanId, planId)
            .orderByAsc(SchShift::getShiftId).last("FOR UPDATE");
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
                /** 计算可用工时 = 班次总工时 - 休息工时 */
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
                } catch (DuplicateKeyException duplicate) {
                    /** 只忽略唯一键冲突；死锁/连接失败必须抛出，交给事务整体回滚和调用方重试。 */
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
        if (calendarId == null || calendarId <= 0 || hours == null || hours.signum() <= 0) {
            throw new ServiceException("产能占用ID或工时无效");
        }
        String tenant = currentTenantId();
        PrdCapacityCalendar calendar = baseMapper.selectOne(Wrappers.lambdaQuery(PrdCapacityCalendar.class)
            .eq(PrdCapacityCalendar::getCalendarId, calendarId).last("FOR UPDATE"));
        if (calendar == null || !tenant.equals(calendar.getTenantId()) || !"ACTIVE".equals(calendar.getStatus())
            || calendar.getPlannedHours() == null || calendar.getRemainingHours() == null
            || calendar.getRemainingHours().compareTo(hours) < 0) {
            throw new ServiceException("产能日历不存在、租户不符或剩余产能不足");
        }
        BigDecimal planned = calendar.getPlannedHours();
        BigDecimal remaining = calendar.getRemainingHours();
        /** 只更新两列，绝不把读出的 actual_hours/班次/日期/状态整实体回写。 */
        int rows = baseMapper.update(null, Wrappers.lambdaUpdate(PrdCapacityCalendar.class)
            .eq(PrdCapacityCalendar::getCalendarId, calendarId)
            .eq(PrdCapacityCalendar::getTenantId, tenant)
            .eq(PrdCapacityCalendar::getStatus, "ACTIVE")
            .eq(PrdCapacityCalendar::getPlannedHours, planned)
            .eq(PrdCapacityCalendar::getRemainingHours, remaining)
            .set(PrdCapacityCalendar::getPlannedHours, planned.add(hours))
            .set(PrdCapacityCalendar::getRemainingHours, remaining.subtract(hours)));
        if (rows != 1) throw new ServiceException("产能日历占用竞争或写入失败");
    }

    /** 已生成预约凭据的计划负荷不允许经此通用接口释放；只接受将来的凭据撤销事务。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseCapacity(Long calendarId, BigDecimal hours) {
        if (calendarId == null || calendarId <= 0 || hours == null || hours.signum() <= 0) {
            throw new ServiceException("产能释放ID或工时无效");
        }
        String tenant = currentTenantId();
        PrdCapacityCalendar calendar = baseMapper.selectOne(Wrappers.lambdaQuery(PrdCapacityCalendar.class)
            .eq(PrdCapacityCalendar::getCalendarId, calendarId).last("FOR UPDATE"));
        if (calendar == null || !tenant.equals(calendar.getTenantId())
            || calendar.getPlannedHours() == null || calendar.getRemainingHours() == null
            || calendar.getEffectiveHours() == null) {
            throw new ServiceException("产能日历不存在或来源无效");
        }
        List<PrdScheduleCapacityBooking> bookings = bookingMapper.selectList(
            Wrappers.lambdaQuery(PrdScheduleCapacityBooking.class)
                .eq(PrdScheduleCapacityBooking::getTenantId, tenant)
                .eq(PrdScheduleCapacityBooking::getCalendarId, calendarId).last("FOR UPDATE"));
        if (bookings == null || !bookings.isEmpty()) {
            throw new ServiceException("产能日历已有预约凭据，不允许直接释放");
        }
        BigDecimal planned = calendar.getPlannedHours();
        BigDecimal remaining = calendar.getRemainingHours();
        if (planned.compareTo(hours) < 0 || remaining.add(hours).compareTo(calendar.getEffectiveHours()) > 0) {
            throw new ServiceException("可释放的未预约计划工时不足");
        }
        int rows = baseMapper.update(null, Wrappers.lambdaUpdate(PrdCapacityCalendar.class)
            .eq(PrdCapacityCalendar::getCalendarId, calendarId)
            .eq(PrdCapacityCalendar::getTenantId, tenant)
            .eq(PrdCapacityCalendar::getPlannedHours, planned)
            .eq(PrdCapacityCalendar::getRemainingHours, remaining)
            .set(PrdCapacityCalendar::getPlannedHours, planned.subtract(hours))
            .set(PrdCapacityCalendar::getRemainingHours, remaining.add(hours)));
        if (rows != 1) throw new ServiceException("产能日历释放竞争或写入失败");
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
        boolean bookable = (entity.getStatus() == null || "ACTIVE".equals(entity.getStatus()))
            && entity.getEffectiveHours() != null && entity.getEffectiveHours().signum() > 0;
        Long shiftId = entity.getShiftId();
        if (shiftId == null) {
            if (bookable) throw new ServiceException("有效产能日历必须关联班次");
            return;
        }
        if (shiftId <= 0) throw new ServiceException("产能日历班次ID无效");
        /** 与 SchShiftServiceImpl.lockUnboundShift 的锁序一致；只有持有班次锁才能防止写入后悬挂引用。 */
        SchShift shift = schShiftMapper.selectOne(Wrappers.lambdaQuery(SchShift.class)
            .eq(SchShift::getShiftId, shiftId).last("FOR UPDATE"));
        if (shift == null || !shiftId.equals(shift.getShiftId())
            || !currentTenantId().equals(shift.getTenantId()))
            throw new ServiceException("产能日历关联班次不存在或租户不一致");
        if (bookable && (shift.getStartTime() == null || shift.getEndTime() == null
            || shift.getIsCrossDay() == null || (shift.getIsCrossDay() != 0 && shift.getIsCrossDay() != 1)
            || (shift.getIsCrossDay() == 0 && !shiftTime(shift.getEndTime()).isAfter(shiftTime(shift.getStartTime())))
            || (shift.getIsCrossDay() == 1 && !shiftTime(shift.getEndTime()).isBefore(shiftTime(shift.getStartTime())))))
            throw new ServiceException("产能日历班次起止时间或跨天标记无效");
    }

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final long DAY_SECONDS = 24L * 60 * 60;

    private LocalTime shiftTime(Date time) {
        return time instanceof Time sqlTime
                ? sqlTime.toLocalTime()
                : Instant.ofEpochMilli(time.getTime()).atZone(BUSINESS_ZONE).toLocalTime();
    }

    private BigDecimal calculateShiftAvailableHours(SchShift shift) {
        if (shift.getStartTime() == null || shift.getEndTime() == null) return BigDecimal.ZERO;
        long start = shiftTime(shift.getStartTime()).toSecondOfDay();
        long end = shiftTime(shift.getEndTime()).toSecondOfDay();
        long duration = end - start;
        if (duration < 0) duration += DAY_SECONDS;
        long restSeconds = 0;
        if (shift.getRestStartTime() != null && shift.getRestEndTime() != null) {
            long restStart = shiftTime(shift.getRestStartTime()).toSecondOfDay();
            long restEnd = shiftTime(shift.getRestEndTime()).toSecondOfDay();
            long restDuration = restEnd - restStart;
            if (restDuration < 0) restDuration += DAY_SECONDS;
            /** 时段按实际时间轴相交，23:30—00:30 要扣满一小时；不回填/修改已生成日历。 */
            for (int dayOffset = -1; dayOffset <= 1; dayOffset++) {
                long offset = restStart - start + dayOffset * DAY_SECONDS;
                restSeconds += Math.max(0, Math.min(duration, offset + restDuration) - Math.max(0, offset));
            }
        }
        return BigDecimal.valueOf(Math.max(0, duration - restSeconds))
            .divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
    }
}
