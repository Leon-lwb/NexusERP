package com.nexus.erp.pu;

import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.pu.domain.vo.PuCertifiedPurchaseResult;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseOrderWorkflowMapper;
import com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 原独立审查反例转为真实领域入口断言，不连接服务或数据库。 */
class PuCertifiedPurchaseOriginalReceiptReviewRegressionTest {
    private static final String T="000001";
    private static final DecisionActor ACTOR=new DecisionActor(T,901L,"离线核验员",Set.of("ai:decision:execute","pu:requisition:add"));

    @ParameterizedTest @ValueSource(strings={"draftMoney","terminalMoney","draftFreight","terminalFreight","draftCurrency","terminalRate","draftPayment","terminalDelivery","draftMissingMoney","terminalDuplicateMoney"})
    void r1UnpricedOrderMustCoverAllActualMoneyAndTerms(String kind) throws Exception {
        try(var f=new PuCertifiedPurchaseOriginalReceiptTest.Fixture()) {
            f.advance(2);if(kind.startsWith("terminal"))f.endOrder(Operation.CLOSE_ORDER);
            switch(kind) {
                case "draftMoney","terminalMoney" -> when(f.nativeMapper.lockMoneyLines(T,801L)).thenReturn(List.of(new PuCertifiedPurchaseOrderWorkflowMapper.LineMoney(T,701L,801L,new BigDecimal("11.3"),BigDecimal.TEN,new BigDecimal("0.13"),new BigDecimal("113"),new BigDecimal("100"),new BigDecimal("13"),BigDecimal.ONE)));
                case "draftFreight","terminalFreight" -> when(f.nativeMapper.lockHead(T,801L)).thenReturn(replace(f.nativeHead(),Map.of("freightAmount",BigDecimal.ONE)));
                case "draftCurrency" -> when(f.nativeMapper.lockHead(T,801L)).thenReturn(replace(f.nativeHead(),Map.of("currency","USD")));
                case "terminalRate" -> when(f.nativeMapper.lockHead(T,801L)).thenReturn(replace(f.nativeHead(),Map.of("exchangeRate",new BigDecimal("2"))));
                case "draftPayment" -> when(f.nativeMapper.lockHead(T,801L)).thenReturn(replace(f.nativeHead(),Map.of("paymentTerms","被篡改的条款")));
                case "terminalDelivery" -> when(f.nativeMapper.lockHead(T,801L)).thenReturn(replace(f.nativeHead(),Map.of("deliveryTerms","被篡改的条款")));
                case "draftMissingMoney" -> when(f.nativeMapper.lockMoneyLines(T,801L)).thenReturn(List.of());
                case "terminalDuplicateMoney" -> {var money=f.nativeMapper.lockMoneyLines(T,801L).get(0);when(f.nativeMapper.lockMoneyLines(T,801L)).thenReturn(List.of(money,money));}
                default -> throw new IllegalArgumentException("未知未定价反例");
            }
            rejected(f,"R1 完整未定价事实缺失不能认证："+kind);
        }
    }

    @ParameterizedTest @ValueSource(strings={"terminalGap","approvalGap"})
    void r2RecomputedDigestsCannotFillMissingNativeCertifiedVersions(String kind) throws Exception {
        try(var f=new PuCertifiedPurchaseOriginalReceiptTest.Fixture()) {
            f.advance("terminalGap".equals(kind)?6:3);
            if("terminalGap".equals(kind)){f.nativeVersion=9;f.nativeTerminal=f.freezeNative(Operation.CLOSE_ORDER,8);}
            else {f.nativeVersion=3;f.nativeApproval=f.freezeNative(Operation.APPROVE_ORDER,2);}
            rejected(f,"R2 缺失合法版本不能用重算摘要代替："+kind);
        }
    }

