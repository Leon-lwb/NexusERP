package com.nexus.mes.eq.service.impl;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.eq.domain.EqPatrolTask;
import com.nexus.mes.eq.domain.bo.EqPatrolRecordBo;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.eq.mapper.EqPatrolRecordMapper;
import com.nexus.mes.eq.mapper.EqPatrolRouteMapper;
import com.nexus.mes.eq.mapper.EqPatrolTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
}
