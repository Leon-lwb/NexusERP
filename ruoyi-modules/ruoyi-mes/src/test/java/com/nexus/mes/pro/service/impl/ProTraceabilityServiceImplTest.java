package com.nexus.mes.pro.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.domain.ProWorkOrderBomLine;
import com.nexus.mes.pro.domain.vo.TraceabilityResult;
import com.nexus.mes.pro.mapper.ProSnMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderBomLineMapper;
import com.nexus.mes.pro.mapper.ProWorkOrderMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ProTraceabilityServiceImpl 组件追溯单元测试(MES-PRO-06)
 * <p>
 * 守护点: forward(组件SN) 必须沿 pro_sn.parent_sn_id 上行取"成品SN"的 mesWoId,
 * 而不是组件SN自身的 mesWoId(组件所在工单); 上行链放 parentSnChain, 下行件保持 assembledComponentSns。
 *
 * @author Nexus
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class ProTraceabilityServiceImplTest {

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProSn.class);
        TableInfoHelper.initTableInfo(assistant, ProWorkOrder.class);
        TableInfoHelper.initTableInfo(assistant, ProWorkOrderBomLine.class);
    }

    @Mock
    private ProWorkOrderMapper workOrderMapper;
    @Mock
    private ProWorkOrderBomLineMapper woBomLineMapper;
    @Mock
    private ProSnMapper snMapper;
    @InjectMocks
    private ProTraceabilityServiceImpl service;

    private ProSn sn(long snId, String snCode, Long mesWoId, Long parentSnId) {
        ProSn sn = new ProSn();
        sn.setSnId(snId);
        sn.setSnCode(snCode);
        sn.setMesWoId(mesWoId);
        sn.setParentSnId(parentSnId);
        return sn;
    }

    private ProWorkOrder wo(long mesWoId, String woNo) {
        ProWorkOrder wo = new ProWorkOrder();
        wo.setMesWoId(mesWoId);
        wo.setWoNo(woNo);
        wo.setMaterialId(200L);
        wo.setMaterialCode("M-200");
        return wo;
    }

    /**
     * 组件SN(父SN=成品SN) -> 返回成品工单, 而非组件自身工单
     */
    @Test
    void forward_componentSn_returnsFinishedWorkOrder() {
        ProSn component = sn(11L, "SN-CARD-001", 5L, 99L);
        ProSn finished = sn(99L, "SN-FIN-001", 7L, null);
        when(snMapper.selectOne(any())).thenReturn(component);
        when(snMapper.selectById(99L)).thenReturn(finished);
        when(workOrderMapper.selectById(7L)).thenReturn(wo(7L, "WO-FIN"));
        when(woBomLineMapper.selectVoList(any())).thenReturn(List.of());
        when(snMapper.selectList(any())).thenReturn(List.of());

        List<TraceabilityResult> results = service.forward(null, "SN-CARD-001");

        assertEquals(1, results.size());
        assertEquals(7L, results.get(0).getMesWoId(), "必须返回成品工单");
        assertEquals("WO-FIN", results.get(0).getWoNo());
        assertEquals(1, results.get(0).getParentSnChain().size());
        assertEquals("SN-FIN-001", results.get(0).getParentSnChain().get(0).getSnCode());
        // 组件自身工单(5)不再被当作消费工单
        verify(workOrderMapper, never()).selectById(5L);
    }

    /**
     * 多层装配链: 组件 -> 半成品 -> 成品, 上行链完整且取顶层成品工单
     */
    @Test
    void forward_multiLevelChain_resolvesTopLevelWorkOrder() {
        ProSn component = sn(11L, "SN-CARD-001", 5L, 22L);
        ProSn subAssembly = sn(22L, "SN-SUB-001", 6L, 33L);
        ProSn finished = sn(33L, "SN-FIN-001", 7L, null);
        when(snMapper.selectOne(any())).thenReturn(component);
        when(snMapper.selectById(22L)).thenReturn(subAssembly);
        when(snMapper.selectById(33L)).thenReturn(finished);
        when(workOrderMapper.selectById(7L)).thenReturn(wo(7L, "WO-FIN"));
        when(woBomLineMapper.selectVoList(any())).thenReturn(List.of());
        when(snMapper.selectList(any())).thenReturn(List.of());

        List<TraceabilityResult> results = service.forward(null, "SN-CARD-001");

        assertEquals(7L, results.get(0).getMesWoId());
        List<TraceabilityResult.SnInfo> chain = results.get(0).getParentSnChain();
        assertEquals(2, chain.size());
        assertEquals("SN-SUB-001", chain.get(0).getSnCode());
        assertEquals("SN-FIN-001", chain.get(1).getSnCode());
    }

    /**
     * 顶层SN(无父SN)无装配关系: 以自身工单归属, 上行链为空
     */
    @Test
    void forward_topLevelSnWithoutParent_usesOwnWorkOrder() {
        ProSn top = sn(21L, "SN-FIN-002", 9L, null);
        when(snMapper.selectOne(any())).thenReturn(top);
        when(workOrderMapper.selectById(9L)).thenReturn(wo(9L, "WO-TOP"));
        when(woBomLineMapper.selectVoList(any())).thenReturn(List.of());
        when(snMapper.selectList(any())).thenReturn(List.of());

        List<TraceabilityResult> results = service.forward(null, "SN-FIN-002");

        assertEquals(1, results.size());
        assertEquals(9L, results.get(0).getMesWoId());
        assertTrue(results.get(0).getParentSnChain().isEmpty());
    }

    /**
     * 组件SN不存在 -> 空结果(不抛异常)
     */
    @Test
    void forward_unknownSnCode_returnsEmpty() {
        when(snMapper.selectOne(any())).thenReturn(null);

        assertTrue(service.forward(null, "SN-NOT-EXIST").isEmpty());
    }

    /**
     * 物料维度正向追溯: 按 MBOM 快照 child_material_id 找到消费该组件的工单
     */
    @Test
    void forward_componentMaterial_returnsConsumingWorkOrders() {
        ProWorkOrderBomLine line = new ProWorkOrderBomLine();
        line.setMesWoId(7L);
        when(woBomLineMapper.selectList(any())).thenReturn(List.of(line));
        when(workOrderMapper.selectById(7L)).thenReturn(wo(7L, "WO-FIN"));
        when(woBomLineMapper.selectVoList(any())).thenReturn(List.of());
        when(snMapper.selectList(any())).thenReturn(List.of());

        List<TraceabilityResult> results = service.forward(201L, null);

        assertEquals(1, results.size());
        assertEquals(7L, results.get(0).getMesWoId());
        assertTrue(results.get(0).getParentSnChain().isEmpty());
    }
}