    @ParameterizedTest @ValueSource(strings={"APPROVE","TRANSFER_TO_ORDER"})
    void r3SingleLineChangedActorCannotAuthenticateAnOriginalWholeSubmission(String operation) throws Exception {
        try(var f=new PuCertifiedPurchaseOriginalReceiptTest.Fixture()) {
            f.advance(3);f.physical.history.stream().filter(e->operation.equals(e.getEventType())).findFirst().orElseThrow().setActorId(999L);
            rejected(f,"R3 单行整申请原提交人必须由独立事实认证："+operation);
        }
    }

    @ParameterizedTest @ValueSource(strings={"APPROVE","TRANSFER_TO_ORDER"})
    void r3MixedActorsAcrossWholeRequisitionMustFail(String operation) throws Exception {
        try(var f=new PuCertifiedPurchaseOriginalReceiptTest.Fixture()) {
            var factory=PuCertifiedPurchaseOriginalReceiptLifecycleTest.class.getDeclaredMethod("twoLines",PuCertifiedPurchaseOriginalReceiptTest.Fixture.class,String.class);factory.setAccessible(true);
            var current=(PuCertifiedPurchaseResult)factory.invoke(null,f,"PARTIALLY_RECEIVED");
            f.physical.events.lockByAllocation(T,102L).stream().filter(e->operation.equals(e.getEventType())).findFirst().orElseThrow().setActorId(999L);f.clearWrites();
            assertThrows(ServiceException.class,()->f.physical.service.verifyOriginalCreationLocked(T,201L,f.key,current),"R3 混合 actor 不可能来自一个原生整单 CAS："+operation);f.assertNoWrites();
        }
    }

    @ParameterizedTest @ValueSource(strings={"pricedBeforeApproval","unpricedBeforeCreate","pricedBeforeTerminalEvent"})
    void r4NativeTerminalMustFollowItsCausalPhysicalHistory(String kind) throws Exception {
        try(var f=new PuCertifiedPurchaseOriginalReceiptTest.Fixture()) {
            f.advance(kind.startsWith("unpriced")?2:6);if(kind.startsWith("unpriced"))f.endOrder(Operation.CLOSE_ORDER);
            Date previous=kind.startsWith("unpriced")?f.physical.history.get(0).getOccurredAt():f.nativeApproval.occurredAt();
            if("pricedBeforeTerminalEvent".equals(kind)) {
                Date later=new Date(previous.getTime()+2000);f.physical.row.setReleasedAt(later);
                f.physical.history.stream().filter(e->"CLOSE_ORDER".equals(e.getEventType())).forEach(e->e.setOccurredAt(later));
                when(f.physical.mapper.databaseNow()).thenReturn(new Date(previous.getTime()+3000));
                f.nativeTerminal=replace(f.nativeTerminal,Map.of("occurredAt",new Date(previous.getTime()+1000)));
            } else f.nativeTerminal=replace(f.nativeTerminal,Map.of("occurredAt",new Date(previous.getTime()-1000)));
            rejected(f,"R4 终态原生命令不能早于原创建、批准或对应事件："+kind);
        }
    }

    @ParameterizedTest @ValueSource(strings={"DRAFT","CLOSED","CANCELLED"})
    void r1LegitimateNullOrZeroUnpricedFactsStillRetainTheOriginalEnvelope(String state) throws Exception {
        try(var f=new PuCertifiedPurchaseOriginalReceiptTest.Fixture()) {
            f.advance(2);if(!"DRAFT".equals(state))f.endOrder("CLOSED".equals(state)?Operation.CLOSE_ORDER:Operation.CANCEL_ORDER);
            when(f.nativeMapper.lockMoneyLines(T,801L)).thenReturn(List.of(new PuCertifiedPurchaseOrderWorkflowMapper.LineMoney(T,701L,801L,null,null,null,null,null,null,null)));
            f.clearWrites();assertSame(f.original,f.adapter.verify(f.action,f.original,ACTOR),"真实未定价零/空事实与合法终态不应改变历史信封");
            assertTrue(f.adapter.recover(f.action,ACTOR).success(),"真实未定价原创建必须可恢复");verify(f.nativeMapper,atLeastOnce()).lockMoneyLines(T,801L);f.assertNoWrites();
        }
    }

