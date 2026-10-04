package com.nexus.erp.prd;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.message.WorkOrderReleaseMessage;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.erp.bd.domain.BdRouting;
import com.nexus.erp.bd.domain.vo.BdBomVo;
import com.nexus.erp.bd.mapper.BdRoutingMapper;
import com.nexus.erp.bd.service.IBdBomService;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.service.impl.PrdWorkOrderServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 只替换数据库和MQ边界，直接执行真实下达方法及其提交后消息映射。 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PrdWorkOrderReleaseRoutingTest {

    private static final long WO_ID = 41L;
    private static final long MATERIAL_ID = 201L;
    private static final long BOM_ID = 301L;
    private static final long ROUTING_ID = 501L;
    private static final String TENANT_ID = "1001";

    @Mock
    private PrdWorkOrderMapper baseMapper;
    @Mock
    private IBdBomService bomService;
    @Mock
    private BdRoutingMapper bdRoutingMapper;
    @Mock
    private ReliableMessageProducer messageProducer;
    @InjectMocks
    private PrdWorkOrderServiceImpl service;

    private PrdWorkOrder workOrder;
    private BdBomVo bom;
    private BdRouting routing;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PrdWorkOrder.class);
        TableInfoHelper.initTableInfo(assistant, BdRouting.class);
    }

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        workOrder = new PrdWorkOrder();
        workOrder.setWoId(WO_ID);
        workOrder.setWoNo("WO-ROUTING-41");
        workOrder.setWoStatus("CREATED");
        workOrder.setTenantId(TENANT_ID);
        workOrder.setMaterialId(MATERIAL_ID);
        workOrder.setBomId(BOM_ID);
        workOrder.setPlannedQty(BigDecimal.TEN);
        // PLAN来源只隔离领料生成，仍执行真实release、CAS与提交后发送路径。
        workOrder.setSourceType("PLAN");
        bom = new BdBomVo();
        bom.setBomId(BOM_ID);
        bom.setParentMaterialId(MATERIAL_ID);
        bom.setRoutingId(ROUTING_ID);
        routing = dedicatedRouting(ROUTING_ID);
        lenient().when(baseMapper.selectById(WO_ID)).thenReturn(workOrder);
        lenient().when(baseMapper.update(isNull(), any())).thenReturn(1);
        lenient().when(bomService.selectById(BOM_ID)).thenReturn(bom);
        lenient().when(bomService.explodeBom(BOM_ID)).thenReturn(List.of());
        lenient().when(bdRoutingMapper.selectList(any())).thenReturn(List.of(routing));
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void release_sendsTrustedBomRoutingResolvedBeforeCommit() {
        service.release(WO_ID);
        assertEquals("RELEASED", workOrder.getWoStatus());
        verify(messageProducer, never()).send(any(), any(), any());
        // 修改回读对象证明消息使用提交前固定ID，而不是提交后重新选择路线。
        bom.setRoutingId(999L);
        routing.setRoutingId(999L);
        WorkOrderReleaseMessage message = commitAndCapture();
        assertEquals(ROUTING_ID, message.getRoutingId(), "真实下达消息必须携带可信专属路线ID");
        assertEquals(WO_ID, message.getWoId());
        assertEquals(MATERIAL_ID, message.getMaterialId());
        assertEquals(BOM_ID, message.getBomId());
        assertEquals(1001L, message.getTenantId());
        verify(bomService).selectById(BOM_ID);
        verify(bdRoutingMapper).selectList(any());
    }

    @Test
    void release_withUnboundBomUsesUniqueCurrentMaterialRouting() {
        bom.setRoutingId(null);
        service.release(WO_ID);
        assertEquals(ROUTING_ID, commitAndCapture().getRoutingId());
    }

    @Test
    void release_withoutBomUsesUniqueCurrentMaterialRouting() {
        workOrder.setBomId(null);
        service.release(WO_ID);
        assertEquals(ROUTING_ID, commitAndCapture().getRoutingId());
        verify(bomService, never()).selectById(any());
    }

    @Test
    void release_withoutDedicatedRoutingRetainsLegacyNullRouting() {
        bom.setRoutingId(null);
        lenient().when(bdRoutingMapper.selectList(any())).thenReturn(List.of());
        service.release(WO_ID);
        assertNull(commitAndCapture().getRoutingId(), "无专属路线时不泛化拒绝历史下达或借用通用路线");
        assertEquals("RELEASED", workOrder.getWoStatus());
    }

    @Test
    void release_withoutMaterialRetainsLegacyReleaseWithoutSelectingAnotherMaterial() {
        workOrder.setMaterialId(null);
        service.release(WO_ID);
        assertNull(commitAndCapture().getRoutingId());
        verify(bdRoutingMapper, never()).selectList(any());
    }

    @Test
    void release_withoutTenantDoesNotInventDedicatedRouting() {
        workOrder.setTenantId(null);
        service.release(WO_ID);
        assertEquals("RELEASED", workOrder.getWoStatus());
        verify(bdRoutingMapper, never()).selectList(any());
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        // 不触发无租户历史消息的登录上下文兜底，测试只核实不猜路线。
    }

    @Test
    void release_rejectsBomForAnotherMaterialBeforeStateClaim() {
        bom.setParentMaterialId(202L);
        rejectBeforeClaim("BOM与工单物料不一致");
    }

    @Test
    void release_rejectsMissingBomBeforeStateClaim() {
        lenient().when(bomService.selectById(BOM_ID)).thenReturn(null);
        rejectBeforeClaim("BOM不存在");
    }

    @Test
    void release_rejectsUnexpectedBomIdentityBeforeStateClaim() {
        bom.setBomId(302L);
        rejectBeforeClaim("BOM关联不一致");
    }

    @Test
    void release_rejectsExplicitBomRoutingWithoutEnabledCurrentDedicatedRouting() {
        lenient().when(bdRoutingMapper.selectList(any())).thenReturn(List.of());
        rejectBeforeClaim("BOM绑定路线");
    }

    @Test
    void release_rejectsBomRoutingDifferentFromCurrentDedicatedRouting() {
        bom.setRoutingId(999L);
        rejectBeforeClaim("BOM绑定路线");
    }

    @Test
    void release_rejectsMultipleCurrentDedicatedRoutingsInsteadOfChoosingLatest() {
        lenient().when(bdRoutingMapper.selectList(any())).thenReturn(List.of(routing, dedicatedRouting(502L)));
        rejectBeforeClaim("多个");
    }

    @ParameterizedTest(name = "不可信路线不得下达：{0}")
    @ValueSource(strings = {"wrong-material", "generic-material", "disabled", "not-current",
        "deleted", "wrong-tenant", "missing-id", "zero-id"})
    void release_rejectsUntrustedRoutingRowsBeforeStateClaim(String invalidCase) {
        switch (invalidCase) {
            case "wrong-material" -> routing.setMaterialId(202L);
            case "generic-material" -> routing.setMaterialId(null);
            case "disabled" -> routing.setStatus(0L);
            case "not-current" -> routing.setIsCurrent(0L);
            case "deleted" -> routing.setDelFlag("1");
            case "wrong-tenant" -> routing.setTenantId("1002");
            case "missing-id" -> routing.setRoutingId(null);
            case "zero-id" -> routing.setRoutingId(0L);
            default -> throw new IllegalArgumentException(invalidCase);
        }
        rejectBeforeClaim("专属路线");
    }

    @Test
    @SuppressWarnings("unchecked")
    void release_queriesOnlyPersistedWorkOrderMaterialTenantAndEnabledCurrentRows() {
        service.release(WO_ID);
        ArgumentCaptor<LambdaQueryWrapper<BdRouting>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(bdRoutingMapper).selectList(captor.capture());
        LambdaQueryWrapper<BdRouting> query = captor.getValue();
        String sql = query.getSqlSegment();
        assertTrue(sql.contains("material_id") && sql.contains("tenant_id"), sql);
        assertTrue(sql.contains("status") && sql.contains("is_current") && sql.contains("del_flag"), sql);
        assertTrue(query.getParamNameValuePairs().containsValue(MATERIAL_ID));
        assertTrue(query.getParamNameValuePairs().containsValue(TENANT_ID));
        assertTrue(query.getParamNameValuePairs().containsValue(1L));
        assertTrue(query.getParamNameValuePairs().containsValue("0"));
    }

    private WorkOrderReleaseMessage commitAndCapture() {
        assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
        ArgumentCaptor<WorkOrderReleaseMessage> captor = ArgumentCaptor.forClass(WorkOrderReleaseMessage.class);
        verify(messageProducer).send(eq(ExchangeConstants.TOPIC_EXCHANGE),
            eq(RoutingKeyConstants.MES_WORKORDER_RELEASE), captor.capture());
        return captor.getValue();
    }

    private void rejectBeforeClaim(String messageFragment) {
        RuntimeException error = assertThrows(RuntimeException.class, () -> service.release(WO_ID));
        assertTrue(error.getMessage().contains(messageFragment), error.getMessage());
        assertEquals("CREATED", workOrder.getWoStatus());
        verify(baseMapper, never()).update(isNull(), any());
        verify(messageProducer, never()).send(any(), any(), any());
        assertTrue(TransactionSynchronizationManager.getSynchronizations().isEmpty());
    }

    private BdRouting dedicatedRouting(Long id) {
        BdRouting result = new BdRouting();
        result.setRoutingId(id);
        result.setMaterialId(MATERIAL_ID);
        result.setTenantId(TENANT_ID);
        result.setStatus(1L);
        result.setIsCurrent(1L);
        result.setDelFlag("0");
        return result;
    }
}
