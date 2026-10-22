package com.nexus.erp.pu;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService.Operation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.Set;

/** 只复用已获准封存时间值于合成身份/数量，不读取真实数据库或改写原结果hash。 */
class PuCertifiedPurchaseOriginalReceiptTimeSourceTest {
    private static final DecisionActor ACTOR=new DecisionActor("000001",901L,"离线核验员",Set.of("ai:decision:execute","pu:requisition:add"));
    @ParameterizedTest @ValueSource(ints={0,1,2})
    void r6ThreePreservedCommitRepresentationsMustRestoreTheirOriginalReceipts(int row) throws Exception {
        String[][] samples={
            {"2026-10-06T19:22:20","2026-10-06T19:30:49","2026-10-06T19:31:18","2026-10-06T19:31:19","2026-10-06T19:53:51","2026-10-06T19:53:51.765","2026-10-06T19:53:51.819"},
            {"2026-10-06T19:22:51","2026-10-06T19:34:39","2026-10-06T19:35:06","2026-10-06T19:35:07","2026-10-06T19:54:32","2026-10-06T19:54:32.446","2026-10-06T19:54:32.504"},
            {"2026-10-06T19:23:20","2026-10-06T19:35:20","2026-10-06T19:35:47","2026-10-06T19:35:47","2026-10-06T19:55:18","2026-10-06T19:55:18.180","2026-10-06T19:55:18.236"}};
        try(var f=new PuCertifiedPurchaseOriginalReceiptTest.Fixture()) {
            f.advance(3);var sample=samples[row];
            for(int i=0;i<4;i++)f.physical.history.get(i).setOccurredAt(date(sample[i==3?4:i]));
            f.physical.row.setCreateTime(date(sample[0]));f.physical.row.setTransferTime(date(sample[3]));f.physical.row.setUpdateTime(date(sample[4]));
            f.approvalUpdatedAt=date(sample[1]);f.requisitionUpdatedAt=date(sample[2]);f.orderCreatedAt=date(sample[2]);f.orderUpdatedAt=date(sample[4]);
            var digest=f.nativeApproval.resultFingerprint();f.nativeApproval=PuCertifiedPurchaseOriginalReceiptReviewRegressionTest.replace(f.nativeApproval,Map.of("occurredAt",date(sample[5])));
            when(f.physical.mapper.databaseNow()).thenReturn(date(sample[6]));f.clearWrites();
            assertEquals(digest,f.nativeApproval.resultFingerprint(),"封存正常时间正控不能通过改旧hash凑成功");
            var verified=assertDoesNotThrow(()->f.adapter.verify(f.action,f.original,ACTOR),"SQL默认事件与Date参数跨秒的真实封存表示必须能独立核验");
            assertSame(f.original,verified,"封存时间兼容必须原样保留旧成功信封");
            var restored=assertDoesNotThrow(()->f.adapter.recover(f.action,ACTOR),"完整正常后续链必须恢复原创建，不为恢复改时间");
            assertTrue(restored.success(),"封存正常时间的原创建恢复应成功");f.assertNoWrites();
        }
    }

    @ParameterizedTest @ValueSource(strings={"CLOSE_ORDER","CANCEL_ORDER"})
    void r6ParameterReleaseAcrossSecondIsNotTheDefaultEventClock(String operation) throws Exception {
        try(var f=new PuCertifiedPurchaseOriginalReceiptTest.Fixture()) {
            f.advance(3);f.endOrder(Operation.valueOf(operation));long epoch=f.physical.history.get(0).getOccurredAt().getTime();
            f.physical.row.setReleasedAt(new Date(epoch+1000));f.physical.row.setUpdateTime(new Date(epoch));
            var digest=f.nativeTerminal.resultFingerprint();f.nativeTerminal=PuCertifiedPurchaseOriginalReceiptReviewRegressionTest.replace(f.nativeTerminal,Map.of("occurredAt",new Date(epoch+800)));
            when(f.physical.mapper.databaseNow()).thenReturn(new Date(epoch+900));f.clearWrites();
            assertEquals(digest,f.nativeTerminal.resultFingerprint(),"释放兼容不改旧终态摘要");
            var verified=assertDoesNotThrow(()->f.adapter.verify(f.action,f.original,ACTOR),"合法参数Date持久跨秒不能被默认INSERT事件秒误拒");
            assertSame(f.original,verified,"合法释放后仍核验原创建信封");
            assertTrue(assertDoesNotThrow(()->f.adapter.recover(f.action,ACTOR),"合法关闭取消应独立恢复原创建").success(),"恢复合法释放后的原回执");f.assertNoWrites();
        }
    }

