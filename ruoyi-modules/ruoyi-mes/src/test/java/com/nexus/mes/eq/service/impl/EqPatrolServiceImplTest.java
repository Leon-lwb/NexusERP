package com.nexus.mes.eq.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.eq.domain.EqPatrolRecord;
import com.nexus.mes.eq.domain.EqPatrolTask;
import com.nexus.mes.eq.domain.bo.EqPatrolRecordBo;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.eq.mapper.EqPatrolRecordMapper;
import com.nexus.mes.eq.mapper.EqPatrolRouteMapper;
import com.nexus.mes.eq.mapper.EqPatrolTaskMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 巡检/点检任务与记录守卫单测（MES-EQ-10 / MES-EQ-11）。
 * <p>
 * 核心断言（修复前必失败）：
 * <ul>
 *   <li>已完成任务下的点检记录不可删除（此前 deleteRecordByIds 无任何校验）；</li>
 *   <li>batchSubmitRecords 必须校验**每一条**的 taskId，而不是只看 bos[0]。</li>
 * </ul>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class EqPatrolServiceImplTest {

    @Mock
    private EqPatrolRouteMapper routeMapper;
    @Mock
    private EqPatrolTaskMapper taskMapper;
    @Mock
    private EqPatrolRecordMapper recordMapper;
    @Mock
    private EqEquipmentMapper equipmentMapper;
    @InjectMocks
    private EqPatrolServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // deleteRecordByIds 使用 Wrappers.<EqPatrolRecord>lambdaQuery()，需要真实列缓存
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqPatrolRecord.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqPatrolTask.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(EqPatrolRecord.class);
        TableInfoHelper.remove(EqPatrolTask.class);
    }

    @Test
    void insertRecordRejectsCompletedTask() {
        EqPatrolTask task = new EqPatrolTask();
        task.setTaskId(1L);
        task.setStatus("COMPLETED");
        when(taskMapper.selectById(1L)).thenReturn(task);

        EqPatrolRecordBo bo = new EqPatrolRecordBo();
        bo.setTaskId(1L);
        bo.setEquipmentId(2L);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.insertRecordByBo(bo));

        assertTrue(exception.getMessage().contains("已结束"));
        verifyNoInteractions(routeMapper, recordMapper, equipmentMapper);
    }

    // ==================== MES-EQ-10: 已完成任务下不可删除 ====================

    @Test
    void deleteRecordRejectsRecordUnderCompletedTask() {
        EqPatrolRecord record = new EqPatrolRecord();
        record.setRecordId(100L);
        record.setTaskId(5L);
        when(recordMapper.selectList(any())).thenReturn(List.of(record));
        EqPatrolTask task = new EqPatrolTask();
        task.setTaskId(5L);
        task.setStatus("COMPLETED");
        when(taskMapper.selectById(5L)).thenReturn(task);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.deleteRecordByIds(new Long[]{100L}));

        assertTrue(exception.getMessage().contains("不可删除"));
        // 校验失败时不得发生物理删除
        verify(recordMapper, never()).deleteByIds(anyList());
    }

    @Test
    void deleteRecordRejectsRecordUnderCancelledTask() {
        EqPatrolRecord record = new EqPatrolRecord();
        record.setRecordId(100L);
        record.setTaskId(5L);
        when(recordMapper.selectList(any())).thenReturn(List.of(record));
        EqPatrolTask task = new EqPatrolTask();
        task.setTaskId(5L);
        task.setStatus("CANCELLED");
        when(taskMapper.selectById(5L)).thenReturn(task);

        assertThrows(ServiceException.class, () -> service.deleteRecordByIds(new Long[]{100L}));
        verify(recordMapper, never()).deleteByIds(anyList());
    }

    @Test
    void deleteRecordAllowsRecordUnderRunningTask() {
        EqPatrolRecord record = new EqPatrolRecord();
        record.setRecordId(100L);
        record.setTaskId(5L);
        when(recordMapper.selectList(any())).thenReturn(List.of(record));
        EqPatrolTask task = new EqPatrolTask();
        task.setTaskId(5L);
        task.setStatus("PENDING");
        when(taskMapper.selectById(5L)).thenReturn(task);
        when(recordMapper.deleteByIds(anyList())).thenReturn(1);

        assertTrue(service.deleteRecordByIds(new Long[]{100L}));
    }

    // ==================== MES-EQ-11: 批量提交逐条校验 taskId ====================

    @Test
    void batchSubmitRejectsAnyCompletedTaskIdNotJustFirst() {
        // 第 1 条挂未结束任务(1L)，第 2 条挂已完成任务(2L) —— 修复前只看 bos[0] 即可绕过守卫
        when(taskMapper.selectById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            EqPatrolTask task = new EqPatrolTask();
            task.setTaskId(id);
            task.setStatus(id == 2L ? "COMPLETED" : "PENDING");
            return task;
        });

        EqPatrolRecordBo first = new EqPatrolRecordBo();
        first.setTaskId(1L);
        first.setEquipmentId(9L);
        EqPatrolRecordBo second = new EqPatrolRecordBo();
        second.setTaskId(2L);
        second.setEquipmentId(9L);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.batchSubmitRecords(List.of(first, second)));

        assertTrue(exception.getMessage().contains("已结束"));
        // 关键：一条记录都不能写入
        verify(recordMapper, never()).insert(any(EqPatrolRecord.class));
    }

    @Test
    void batchSubmitRejectsMissingTaskId() {
        when(taskMapper.selectById(anyLong())).thenReturn(null);

        EqPatrolRecordBo bo = new EqPatrolRecordBo();
        bo.setTaskId(7L);
        bo.setEquipmentId(9L);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.batchSubmitRecords(List.of(bo)));

        assertTrue(exception.getMessage().contains("巡检任务不存在"));
        verify(recordMapper, never()).insert(any(EqPatrolRecord.class));
    }
}