    @Test void r3DifferentApprovalTransferAndTerminalActorsAreLegitimate() throws Exception {
        try(var f=new PuCertifiedPurchaseOriginalReceiptTest.Fixture()) {
            f.approveAs(902L);f.clearWrites();assertSame(f.original,f.adapter.verify(f.action,f.original,ACTOR),"审批人可不同于创建人，需绑定保留的真实行 audit");
            f.transferAs(903L);f.orderActor=904L;f.orderStatus="APPROVED";f.orderUpdatedBy=904L;f.applyOrderAs(Operation.APPROVE_ORDER,904L);f.priced=true;f.nativeApproval=f.freezeNative(Operation.APPROVE_ORDER,0);
            f.clearWrites();assertSame(f.original,f.adapter.verify(f.action,f.original,ACTOR),"审批/转单/订单批准人可以合法不同");
            f.endOrderAs(Operation.CLOSE_ORDER,905L);f.clearWrites();assertTrue(f.adapter.recover(f.action,ACTOR).success(),"合法不同关闭人及连续 0→1→2 版本仍可恢复原创建");
            assertSame(f.original,f.adapter.verify(f.action,f.original,ACTOR),"合法全生命周期不得重新赋予旧结果结论");f.assertNoWrites();
        }
    }

    @ParameterizedTest @ValueSource(strings={"approvalActorMissing","headCasMissing","orderCreatorMissing"})
    void r3MissingIndependentAuditNeverDefaultsToTheEventActor(String missing) throws Exception {
        try(var f=new PuCertifiedPurchaseOriginalReceiptTest.Fixture()) {
            f.advance(3);
            if("approvalActorMissing".equals(missing))f.approvalUpdatedBy=null;
            else if("headCasMissing".equals(missing))f.requisitionUpdatedBy=null;
            else when(f.physical.mapper.lockOrderStateAudit(T,801L)).thenReturn(null);
            rejected(f,"原生独立 audit 缺失必须拒绝，不能猜创建人或复制事件："+missing);
        }
    }

    @Test void r4SqlDefaultAuditsAndNativeMillisecondsKeepTheirOwnSourcesWithoutChangingHashes() throws Exception {
        try(var f=new PuCertifiedPurchaseOriginalReceiptTest.Fixture()) {
            f.advance(6);long epoch=f.nativeApproval.occurredAt().getTime();var digest=f.nativeTerminal.resultFingerprint();
            // 这四列由原生NOW()/DEFAULT写入，只保留floor秒；不得填参数Date的小数表示。
            f.requisitionUpdatedAt=new Date(epoch);f.approvalUpdatedAt=new Date(epoch);f.orderCreatedAt=new Date(epoch);f.orderUpdatedAt=new Date(epoch);
            f.nativeTerminal=replace(f.nativeTerminal,Map.of("occurredAt",new Date(epoch+500)));when(f.physical.mapper.databaseNow()).thenReturn(new Date(epoch+900));f.clearWrites();
            assertEquals(digest,f.nativeTerminal.resultFingerprint(),"精度适配不得更换旧原生结果摘要");
            assertSame(f.original,f.adapter.verify(f.action,f.original,ACTOR),"SQL默认审计秒与原生命令毫秒分别认证，不使用浮点容差");
            assertTrue(f.adapter.recover(f.action,ACTOR).success(),"同秒兼容应保留合法原回执");f.assertNoWrites();
        }
    }