    @ParameterizedTest @ValueSource(strings={"APPROVE_ORDER","CLOSE_ORDER"})
    void r7NativeOperationCannotPrecedeTheDefaultInsertEventFloor(String operation) throws Exception {
        try(var f=new PuCertifiedPurchaseOriginalReceiptTest.Fixture()) {
            boolean approval="APPROVE_ORDER".equals(operation);f.advance(approval?3:6);long epoch=f.physical.history.get(0).getOccurredAt().getTime();
            f.physical.history.stream().filter(e->operation.equals(e.getEventType())).forEach(e->e.setOccurredAt(new Date(epoch+1000)));
            f.physical.row.setUpdateTime(new Date(epoch+1000));
            if(approval)f.nativeApproval=PuCertifiedPurchaseOriginalReceiptReviewRegressionTest.replace(f.nativeApproval,Map.of("occurredAt",new Date(epoch+750)));
            else {f.physical.row.setReleasedAt(new Date(epoch+1000));f.nativeTerminal=PuCertifiedPurchaseOriginalReceiptReviewRegressionTest.replace(f.nativeTerminal,Map.of("occurredAt",new Date(epoch+750)));}
            when(f.physical.mapper.databaseNow()).thenReturn(new Date(epoch+1200));f.clearWrites();
            assertAll("默认CURRENT_TIMESTAMP(0)严格秒下界不能用native+500放行倒序",
                ()->assertThrows(ServiceException.class,()->f.adapter.verify(f.action,f.original,ACTOR),"原生命令早于对应INSERT事件必须拒绝"),
                ()->assertThrows(ServiceException.class,()->f.adapter.recover(f.action,ACTOR),"倒序不能恢复原创建成功"));f.assertNoWrites();
        }
    }

    @Test
    void r6ParameterCaptureMayPrecedeItsLaterDefaultInsertBySeveralStatements() throws Exception {
        try(var f=new PuCertifiedPurchaseOriginalReceiptTest.Fixture()) {
            f.advance(3);long epoch=f.physical.history.get(0).getOccurredAt().getTime();
            f.physical.row.setTransferTime(new Date(epoch+1000));f.physical.row.setUpdateTime(new Date(epoch+2000));
            f.physical.history.stream().filter(e->Set.of("TRANSFER_TO_ORDER","APPROVE_ORDER").contains(e.getEventType())).forEach(e->e.setOccurredAt(new Date(epoch+2000)));
            f.orderUpdatedAt=new Date(epoch+2000);f.nativeApproval=PuCertifiedPurchaseOriginalReceiptReviewRegressionTest.replace(f.nativeApproval,Map.of("occurredAt",new Date(epoch+2800)));
            when(f.physical.mapper.databaseNow()).thenReturn(new Date(epoch+2900));f.clearWrites();
            var verified=assertDoesNotThrow(()->f.adapter.verify(f.action,f.original,ACTOR),"context参数Date捕获在INSERT前，不能反向要求参数Date晚于event默认时钟");
            assertSame(f.original,verified,"正常SQL多语句时差不改历史信封");f.assertNoWrites();
        }
    }

    @ParameterizedTest @ValueSource(strings={"transferBeforeCreate","transferAfterEventWindow","releaseBeforeCas","missingLatestWriteClock","writeClockBeforePreviousEvent"})
    void impossibleIndependentParameterOrSqlClockProofMustStillFailClosed(String kind) throws Exception {
        try(var f=new PuCertifiedPurchaseOriginalReceiptTest.Fixture()) {
            f.advance(kind.startsWith("release")?6:3);long epoch=f.physical.history.get(0).getOccurredAt().getTime();
            switch(kind) {
                case "transferBeforeCreate" -> f.physical.row.setTransferTime(new Date(epoch-1000));
                case "transferAfterEventWindow" -> f.physical.row.setTransferTime(new Date(epoch+2000));
                case "releaseBeforeCas" -> f.physical.row.setReleasedAt(new Date(epoch-1000));
                case "missingLatestWriteClock" -> f.physical.row.setUpdateTime(null);
                case "writeClockBeforePreviousEvent" -> f.physical.row.setUpdateTime(new Date(epoch-1000));
                default -> throw new IllegalArgumentException("未知时间来源反例");
            }
            f.clearWrites();assertAll("独立参数区间或SQL时钟未知必须拒绝："+kind,
                ()->assertThrows(ServiceException.class,()->f.adapter.verify(f.action,f.original,ACTOR),"时间证明不成立不能核验成功"),
                ()->assertThrows(ServiceException.class,()->f.adapter.recover(f.action,ACTOR),"时间未知不能投影回执"));f.assertNoWrites();
        }
    }

    static Date date(String value){return Date.from(LocalDateTime.parse(value).atZone(ZoneId.of("Asia/Shanghai")).toInstant());}
}
