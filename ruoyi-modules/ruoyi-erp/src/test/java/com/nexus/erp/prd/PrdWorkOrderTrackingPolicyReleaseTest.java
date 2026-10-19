package com.nexus.erp.prd;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.erp.bd.domain.vo.BdBomVo;
import com.nexus.erp.bd.mapper.BdRoutingMapper;
import com.nexus.erp.bd.service.IBdBomService;
import com.nexus.erp.prd.domain.*;
import com.nexus.erp.prd.mapper.*;
import com.nexus.erp.prd.service.impl.PrdWorkOrderServiceImpl;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.mapper.WmProductionIssueMapper;
import org.junit.jupiter.api.*;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the existing source release method, including its post-commit publication callback. */
class PrdWorkOrderTrackingPolicyReleaseTest {
    @BeforeAll static void metadata() {PrdWorkOrderServiceImplTest.initTableInfo();}
    @Test void enabledReleaseFreezesItsActualPoliciesBeforePublishingTheCommittedOrder() throws Exception {
        try(var f=new Fixture(true)) {
            f.db.tx.execute(s->{f.orders.release(1L);assertEquals(3,f.db.count());verifyNoInteractions(f.messages);return null;});
            assertEquals("RELEASED",f.status());assertEquals(3,f.db.count());
            assertTrue(f.db.service.find(PrdWorkOrderTrackingPolicyServiceTest.Fixture.TENANT,1L,20L).originalRelease());
            verify(f.messages,times(1)).send(anyString(),anyString(),any());
        }
    }
    @Test void failedAtomicClaimRollsBackAllFrozenPoliciesAndCannotPublish() throws Exception {
        try(var f=new Fixture(true)) {
            when(f.mapper.update(isNull(),any(LambdaUpdateWrapper.class))).thenReturn(0);
            assertThrows(RuntimeException.class,()->f.db.tx.execute(s->{f.orders.release(1L);return null;}));
            assertEquals(0,f.db.count());assertEquals("CREATED",f.status());verifyNoInteractions(f.messages);
        }
    }
    @Test void aLaterProductionIssueFailureRollsBackTheSnapshotAndReleaseState() throws Exception {
        try(var f=new Fixture(true)) {
            doThrow(new IllegalStateException("issue source unavailable")).when(f.issues).insert(any(WmProductionIssue.class));
            assertThrows(RuntimeException.class,()->f.db.tx.execute(s->{f.orders.release(1L);return null;}));
            assertEquals(0,f.db.count());assertEquals("CREATED",f.status());verifyNoInteractions(f.messages);
        }
    }
    @Test void enabledReleaseCannotPublishWhenAnyRequiredMaterialPolicyIsMissing() throws Exception {
        try(var f=new Fixture(true)) {
            f.db.jdbc.update("DELETE FROM bd_material WHERE material_id=30");
            assertThrows(RuntimeException.class,()->f.db.tx.execute(s->{f.orders.release(1L);return null;}));
            assertEquals(0,f.db.count());assertEquals("CREATED",f.status());verifyNoInteractions(f.messages);
        }
    }
    @Test void aSourceEditAfterTheInitialReadCannotFreezeOneProductAndPublishAnother() throws Exception {
        try(var f=new Fixture(true)) {
            // The service's previously selected WO still names product10; its actual locked source now names11.
            f.db.jdbc.update("UPDATE prd_work_order SET material_id=11");
            f.db.jdbc.update("UPDATE bd_bom SET parent_material_id=11");
            f.db.jdbc.update("INSERT INTO bd_material VALUES(11,?,1,1,0,9,'STRICT',1,'0')",PrdWorkOrderTrackingPolicyServiceTest.Fixture.TENANT);
            assertThrows(RuntimeException.class,()->f.db.tx.execute(s0->{f.orders.release(1L);return null;}));
            assertEquals(0,f.db.count());assertEquals("CREATED",f.status());verifyNoInteractions(f.messages);
        }
    }
    @Test void explicitlyDisabledRelayKeepsTheExistingNonTraceReleaseAdapter() throws Exception {
        try(var f=new Fixture(false)) {
            f.db.jdbc.execute("DROP TABLE wm_work_order_tracking_policy");
            f.db.tx.execute(s->{f.orders.release(1L);return null;});assertEquals("RELEASED",f.status());
            verify(f.messages,times(1)).send(anyString(),anyString(),any());
        }
    }
    static final class Fixture implements AutoCloseable {
        final PrdWorkOrderTrackingPolicyServiceTest.Fixture db=new PrdWorkOrderTrackingPolicyServiceTest.Fixture();
        final PrdWorkOrderServiceImplTest existing=new PrdWorkOrderServiceImplTest();
        final AutoCloseable mocks;final PrdWorkOrderServiceImpl orders;final PrdWorkOrderMapper mapper;
        final ReliableMessageProducer messages;final WmProductionIssueMapper issues;
        Fixture(boolean enabled) throws Exception {
            AutoCloseable opened=null;
            try {
                opened=MockitoAnnotations.openMocks(existing);mocks=opened;
                orders=(PrdWorkOrderServiceImpl)ReflectionTestUtils.getField(existing,"workOrderService");
                mapper=(PrdWorkOrderMapper)ReflectionTestUtils.getField(existing,"baseMapper");
                messages=(ReliableMessageProducer)ReflectionTestUtils.getField(existing,"messageProducer");
                issues=(WmProductionIssueMapper)ReflectionTestUtils.getField(existing,"productionIssueMapper");
                ReflectionTestUtils.setField(orders,"trackingPolicies",db.service);
                ReflectionTestUtils.setField(orders,"traceRelayEnabled",enabled);
                var wo=new PrdWorkOrder();wo.setWoId(1L);wo.setWoNo("WO1");wo.setTenantId(PrdWorkOrderTrackingPolicyServiceTest.Fixture.TENANT);
                wo.setWoStatus("CREATED");wo.setMaterialId(10L);wo.setBomId(100L);wo.setBomVersion("B1");wo.setPlanId(500L);wo.setPlannedQty(java.math.BigDecimal.ONE);
                wo.setSourceType("MATERIAL_REQUIREMENT");wo.setSourceId(200L);wo.setBackflushFlag(0);
                when(mapper.selectById(1L)).thenReturn(wo);
                when(mapper.update(isNull(),any(LambdaUpdateWrapper.class))).thenAnswer(a->db.jdbc.update("UPDATE prd_work_order SET wo_status='RELEASED' WHERE wo_id=1 AND wo_status='CREATED'"));
                var req=new PrdMaterialRequirement();req.setRequirementId(200L);req.setTenantId(wo.getTenantId());req.setPlanId(500L);
                var reqMapper=(PrdMaterialRequirementMapper)ReflectionTestUtils.getField(existing,"requirementMapper");when(reqMapper.selectById(200L)).thenReturn(req);
                var routing=mock(BdRoutingMapper.class);when(routing.selectList(any())).thenReturn(List.of());ReflectionTestUtils.setField(orders,"bdRoutingMapper",routing);
                var bom=(IBdBomService)ReflectionTestUtils.getField(existing,"bomService");var header=new BdBomVo();header.setBomId(100L);header.setParentMaterialId(10L);when(bom.selectById(100L)).thenReturn(header);when(bom.explodeBom(100L)).thenReturn(List.of());
                when(issues.insert(any(WmProductionIssue.class))).thenAnswer(a->{((WmProductionIssue)a.getArgument(0)).setIssueId(900L);return 1;});
            } catch(Exception|Error error) {if(opened!=null)opened.close();db.close();throw error;}
        }
        String status() {return db.jdbc.queryForObject("SELECT wo_status FROM prd_work_order WHERE wo_id=1",String.class);}
        public void close() throws Exception {try{mocks.close();}finally{db.close();}}
    }
}