    @Test void r5RealExecutorAndMandatoryLifecycleProxyRejectTheVersionGapWithoutDatabase() throws Exception {
        try(var f=new PuCertifiedPurchaseOriginalReceiptTest.Fixture()) {
            f.advance(3);f.clearWrites();
            var type=Class.forName("com.nexus.erp.pu.PuRequirementErpTransactionExecutorTest$Fixture");var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);var tx=constructor.newInstance();
            var factory=(org.apache.ibatis.session.SqlSessionFactory)field(tx,"sessionFactory");var routing=(javax.sql.DataSource)field(tx,"routing");
            var jdbc=(java.sql.Connection)field(field(tx,"erpJdbc"),"connection");var isolation=new java.util.concurrent.atomic.AtomicInteger(java.sql.Connection.TRANSACTION_READ_COMMITTED);
            when(jdbc.getTransactionIsolation()).thenAnswer(a->isolation.get());doAnswer(a->{isolation.set(a.getArgument(0));return null;}).when(jdbc).setTransactionIsolation(org.mockito.ArgumentMatchers.anyInt());
            var executor=new com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor(factory);var qualification=new com.nexus.erp.pu.service.PuRequirementErpTransactionQualification(factory);
            var target=new com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService(executor,qualification,f.physical.locks,f.physical.guards,f.physical.ledgers,f.physical.events,f.physical.mapper);
            var proxy=new org.springframework.aop.framework.ProxyFactory(target);proxy.setProxyTargetClass(true);
            proxy.addAdvisor(new com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationAdvisor(new com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationInterceptor(true,null),com.baomidou.dynamic.datasource.annotation.DS.class));
            proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(new org.springframework.jdbc.datasource.DataSourceTransactionManager(routing),new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
            var lifecycle=(com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService)proxy.getProxy();
            var workflow=new com.nexus.erp.pu.service.PuCertifiedPurchaseOrderWorkflowService(executor,qualification,f.physical.locks,f.nativeMapper,f.physical.mapper,lifecycle,f.certification);
            var writer=new com.nexus.erp.pu.service.PuCertifiedPurchaseWriteService(mock(com.nexus.erp.pu.service.PuCertifiedPurchaseAuthorizer.class),executor,qualification,f.physical.locks,f.certification,f.write,f.physical.ledgers,f.physical.events,f.numbers,f.physical.mapper,f.scope,lifecycle,workflow,f.nativeMapper);
            var adapter=new com.nexus.erp.ai.adapter.ErpCertifiedPurchaseDecisionAdapter(writer);
            assertTrue(org.springframework.aop.support.AopUtils.isCglibProxy(lifecycle),"生命周期必须通过真实 Spring 代理调用 MANDATORY");
            assertSame(f.original,adapter.verify(f.action,f.original,ACTOR),"真实 executor/代理与 mock JDBC 正向必须保持原信封");
            f.physical.row.setVersion(5);
            assertAll("真实组合拒绝缺失账本历史",()->assertThrows(ServiceException.class,()->adapter.verify(f.action,f.original,ACTOR),"真实组合不能认证版本跳跃"),
                ()->assertThrows(ServiceException.class,()->adapter.recover(f.action,ACTOR),"真实组合不能投影不完整历史"));
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(),"退出后必须清理真实事务");
            assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.getResourceMap().isEmpty(),"退出后必须清理连接资源");
            assertNull(com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder.peek(),"退出后必须清理路由");
            verify((javax.sql.DataSource)field(tx,"master"),never()).getConnection();f.assertNoWrites();
        }
    }
    private static Object field(Object object,String name) throws Exception {var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}

    @Test void r3AuditProjectionsReadOnlyExistingNativeColumnsWithinTenantAndParent() throws Exception {
        var mapper=com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper.class;
        for(String name:List.of("lockRequisitionStateAudit","lockRequisitionApprovalAudits","lockOrderStateAudit")) {
            var method=mapper.getMethod(name,String.class,Long.class);var select=method.getAnnotation(org.apache.ibatis.annotations.Select.class);
            assertNotNull(select,"独立 audit 必须是只读原生 SELECT 投影");String sql=String.join(" ",select.value());
            assertTrue(sql.startsWith("SELECT "),"不得以新投影执行写入或修改 schema");
            assertTrue(sql.contains("tenant_id=#{tenantId}")&&sql.contains("FOR UPDATE"),"必须保留真实租户父绑定与当前锁定读");
            assertTrue(sql.contains("update_by")&&sql.contains("update_time"),"必须读取独立旧 actor 和对应 CAS 审计事实");
        }
    }

    @ParameterizedTest @ValueSource(strings={"beforeApprovalMillis","futureUnpricedMillis"})
    void r4NativeDatetimeThreePairsMustNotLoseSubsecondCausality(String kind) throws Exception {
        try(var f=new PuCertifiedPurchaseOriginalReceiptTest.Fixture()) {
            f.advance("beforeApprovalMillis".equals(kind)?6:2);if(kind.startsWith("future"))f.endOrder(Operation.CLOSE_ORDER);
            long epoch=f.nativeTerminal.occurredAt().getTime();
            if("beforeApprovalMillis".equals(kind)) {
                f.nativeApproval=replace(f.nativeApproval,Map.of("occurredAt",new Date(epoch+700)));
                f.nativeTerminal=replace(f.nativeTerminal,Map.of("occurredAt",new Date(epoch+500)));when(f.physical.mapper.databaseNow()).thenReturn(new Date(epoch+900));
            } else {
                f.nativeTerminal=replace(f.nativeTerminal,Map.of("occurredAt",new Date(epoch+900)));when(f.physical.mapper.databaseNow()).thenReturn(new Date(epoch+100));
            }
            rejected(f,"同为 datetime(3) 的原生记录/时钟必须精确比较，不用共同秒桶放过先后倒置："+kind);
        }
    }

    @Test void r4DefaultInsertClockCannotBeBackdatedAsAParameterRoundingExample() throws Exception {
        try(var f=new PuCertifiedPurchaseOriginalReceiptTest.Fixture()) {
            f.advance(6);long epoch=f.nativeTerminal.occurredAt().getTime();
            Date rounded=new Date(epoch+1000);f.physical.row.setReleasedAt(rounded);
            f.physical.history.stream().filter(e->"CLOSE_ORDER".equals(e.getEventType())).forEach(e->e.setOccurredAt(rounded));
            f.nativeTerminal=replace(f.nativeTerminal,Map.of("occurredAt",new Date(epoch+650)));when(f.physical.mapper.databaseNow()).thenReturn(new Date(epoch+800));f.clearWrites();
            assertAll("默认INSERT事件不是参数Date舍入，不能用兼容正例名义放过未来事件/倒序命令",
                ()->assertThrows(ServiceException.class,()->f.adapter.verify(f.action,f.original,ACTOR),"默认SQL事件下界必须严格认证"),
                ()->assertThrows(ServiceException.class,()->f.adapter.recover(f.action,ACTOR),"倒序默认事件不得恢复成功"));f.assertNoWrites();
        }
    }

    private static void rejected(PuCertifiedPurchaseOriginalReceiptTest.Fixture f,String message) {
        f.clearWrites();assertAll(message,
            ()->assertThrows(ServiceException.class,()->f.adapter.recover(f.action,ACTOR),"恢复必须失败关闭"),
            ()->assertThrows(ServiceException.class,()->f.adapter.verify(f.action,f.original,ACTOR),"核验不能信历史信封而放过原生篡改"));f.assertNoWrites();
    }
    @SuppressWarnings("unchecked")
    static <T>T replace(T record,Map<String,Object> changed) throws Exception {
        var parts=record.getClass().getRecordComponents();Object[] args=new Object[parts.length];Class<?>[] types=new Class<?>[parts.length];
        for(int i=0;i<parts.length;i++){types[i]=parts[i].getType();var accessor=parts[i].getAccessor();accessor.setAccessible(true);args[i]=changed.containsKey(parts[i].getName())?changed.get(parts[i].getName()):accessor.invoke(record);}
        var constructor=record.getClass().getDeclaredConstructor(types);constructor.setAccessible(true);return (T)constructor.newInstance(args);
    }
}
