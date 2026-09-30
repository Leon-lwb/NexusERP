package com.nexus.erp.sch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.prd.domain.PrdCapacityCalendar;
import com.nexus.erp.prd.domain.PrdScheduleCapacityBooking;
import com.nexus.erp.prd.mapper.PrdCapacityCalendarMapper;
import com.nexus.erp.prd.mapper.PrdScheduleCapacityBookingMapper;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.sch.domain.SchHoliday;
import com.nexus.erp.sch.domain.bo.SchHolidayBo;
import com.nexus.erp.sch.domain.vo.SchHolidayVo;
import com.nexus.erp.sch.mapper.SchHolidayMapper;
import com.nexus.erp.sch.service.ISchHolidayService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Objects;

/**
 * 节假日设置服务业务层处理
 *
 * @author nexus
 * @date 2025-12-17
 */
@RequiredArgsConstructor
@DS("erp")
@Service
public class SchHolidayServiceImpl implements ISchHolidayService {

    private final SchHolidayMapper baseMapper;
    private final PrdCapacityCalendarMapper calendarMapper;
    private final PrdScheduleCapacityBookingMapper bookingMapper;

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 分页查询节假日设置列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 节假日设置分页列表
     */
    @Override
    public TableDataInfo<SchHolidayVo> selectPageList(SchHolidayBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SchHoliday> lqw = buildQueryWrapper(bo);
        Page<SchHolidayVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 查询节假日设置列表
     *
     * @param bo 查询参数
     * @return 节假日设置列表
     */
    @Override
    public List<SchHolidayVo> selectList(SchHolidayBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据ID获取节假日设置详情
     *
     * @param id 节假日ID
     * @return 节假日设置详情
     */
    @Override
    public SchHolidayVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 新增节假日设置
     *
     * @param bo 节假日设置业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(SchHolidayBo bo) {
        LocalDate date = requireDate(bo == null ? null : bo.getHolidayDate());
        String tenant = currentTenantId();
        lockDates(List.of(date), tenant);
        refuseBookedDates(List.of(date), tenant);
        SchHoliday entity = MapstructUtils.convert(bo, SchHoliday.class);
        return baseMapper.insert(entity);
    }

    /**
     * 修改节假日设置
     *
     * @param bo 节假日设置业务对象
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(SchHolidayBo bo) {
        if (bo == null || bo.getHolidayId() == null || bo.getHolidayId() <= 0)
            throw new ServiceException("节假日ID无效");
        String tenant = currentTenantId();
        SchHoliday snapshot = requireOwned(baseMapper.selectById(bo.getHolidayId()), tenant);
        LocalDate oldDate = requireDate(snapshot.getHolidayDate());
        LocalDate newDate = requireDate(bo.getHolidayDate());
        List<LocalDate> dates = orderedDates(List.of(oldDate, newDate));
        lockDates(dates, tenant);
        SchHoliday current = requireOwned(baseMapper.selectOne(Wrappers.lambdaQuery(SchHoliday.class)
            .eq(SchHoliday::getHolidayId, bo.getHolidayId()).last("FOR UPDATE")), tenant);
        if (!oldDate.equals(requireDate(current.getHolidayDate())))
            throw new ServiceException("节假日日期已变化，请重新提交");
        refuseBookedDates(dates, tenant);
        SchHoliday entity = MapstructUtils.convert(bo, SchHoliday.class);
        return baseMapper.updateById(entity);
    }

    /**
     * 批量删除节假日设置
     *
     * @param ids 节假日ID数组
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0 || Arrays.stream(ids).anyMatch(id -> id == null || id <= 0))
            throw new ServiceException("节假日ID无效");
        String tenant = currentTenantId();
        List<Long> orderedIds = Arrays.stream(ids).distinct().sorted().toList();
        List<SchHoliday> snapshots = orderedIds.stream()
            .map(id -> requireOwned(baseMapper.selectById(id), tenant)).toList();
        List<LocalDate> dates = orderedDates(snapshots.stream()
            .map(row -> requireDate(row.getHolidayDate())).toList());
        lockDates(dates, tenant);
        for (int i = 0; i < orderedIds.size(); i++) {
            SchHoliday current = requireOwned(baseMapper.selectOne(Wrappers.lambdaQuery(SchHoliday.class)
                .eq(SchHoliday::getHolidayId, orderedIds.get(i)).last("FOR UPDATE")), tenant);
            if (!Objects.equals(requireDate(current.getHolidayDate()), requireDate(snapshots.get(i).getHolidayDate())))
                throw new ServiceException("节假日日期已变化，请重新提交");
        }
        refuseBookedDates(dates, tenant);
        return baseMapper.deleteBatchIds(orderedIds);
    }

    private String currentTenantId() {
        String tenant = TenantHelper.isEnable() ? TenantHelper.getTenantId() : "000000";
        if (StringUtils.isBlank(tenant)) throw new ServiceException("节假日租户上下文缺失");
        return tenant;
    }

    private SchHoliday requireOwned(SchHoliday row, String tenant) {
        if (row == null || row.getHolidayId() == null || !tenant.equals(row.getTenantId()))
            throw new ServiceException("节假日不存在或租户不一致");
        return row;
    }

    private LocalDate requireDate(Date date) {
        if (date == null) throw new ServiceException("节假日日期不能为空");
        return date instanceof java.sql.Date sqlDate ? sqlDate.toLocalDate()
            : Instant.ofEpochMilli(date.getTime()).atZone(BUSINESS_ZONE).toLocalDate();
    }

    private List<LocalDate> orderedDates(List<LocalDate> dates) {
        return dates.stream().distinct().sorted().toList();
    }

    private void lockDates(List<LocalDate> dates, String tenant) {
        for (LocalDate day : dates) {
            /**
             * uk_tenant_holiday_date(tenant_id, holiday_date) 上的 RR 范围锁覆盖缺行间隙。
             * 发布同样先锁 租户/日期范围，后锁日历；不能换成按 holidayId 锁后再锁日期。
             */
            baseMapper.selectList(Wrappers.lambdaQuery(SchHoliday.class)
                .eq(SchHoliday::getTenantId, tenant)
                .ge(SchHoliday::getHolidayDate, Date.from(day.atStartOfDay(BUSINESS_ZONE).toInstant()))
                .lt(SchHoliday::getHolidayDate, Date.from(day.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant()))
                .orderByAsc(SchHoliday::getHolidayDate).last("FOR UPDATE"));
        }
    }

