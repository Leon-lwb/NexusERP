package com.nexus.erp.pu;

import cn.dev33.satoken.stp.StpUtil;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.log.annotation.Log;
import com.nexus.erp.pu.controller.PuCertifiedPurchaseOrderWorkflowController;
import com.nexus.erp.pu.service.PuCertifiedPurchaseOrderWorkflowService;
import com.nexus.erp.pu.service.PuCertifiedPurchaseOrderWorkflowService.SupplierTermsRequest;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseOrderWorkflowMapper.Quote;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService.CertifiedPurchaseScope;
import java.time.LocalDate;
import com.nexus.erp.pu.PuCertifiedPurchaseOrderWorkflowServiceTest.Fixture;
import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.nexus.erp.pu.service.support.PuCertifiedPurchaseLifecycleSupport.hash;

/** 原报价未知结果的显式处置回归；模拟持久事实不等于现场验收。 */
class PuCertifiedSupplierTermsReconciliationTest {
    private static Fixture recoveryFixture() {
        Fixture f=new Fixture();
        when(f.certification.previewLocked(any(),any(),any(),any(),any())).thenAnswer(a->{
            assertTrue(f.callback);
            return new CertifiedPurchaseScope(f.t,201L,1501L,LocalDate.of(2026,10,3),f.end,f.currentCatalog,null,
                java.util.List.of(),java.util.Map.of(),"e".repeat(64));
        });
        return f;
    }
    private static String requestHash(Fixture f, SupplierTermsRequest r) {
        return hash("QUOTE_REQUEST",f.t,f.actor.toString(),"801",r.expectedOrderVersion().toString(),r.requestKey(),
            r.unitPriceExcludingTax().stripTrailingZeros().toPlainString(),r.taxRate().stripTrailingZeros().toPlainString(),r.currency(),
            r.exchangeRate().stripTrailingZeros().toPlainString(),r.promisedArrivalDate().toString(),r.validFrom().toString(),r.validUntil().toString(),
            r.supplierQuoteReference(),r.supplierContact(),r.evidenceReference(),r.evidenceSha256(),r.paymentTerms(),r.deliveryTerms());
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static Object resolve(Fixture f,SupplierTermsRequest r,String mode,Long actor,String digest,String intent,String reason) {
        try {
            Class<?> modeType=Class.forName(PuCertifiedPurchaseOrderWorkflowService.class.getName()+"$TermsRecoveryMode");
            Class<?> requestType=Class.forName(PuCertifiedPurchaseOrderWorkflowService.class.getName()+"$TermsRecoveryRequest");
            Object input=requestType.getConstructor(SupplierTermsRequest.class,Long.class,String.class,String.class,String.class,modeType)
                .newInstance(r,actor,intent,digest,reason,mode==null?null:Enum.valueOf((Class)modeType,mode));
            return PuCertifiedPurchaseOrderWorkflowService.class.getMethod("reconcileTerms",Long.class,requestType).invoke(f.service,801L,input);
        } catch(InvocationTargetException e) {
            if(e.getCause() instanceof RuntimeException error)throw error;
            throw new AssertionError(e.getCause());
        } catch(ReflectiveOperationException e) {throw new AssertionError("缺少原请求显式处置能力",e);}
    }
    private static Object resolve(Fixture f,String mode) {
        var r=f.request("original-unknown-key");return resolve(f,r,mode,f.actor,requestHash(f,r),"9".repeat(64),"保留原未知请求，仅核对原键并显式处置当前状态");
    }
    private static Object field(Object result,String name) {
        try{return result.getClass().getMethod(name).invoke(result);}catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    @Test void checkOnlyRevalidatesAbsentOriginalWithoutAnyQuoteOrOrderWrite() {
        try(var f=recoveryFixture()) {
            var r=resolve(f,"CHECK_ONLY");assertEquals("ABSENT_REVALIDATED",field(r,"status").toString());assertNull(field(r,"result"));
            verify(f.mapper,never()).insertQuote(any());f.noOrderWrites();assertEquals(1,f.executions);
            verify(f.locks).lockExisting(f.t,java.util.List.of(201L));verify(f.locks,never()).lock(any(),any());
            verify(f.certification).previewLocked(eq(f.t),eq(201L),eq(1501L),eq(f.end),any());
        }
    }
    @Test void settleCreatesOneOriginalKeyQuoteNowAndNeverClaimsHistoricalSuccess() {
        try(var f=recoveryFixture()) {
            var r=resolve(f,"SETTLE_IF_ABSENT");assertEquals("RECORDED_AFTER_RECONCILIATION",field(r,"status").toString());
            assertEquals("9".repeat(64),field(r,"originalIntentSha256"));assertEquals(f.actor,field(r,"actorId"));
            var q=(Quote)field(field(r,"result"),"quote");assertEquals("original-unknown-key",q.requestKey());assertEquals(requestHash(f,f.request(q.requestKey())),q.requestHash());
            assertEquals(1,q.quoteVersion());assertEquals(1,f.quotes.size());assertEquals("DRAFT",f.state);assertEquals(0,f.version);f.noOrderWrites();
            verify(f.mapper,times(1)).insertQuote(any());verify(f.locks).lockExisting(f.t,java.util.List.of(201L));
        }
    }
    @Test void existingOriginalCommitOnlyReturnsVerifiedResultAndNeverRecreatesOrRecertifies() {
        try(var f=recoveryFixture()) {
            var first=resolve(f,"SETTLE_IF_ABSENT");clearInvocations(f.mapper,f.certification);
            var next=resolve(f,"SETTLE_IF_ABSENT");assertEquals("EXISTING_COMMIT",field(next,"status").toString());assertEquals(true,field(field(next,"result"),"replayed"));
            assertEquals(field(field(first,"result"),"quote"),field(field(next,"result"),"quote"));
            verify(f.mapper,never()).insertQuote(any());verifyNoInteractions(f.certification);assertEquals(1,f.quotes.size());
        }
    }
    @Test void checkOnlyCanReadACommitWithoutNewEffects() {
        try(var f=recoveryFixture()) {resolve(f,"SETTLE_IF_ABSENT");clearInvocations(f.mapper,f.certification);assertEquals("EXISTING_COMMIT",field(resolve(f,"CHECK_ONLY"),"status").toString());verify(f.mapper,never()).insertQuote(any());verifyNoInteractions(f.certification);}
    }
    @Test void originalActorMustMatchActualAuthenticatedActorBeforeTransaction() {
        try(var f=recoveryFixture()) {var r=f.request("original-unknown-key");assertThrows(ServiceException.class,()->resolve(f,r,"SETTLE_IF_ABSENT",902L,requestHash(f,r),"9".repeat(64),"明确处置原请求"));assertEquals(0,f.executions);verify(f.mapper,never()).insertQuote(any());}
    }
    @Test void completeOriginalLogicalDigestMustMatchBeforeAnyTransaction() {
        try(var f=recoveryFixture()) {assertThrows(ServiceException.class,()->resolve(f,f.request("original-unknown-key"),"SETTLE_IF_ABSENT",f.actor,"a".repeat(64),"9".repeat(64),"明确处置原请求"));assertEquals(0,f.executions);}
    }
    @Test void changedPriceCannotReuseFrozenOriginalDigest() {
        try(var f=recoveryFixture()) {var original=f.request("original-unknown-key");var changed=new SupplierTermsRequest(original.requestKey(),0,new BigDecimal("11"),original.taxRate(),original.currency(),original.exchangeRate(),original.promisedArrivalDate(),original.validFrom(),original.validUntil(),original.supplierQuoteReference(),original.supplierContact(),original.evidenceReference(),original.evidenceSha256(),original.paymentTerms(),original.deliveryTerms());assertThrows(ServiceException.class,()->resolve(f,changed,"SETTLE_IF_ABSENT",f.actor,requestHash(f,original),"9".repeat(64),"明确处置原请求"));assertEquals(0,f.executions);}
    }
    @Test void absentOriginalCannotSupersedeAnotherQuoteVersion() {
        try(var f=recoveryFixture()) {f.service.recordTerms(801L,f.request("another-key"));clearInvocations(f.mapper);assertThrows(ServiceException.class,()->resolve(f,"SETTLE_IF_ABSENT"));verify(f.mapper,never()).insertQuote(any());assertEquals(1,f.quotes.size());}
    }
    @Test void expiredTermsNeverBecomeCurrentSuccessAfterAbsence() {
        try(var f=recoveryFixture()) {f.today="2026-10-16";assertThrows(ServiceException.class,()->resolve(f,"SETTLE_IF_ABSENT"));verify(f.mapper,never()).insertQuote(any());}
    }
    @Test void staleHeadVersionNeverSettles() {
        try(var f=recoveryFixture()) {f.version=1;assertThrows(ServiceException.class,()->resolve(f,"SETTLE_IF_ABSENT"));verify(f.mapper,never()).insertQuote(any());}
    }
    @Test void changedCurrentCatalogIsNotForgivenByOriginalIntentHash() {
        try(var f=recoveryFixture()) {f.currentCatalog=f.catalog("drifted");assertThrows(ServiceException.class,()->resolve(f,"SETTLE_IF_ABSENT"));verify(f.mapper,never()).insertQuote(any());}
    }
    @Test void overcommittedGlobalCapacityStillRejectsSettlement() {
        try(var f=recoveryFixture()) {doThrow(new ServiceException("OVERCOMMITTED")).when(f.certification).previewLocked(any(),any(),any(),any(),any());assertThrows(ServiceException.class,()->resolve(f,"SETTLE_IF_ABSENT"));verify(f.mapper,never()).insertQuote(any());}
    }
    @Test void readOnlyCheckRetainsSameCapacityGuard() {
        try(var f=recoveryFixture()) {doThrow(new ServiceException("OVERCOMMITTED")).when(f.certification).previewLocked(any(),any(),any(),any(),any());assertThrows(ServiceException.class,()->resolve(f,"CHECK_ONLY"));verify(f.mapper,never()).insertQuote(any());}
    }
    @Test void failedInsertNeverReturnsSettledSuccess() {
        try(var f=recoveryFixture()) {doReturn(0).when(f.mapper).insertQuote(any());assertThrows(ServiceException.class,()->resolve(f,"SETTLE_IF_ABSENT"));}
    }
    @Test void missingGuardIsNotCreatedByRecovery() {
        try(var f=recoveryFixture()) {when(f.locks.lockExisting(any(),any())).thenReturn(java.util.Map.of());assertThrows(ServiceException.class,()->resolve(f,"SETTLE_IF_ABSENT"));verify(f.locks,never()).lock(any(),any());verify(f.mapper,never()).insertQuote(any());}
    }
    @Test void permissionIsRecheckedInsideTheExclusiveErpTransaction() {
        try(var f=recoveryFixture()) {f.auth.when(()->StpUtil.checkPermission("pu:order:terms:add")).thenAnswer(a->{if(f.callback)throw new ServiceException("失权");return null;});assertThrows(ServiceException.class,()->resolve(f,"SETTLE_IF_ABSENT"));verify(f.mapper,never()).insertQuote(any());}
    }
    @Test void malformedIntentDigestNeverAuthorizesSettlement() {
        try(var f=recoveryFixture()) {var r=f.request("original-unknown-key");assertThrows(ServiceException.class,()->resolve(f,r,"SETTLE_IF_ABSENT",f.actor,requestHash(f,r),"invalid","明确处置原请求"));assertEquals(0,f.executions);}
    }
    @Test void reasonRequiredForAuditEvenForReadOnlyCheck() {
        try(var f=recoveryFixture()) {var r=f.request("original-unknown-key");assertThrows(ServiceException.class,()->resolve(f,r,"CHECK_ONLY",f.actor,requestHash(f,r),"9".repeat(64),""));assertEquals(0,f.executions);}
    }
    @Test void explicitModeRequiredAndNeverDefaultsToWriting() {
        try(var f=recoveryFixture()) {var r=f.request("original-unknown-key");assertThrows(ServiceException.class,()->resolve(f,r,null,f.actor,requestHash(f,r),"9".repeat(64),"明确处置原请求"));assertEquals(0,f.executions);}
    }
    @Test void queryPermissionIsRequiredBeforeTransaction() {
        try (var f=recoveryFixture()) {
            f.auth.when(()->StpUtil.checkPermission("pu:order:query")).thenThrow(new ServiceException("查询失权"));
            assertThrows(ServiceException.class,()->resolve(f,"CHECK_ONLY"));
            assertEquals(0,f.executions);verify(f.mapper,never()).insertQuote(any());
        }
    }
    @Test void queryPermissionIsRecheckedInsideTransaction() {
        try (var f=recoveryFixture()) {
            f.auth.when(()->StpUtil.checkPermission("pu:order:query")).thenAnswer(a->{if(f.callback)throw new ServiceException("查询失权");return null;});
            assertThrows(ServiceException.class,()->resolve(f,"SETTLE_IF_ABSENT"));
            verify(f.mapper,never()).insertQuote(any());
        }
    }
    @Test void actualActorDriftInsideTransactionCannotWrite() {
        try (var f=recoveryFixture()) {
            f.login.when(LoginHelper::getUserId).thenAnswer(a->f.callback?902L:f.actor);
            assertThrows(ServiceException.class,()->resolve(f,"SETTLE_IF_ABSENT"));
            verify(f.mapper,never()).insertQuote(any());
        }
    }
    @Test void actualTenantDriftInsideTransactionCannotWrite() {
        try (var f=recoveryFixture()) {
            f.login.when(LoginHelper::getTenantId).thenAnswer(a->f.callback?"123456":f.t);
            assertThrows(ServiceException.class,()->resolve(f,"SETTLE_IF_ABSENT"));
            verify(f.mapper,never()).insertQuote(any());
        }
    }
    @Test void originalKeyAlreadyOwnedByDifferentActorCannotBeRecovered() {
        try (var f=recoveryFixture()) {
            resolve(f,"SETTLE_IF_ABSENT");clearInvocations(f.mapper);
            f.actor=902L;
            assertThrows(ServiceException.class,()->resolve(f,"SETTLE_IF_ABSENT"));
            verify(f.mapper,never()).insertQuote(any());assertEquals(1,f.quotes.size());
        }
    }
    @Test void publicEndpointRetainsBothPermissionsAndCurrentReconciliationAudit() throws Exception {
        Class<?> request=Class.forName(PuCertifiedPurchaseOrderWorkflowService.class.getName()+"$TermsRecoveryRequest");
        var method=PuCertifiedPurchaseOrderWorkflowController.class.getMethod("reconcileTerms",Long.class,request);
        assertArrayEquals(new String[]{"pu:order:terms:add","pu:order:query"},method.getAnnotation(SaCheckPermission.class).value());
        assertNotNull(method.getAnnotation(Log.class));
        assertEquals("/{id}/terms/reconcile",method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class).value()[0]);
        assertNull(method.getAnnotation(org.springframework.transaction.annotation.Transactional.class));
        var service=PuCertifiedPurchaseOrderWorkflowService.class.getMethod("reconcileTerms",Long.class,request);
        assertNull(service.getAnnotation(org.springframework.transaction.annotation.Transactional.class));
        assertNull(service.getAnnotation(com.baomidou.dynamic.datasource.annotation.DS.class));
    }
    @Test void bothRecoveryModesPropagateExistingGuardOnlyToLifecycleAndCertification() {
        for (String mode:java.util.List.of("CHECK_ONLY","SETTLE_IF_ABSENT")) {
            try (var f=recoveryFixture()) {
                resolve(f,mode);
                verify(f.certification).previewLocked(eq(f.t),eq(201L),eq(1501L),eq(f.end),any());
                verify(f.certification,never()).prepareLocked(any(),any(),any(),any(),any());
                var calls=mockingDetails(f.lifecycle).getInvocations();
                assertTrue(calls.stream().anyMatch(x->x.getMethod().getName().equals("validateOrderForWorkflowExistingGuardLocked")));
                assertFalse(calls.stream().anyMatch(x->x.getMethod().getName().equals("validateOrderForWorkflowLocked")));
                verify(f.locks,never()).lock(any(),any());
            }
        }
    }
    @Test void checkOnlyMustRejectTheSameRoundedZeroAmountAsActualSettlement() {
        try (var f=recoveryFixture()) {
            var r=f.request("original-unknown-key");
            var tooSmall=new SupplierTermsRequest(r.requestKey(),r.expectedOrderVersion(),new BigDecimal("0.0001"),
                r.taxRate(),r.currency(),r.exchangeRate(),r.promisedArrivalDate(),r.validFrom(),r.validUntil(),
                r.supplierQuoteReference(),r.supplierContact(),r.evidenceReference(),r.evidenceSha256(),r.paymentTerms(),r.deliveryTerms());
            assertThrows(ServiceException.class,()->resolve(f,tooSmall,"CHECK_ONLY",f.actor,requestHash(f,tooSmall),"9".repeat(64),"核对金额门禁"));
            verify(f.mapper,never()).insertQuote(any());f.noOrderWrites();
        }
    }
}
