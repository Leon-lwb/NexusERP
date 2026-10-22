package com.nexus.erp.sch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.sch.domain.SchEmployeeSchedule;
import com.nexus.erp.sch.domain.SchPlan;
import com.nexus.erp.sch.domain.SchShift;
import com.nexus.erp.sch.domain.SchTeamMember;
import com.nexus.erp.sch.domain.SchTeamSchedule;
import com.nexus.erp.sch.mapper.SchEmployeeScheduleMapper;
import com.nexus.erp.sch.mapper.SchPlanMapper;
import com.nexus.erp.sch.mapper.SchShiftMapper;
import com.nexus.erp.sch.mapper.SchTeamMapper;
import com.nexus.erp.sch.mapper.SchTeamMemberMapper;
import com.nexus.erp.sch.mapper.SchTeamScheduleMapper;
import com.nexus.erp.sch.service.impl.SchEmployeeScheduleServiceImpl;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

/**
 * SchEmployeeScheduleServiceImpl.generate 单元测试
 * <p>核心验证: 按班组排班模板精确展开(班组→班次绑定/日期/休息日), 按计划范围清理,
 * 仅处理已发布计划, 模板日期截断到日。</p>
 *
 * @author Nexus
 */
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class SchEmployeeScheduleServiceImplTest {

    @Mock
    private SchEmployeeScheduleMapper baseMapper;
    @Mock
    private SchTeamMapper schTeamMapper;
    @Mock
    private SchTeamMemberMapper schTeamMemberMapper;
    @Mock
    private SchPlanMapper schPlanMapper;
    @Mock
    private SchTeamScheduleMapper schTeamScheduleMapper;
    @Mock
    private SchShiftMapper schShiftMapper;
    @InjectMocks
    private SchEmployeeScheduleServiceImpl service;

    private SchPlan plan(Long id, String status) {
        SchPlan p = new SchPlan();
        p.setPlanId(id);
        p.setPlanCode("PBJH2026000" + id);
        p.setStatus(status);
        p.setStartDate(new Date(0));
        p.setEndDate(new Date(86400000L));
        return p;
    }

    private SchTeamSchedule template(Long planId, Long teamId, Long shiftId, Date date, Integer isHoliday) {
        SchTeamSchedule t = new SchTeamSchedule();
        t.setPlanId(planId);
        t.setTeamId(teamId);
        t.setShiftId(shiftId);
        t.setScheduledDate(date);
        t.setIsHoliday(isHoliday);
        return t;
    }

    private SchTeamMember member(Long teamId, Long workerId, String name) {
        SchTeamMember m = new SchTeamMember();
        m.setTeamId(teamId);
        m.setWorkerId(workerId);
        m.setWorkerName(name);
        m.setStatus(1L);
        return m;
    }

    private SchShift shift(Long id, String name, boolean crossDay) {
        SchShift s = new SchShift();
        s.setShiftId(id);
        s.setShiftName(name);
        s.setIsCrossDay(crossDay ? 1 : 0);
        s.setStartTime(new Date(8 * 3600 * 1000L));            // 08:00:00
        s.setEndTime(new Date(crossDay ? 6 * 3600 * 1000L       // 跨天: 次日06:00
                                       : 16 * 3600 * 1000L));   // 当日16:00:00
        return s;
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateExpandsTeamScheduleTemplateExactly() {
        Long planId = 100L, teamA = 1L, teamB = 2L, morning = 10L, evening = 20L;
        // 模板: A组两天早班(日期带非零时刻验证截断), B组一天晚班(跨天)+一天休息
        Date d1 = new Date(8 * 3600 * 1000L);          // 1970-01-01 08:00:00
        Date d2 = new Date(86400000L + 3600 * 1000L);  // 1970-01-02 01:00:00
        List<SchTeamSchedule> templates = List.of(
            template(planId, teamA, morning, d1, 0),
            template(planId, teamA, morning, d2, 0),
            template(planId, teamB, evening, d1, 0),
            template(planId, teamB, null, d2, 1));
        when(schPlanMapper.selectById(planId)).thenReturn(plan(planId, "PUBLISHED"));
        when(schTeamScheduleMapper.selectList(any())).thenReturn(templates);
        when(schTeamMapper.selectList(any())).thenReturn(List.of());
        when(schTeamMemberMapper.selectList(any()))
            .thenReturn(List.of(member(teamA, 11L, "陈一飞"), member(teamA, 12L, "刘二娜"), member(teamB, 21L, "张三丰")));
        when(schShiftMapper.selectById(morning)).thenReturn(shift(morning, "早班", false));
        when(schShiftMapper.selectById(evening)).thenReturn(shift(evening, "晚班", true));
        when(baseMapper.insertBatch(anyList())).thenReturn(true);
        when(baseMapper.delete(any())).thenReturn(0);

        int count = service.generate(planId);

        // A组2人×2天=4 + B组1人×(1工作+1休息)=2 → 共6条
        assertEquals(6, count);
        // 清理仅执行一次且仅针对该计划(按计划范围删除, 不再全表清空)
        verify(baseMapper, times(1)).delete(any());
        ArgumentCaptor<List<SchEmployeeSchedule>> captor = ArgumentCaptor.forClass(List.class);
        verify(baseMapper).insertBatch(captor.capture());
        List<SchEmployeeSchedule> rows = captor.getValue();
        assertEquals(6, rows.size());

        // A组两天都早班且日期已截断到日(00:00)
        List<SchEmployeeSchedule> teamARows = rows.stream().filter(r -> r.getTeamId().equals(teamA)).toList();
        assertEquals(4, teamARows.size());
        assertTrue(teamARows.stream().allMatch(r -> morning.equals(r.getShiftId()) && "早班".equals(r.getShiftName())));
        assertTrue(teamARows.stream().allMatch(r -> r.getScheduledDate().getHours() == 0));

        // B组: 晚班(跨天结束时间落在次日), 休息行无班次无时间
        SchEmployeeSchedule bWork = rows.stream()
            .filter(r -> r.getTeamId().equals(teamB) && Integer.valueOf(1).equals(r.getIsWorkingDay())).findFirst().orElseThrow();
        assertEquals(evening, bWork.getShiftId());
        assertNotNull(bWork.getEndTime());
        SchEmployeeSchedule bRest = rows.stream()
            .filter(r -> r.getTeamId().equals(teamB) && Integer.valueOf(0).equals(r.getIsWorkingDay())).findFirst().orElseThrow();
        assertNull(bRest.getShiftId());
        assertNull(bRest.getStartTime());
        assertEquals("rest", bRest.getScheduleStatus());
    }

    @Test
    void generateSkipsDraftPlansAndPlansWithoutTemplate() {
        when(schPlanMapper.selectList(any())).thenReturn(List.of(
            plan(1L, "draft"),       // 草稿: 过滤
            plan(2L, "PUBLISHED"))); // 已发布但无模板: 跳过
        when(schTeamScheduleMapper.selectList(any())).thenReturn(List.of());
        when(schTeamMapper.selectList(any())).thenReturn(List.of());
        when(schTeamMemberMapper.selectList(any())).thenReturn(List.of(member(1L, 11L, "x")));

        int count = service.generate(null);

        assertEquals(0, count);
        // 已发布计划(2L)即使无模板也按范围清理旧明细; 草稿计划(1L)不清理
        verify(baseMapper, times(1)).delete(any());
        verify(baseMapper, never()).insertBatch(anyList());
    }

    @Test
    void generateSkipsPlanWithNullDates() {
        SchPlan p = plan(1L, "PUBLISHED");
        p.setStartDate(null);
        when(schPlanMapper.selectById(1L)).thenReturn(p);

        // 空日期计划被过滤(不抛 NPE), 返回 0
        assertEquals(0, service.generate(1L));
        verify(baseMapper, never()).insertBatch(anyList());
    }

    @Test
    void generateThrowsWhenPlanNotExist() {
        when(schPlanMapper.selectById(999L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.generate(999L));
    }
}