    private void refuseBookedDates(List<LocalDate> dates, String tenant) {
        /**
         * 节假日范围已先锁住发布；发现候选日历 ID 后按全局 PK 升序锁定现值，
         * 不按日期逐段加锁（跨日期编辑会与日历批删/发布形成反向顺序）。
         */
        Map<Long, LocalDate> candidateIds = new HashMap<>();
        for (LocalDate day : dates) {
            List<PrdCapacityCalendar> discovered = calendarMapper.selectList(Wrappers.lambdaQuery(PrdCapacityCalendar.class)
                .eq(PrdCapacityCalendar::getCalendarDate, java.sql.Date.valueOf(day))
                .eq(PrdCapacityCalendar::getTenantId, tenant));
            if (discovered == null) throw new ServiceException("产能日历查询失败");
            for (PrdCapacityCalendar calendar : discovered) {
                if (calendar == null || calendar.getCalendarId() == null || calendar.getCalendarId() <= 0
                    || candidateIds.putIfAbsent(calendar.getCalendarId(), day) != null)
                    throw new ServiceException("产能日历候选ID无效或重复");
            }
        }
        for (Long id : candidateIds.keySet().stream().sorted().toList()) {
            PrdCapacityCalendar calendar = calendarMapper.selectOne(Wrappers.lambdaQuery(PrdCapacityCalendar.class)
                .eq(PrdCapacityCalendar::getCalendarId, id).last("FOR UPDATE"));
            if (calendar == null || !id.equals(calendar.getCalendarId()) || !tenant.equals(calendar.getTenantId())
                || !candidateIds.get(id).equals(requireDate(calendar.getCalendarDate()))
                || calendar.getPlannedHours() == null || calendar.getPlannedHours().signum() != 0)
                throw new ServiceException("日期已有产能占用或日历来源不可信，不能修改节假日");
        }
        for (LocalDate day : dates) {
            /**
             * 前面的候选 ID 读取可能复用编辑/删除在 selectById 时建立的旧 RR 快照。
             * idx_date(calendar_date) 的当前范围读包含缺行间隙；出现此前不可见的日历则拒绝并回滚。
             * 这一步在已持有的 PK 锁之后，可能与并发批删形成可回滚死锁，不吞掉/误判死锁异常。
             */
            List<PrdCapacityCalendar> current = calendarMapper.selectList(Wrappers.lambdaQuery(PrdCapacityCalendar.class)
                .eq(PrdCapacityCalendar::getCalendarDate, java.sql.Date.valueOf(day))
                .eq(PrdCapacityCalendar::getTenantId, tenant)
                .orderByAsc(PrdCapacityCalendar::getCalendarId).last("FOR UPDATE"));
            if (current == null) throw new ServiceException("产能日历复核失败");
            Set<Long> currentIds = new HashSet<>();
            for (PrdCapacityCalendar row : current) {
                if (row == null || row.getCalendarId() == null || !tenant.equals(row.getTenantId())
                    || !day.equals(requireDate(row.getCalendarDate())) || row.getPlannedHours() == null
                    || row.getPlannedHours().signum() != 0 || !currentIds.add(row.getCalendarId()))
                    throw new ServiceException("日期已有产能占用或日历来源不可信，不能修改节假日");
            }
            Set<Long> expectedIds = candidateIds.entrySet().stream()
                .filter(entry -> day.equals(entry.getValue())).map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
            if (!currentIds.equals(expectedIds)) throw new ServiceException("产能日历已变化，请重试节假日修改");
        }
        for (Long id : candidateIds.keySet().stream().sorted().toList()) {
            /** idx_pscb_tenant_calendar 支撑锁定当前读；即使账面 planned=0 也拒绝孤立预约。 */
            List<PrdScheduleCapacityBooking> bookings = bookingMapper.selectList(Wrappers.lambdaQuery(PrdScheduleCapacityBooking.class)
                .eq(PrdScheduleCapacityBooking::getTenantId, tenant)
                .eq(PrdScheduleCapacityBooking::getCalendarId, id).last("FOR UPDATE"));
            if (bookings == null || !bookings.isEmpty())
                throw new ServiceException("日期已有产能预约凭据，不能修改节假日");
        }
    }

    private LambdaQueryWrapper<SchHoliday> buildQueryWrapper(SchHolidayBo bo) {
        LambdaQueryWrapper<SchHoliday> lqw = Wrappers.lambdaQuery();
        if (bo != null) {
            lqw.eq(bo.getHolidayDate() != null, SchHoliday::getHolidayDate, bo.getHolidayDate());
            lqw.like(StringUtils.isNotBlank(bo.getHolidayName()), SchHoliday::getHolidayName, bo.getHolidayName());
            lqw.eq(bo.getYear() != null, SchHoliday::getYear, bo.getYear());
            lqw.eq(bo.getHolidayType() != null, SchHoliday::getHolidayType, bo.getHolidayType());
        }
        return lqw;
    }
}
