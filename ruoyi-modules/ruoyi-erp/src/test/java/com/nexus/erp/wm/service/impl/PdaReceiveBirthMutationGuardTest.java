package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.wm.domain.*;
import com.nexus.erp.wm.domain.bo.pda.PdaLineConfirmBo;
import com.nexus.erp.wm.domain.bo.pda.PdaShelfBo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveVo;
import com.nexus.erp.wm.mapper.*;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper.*;
import com.nexus.erp.wm.service.IPutawayStrategyService;
import com.nexus.erp.wm.service.IWmMaterialReceiveService;
import com.nexus.erp.wm.service.WmCertifiedReceiptMutationGuard;
import com.nexus.erp.wm.service.WmCertifiedReceiptPostingService;
import com.nexus.erp.wm.service.PdaReceiveShelfAllocationService;
import com.nexus.erp.wm.service.IWmMaterialReceiveCrudService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 实际 PDA 服务及真实冻结守卫回归；Spring 事务代理使用连接替身，不连接真实数据库。 */
class PdaReceiveBirthMutationGuardTest {
    private static final String TENANT = "000007";
    private final IWmMaterialReceiveService receipts = mock(IWmMaterialReceiveService.class);
    private final WmCertifiedReceiptPostingService posting = mock(WmCertifiedReceiptPostingService.class);
    private final IWmMaterialReceiveCrudService legacy = mock(IWmMaterialReceiveCrudService.class);
    private final WmMaterialReceiveMapper heads = mock(WmMaterialReceiveMapper.class);
    private final WmMaterialReceiveLineMapper lines = mock(WmMaterialReceiveLineMapper.class);
    private final WmArrivalNoticeLineMapper arrivals = mock(WmArrivalNoticeLineMapper.class);
    private final WmMaterialReceiveDetailMapper details = mock(WmMaterialReceiveDetailMapper.class);
    private final WmStorageLocationMapper locations = mock(WmStorageLocationMapper.class);
    private final IPutawayStrategyService putaway = mock(IPutawayStrategyService.class);
    private final WmCertifiedReceiptPostingMapper frozen = mock(WmCertifiedReceiptPostingMapper.class);
    private final DataSource datasource = mock(DataSource.class);
    private MockedStatic<TenantHelper> tenant;
    private PdaReceiveServiceImpl target;
    private PdaReceiveServiceImpl service;
    private DataSourceTransactionManager transactions;
    private WmMaterialReceiveDetail allocation;

    @BeforeAll static void nativeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> type : List.of(WmMaterialReceive.class, WmMaterialReceiveLine.class,
            WmMaterialReceiveDetail.class, WmArrivalNoticeLine.class, WmStorageLocation.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    @BeforeEach void arrange() throws Exception {
        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::isEnable).thenReturn(true);
        tenant.when(TenantHelper::getTenantId).thenReturn(TENANT);
        Connection connection = mock(Connection.class);
        when(datasource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        transactions = new DataSourceTransactionManager(datasource);
        WmCertifiedReceiptMutationGuard guard = transactional(new WmCertifiedReceiptMutationGuard(frozen));
        // 用真实构造器装配：旧实现不注入守卫时也能编译，回归会在行为断言处失败。
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("receiveService", receipts); beans.registerSingleton("receiveMapper", heads);
        beans.registerSingleton("lineMapper", lines); beans.registerSingleton("arrivalNoticeLineMapper", arrivals);
        beans.registerSingleton("detailMapper", details); beans.registerSingleton("locationMapper", locations);
        beans.registerSingleton("putawayStrategyService", putaway); beans.registerSingleton("certifiedReceiptGuard", guard);
        beans.registerSingleton("certifiedPosting", posting); beans.registerSingleton("crudService", legacy);
        PdaReceiveShelfAllocationService allocator = transactional((PdaReceiveShelfAllocationService) beans.createBean(
            PdaReceiveShelfAllocationService.class, AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR, false));
        beans.registerSingleton("shelfAllocation", allocator);
        target = (PdaReceiveServiceImpl) beans.createBean(PdaReceiveServiceImpl.class,
            AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR, false);
        service = transactional(target);
        when(frozen.discoverLineIdentity(TENANT, 701L)).thenReturn(identity(101L, 701L));
        when(frozen.lockLineIdentity(TENANT, 701L)).thenReturn(identity(101L, 701L));
        when(frozen.lockHead(eq(TENANT), anyLong())).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.getResource(datasource)).isNotNull();
            return lockedHead(i.getArgument(1), "APPROVED");
        });
        when(frozen.lockBirthIdentities(TENANT, 101L)).thenReturn(List.of());
        when(receipts.selectById(101L)).thenReturn(head("APPROVED"));
        when(heads.selectVoOne(any())).thenReturn(head("APPROVED"));
        when(lines.selectOne(any())).thenReturn(line(701L, "10"));
        when(lines.selectList(any())).thenReturn(List.of(line(701L, "10")));
        WmArrivalNoticeLine arrival = new WmArrivalNoticeLine(); arrival.setQuantityArrival(new BigDecimal("20"));
        when(arrivals.selectList(any())).thenReturn(List.of(arrival));
        allocation = allocation(901L, 701L);
        when(details.selectOne(any())).thenReturn(allocation);
        when(details.selectList(any())).thenReturn(List.of(allocation));
        when(details.updateById(any(WmMaterialReceiveDetail.class))).thenReturn(1);
        when(details.update(any(), any())).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?> update = i.getArgument(1);
            assertThat(update.getSqlSet()).contains("location_id", "location_code")
                .doesNotContain("quantity", "receive_id", "line_id", "material_id");
            allocation.setLocationId(7L); allocation.setLocationCode("A-01"); return 1;
        });
        when(details.insert(any(WmMaterialReceiveDetail.class))).thenReturn(1);
        when(lines.update(any(), any())).thenReturn(1);
        when(frozen.discoverDetailIdentity(TENANT, 901L)).thenReturn(identity(101L, 701L));
        when(frozen.lockDetailIdentity(TENANT, 901L)).thenReturn(identity(101L, 701L));
        WmStorageLocation location = new WmStorageLocation(); location.setLocationId(7L); location.setLocationCode("A-01");
        when(locations.selectOne(any())).thenReturn(location);
        when(posting.handlesShelf(101L)).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); return true;
        });
        when(posting.shelf(101L)).thenAnswer(i -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); return true;
        });
    }

    @SuppressWarnings("unchecked") private <T> T transactional(T bean) {
        ProxyFactory proxy = new ProxyFactory(bean); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }

    @AfterEach void clear() {
        tenant.close();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
    }

    private SourceIdentity identity(Long receive, Long line) { return new SourceIdentity(TENANT, receive, line); }
    private Head lockedHead(Long receive, String status) {
        return new Head(TENANT, receive, "RECV-101", 41L, 91L, status, 5L, 6L, 7L, null, null, "0");
    }
    private WmMaterialReceiveVo head(String status) {
        WmMaterialReceiveVo head = new WmMaterialReceiveVo(); head.setReceiveId(101L); head.setStatus(status);
        head.setNoticeId(77L); head.setWarehouseId(5L); return head;
    }
    private WmMaterialReceiveLine line(Long id, String quantity) {
        WmMaterialReceiveLine row = new WmMaterialReceiveLine(); row.setLineId(id); row.setReceiveId(101L);
        row.setMaterialId(201L); row.setWarehouseId(5L); row.setBaseUomId("301");
        row.setQuantityReceived(new BigDecimal(quantity)); return row;
    }
    private WmMaterialReceiveDetail allocation(Long id, Long line) {
        WmMaterialReceiveDetail detail = new WmMaterialReceiveDetail(); detail.setDetailId(id);
        detail.setReceiveId(101L); detail.setLineId(line); detail.setMaterialId(201L);
        detail.setQuantity(new BigDecimal("10")); return detail;
    }
    private PdaLineConfirmBo oneMore() {
        PdaLineConfirmBo bo = new PdaLineConfirmBo(); bo.setLineId(701L); bo.setQty(BigDecimal.ONE); return bo;
    }
    private PdaShelfBo shelf() { PdaShelfBo bo = new PdaShelfBo(); bo.setLocationCode("A-01"); return bo; }
    private void zeroDml() {
        verify(lines, never()).update(any(), any());
        verify(details, never()).insert(any(WmMaterialReceiveDetail.class));
        verify(details, never()).updateById(any(WmMaterialReceiveDetail.class));
        verify(details, never()).update(any(), any());
    }
    private void rejectLine() {
        assertThatThrownBy(() -> service.confirmLine(101L, oneMore())).isInstanceOf(ServiceException.class);
        zeroDml();
    }

    @Test void bornTenReceivedTwentyDuePlusOneMustBeRejectedBeforeAnyDml() {
        when(frozen.lockBirthIdentities(TENANT, 101L)).thenReturn(List.of(71L));
        rejectLine(); assertThat(allocation.getQuantity()).isEqualByComparingTo("10");
        verify(frozen).lockHead(TENANT, 101L); verify(frozen).lockBirthIdentities(TENANT, 101L);
    }
    @Test void existingPostingMustRejectEvenIfHeadWasTamperedBackToApproved() {
        when(frozen.lockPosting(TENANT, 101L)).thenReturn(new Posting(TENANT, 101L, 201L, 9L,
            "a".repeat(64), BigDecimal.TEN, "2026-10-03 10:00:00", "ENQUEUED"));
        rejectLine();
    }
    @Test void foreignTenantLineIdentityMustNotReachWrites() {
        when(frozen.discoverLineIdentity(TENANT, 701L)).thenReturn(new SourceIdentity("000008", 101L, 701L));
        rejectLine();
    }
    @Test void lineCannotBeReboundToTheRequestedOtherReceipt() {
        when(frozen.discoverLineIdentity(TENANT, 701L)).thenReturn(identity(202L, 701L));
        when(frozen.lockLineIdentity(TENANT, 701L)).thenReturn(identity(202L, 701L));
        rejectLine();
    }
    @Test void changedParentBetweenDiscoveryAndLockCannotBeAccepted() {
        when(frozen.lockLineIdentity(TENANT, 701L)).thenReturn(identity(202L, 701L));
        rejectLine();
    }
    @Test void birthRegistryReadFailureIsNotAnUnbornDefault() {
        when(frozen.lockBirthIdentities(TENANT, 101L)).thenThrow(new IllegalStateException("原生结构不可用"));
        rejectLine();
    }
    @Test void unknownBirthRegistryResultMustFailClosed() {
        when(frozen.lockBirthIdentities(TENANT, 101L)).thenReturn(null);
        rejectLine();
    }
    @Test void currentLockedShelvedHeadCannotUseAnOldApprovedRead() {
        doReturn(lockedHead(101L, "SHELVED")).when(frozen).lockHead(TENANT, 101L);
        rejectLine();
    }
    @Test void mutationGuardRequiresTheExistingPdaTransaction() {
        assertThatThrownBy(() -> target.confirmLine(101L, oneMore())).isInstanceOf(IllegalTransactionStateException.class);
        zeroDml(); verifyNoInteractions(frozen);
    }
    @Test void unbornValidLineLocksItsHeadAndIdentityBeforeChangingQuantity() {
        assertThat(service.confirmLine(101L, oneMore()).getOk()).isTrue();
        var order = inOrder(frozen, lines);
        order.verify(frozen).discoverLineIdentity(TENANT, 701L);
        order.verify(frozen).lockHead(TENANT, 101L);
        order.verify(frozen).lockLineIdentity(TENANT, 701L);
        order.verify(lines).selectOne(any()); order.verify(lines).update(any(), any());
        assertThat(allocation.getQuantity()).isEqualByComparingTo("11");
    }
    @Test void detailQuantityUsesTheCurrentLockedLineRatherThanAnOlderSnapshot() {
        when(lines.selectOne(any())).thenAnswer(i -> {
            Wrapper<?> query = i.getArgument(0);
            return line(701L, query.getSqlSegment().contains("FOR UPDATE") ? "10" : "8");
        });
        assertThat(service.confirmLine(101L, oneMore()).getOk()).isTrue();
        assertThat(allocation.getQuantity()).isEqualByComparingTo("11");
    }
    @Test void currentRejectedHeadCannotUseAnOlderApprovedHeadForPdaReceiving() {
        doReturn(lockedHead(101L, "REJECTED")).when(frozen).lockHead(TENANT, 101L);
        when(heads.selectVoOne(any())).thenReturn(head("REJECTED"));
        assertThat(service.confirmLine(101L, oneMore()).getOk()).isFalse(); zeroDml();
    }
    @Test void bornReceiptStillAllowsPureLocationAllocationWithoutQuantityRewrite() {
        when(frozen.lockBirthIdentities(TENANT, 101L)).thenReturn(List.of(71L));
        assertThat(service.shelf(101L, shelf()).getOk()).isTrue();
        assertThat(allocation.getLocationId()).isEqualTo(7L);
        assertThat(allocation.getQuantity()).isEqualByComparingTo("10");
        verify(lines, never()).update(any(), any());
        verify(frozen).lockLineIdentity(TENANT, 701L); verify(frozen).lockDetailIdentity(TENANT, 901L);
        verify(frozen, never()).lockBirthIdentities(anyString(), anyLong());
    }
    @Test void bornReceiptMayCreateOnlyAnAllocationWithItsUnchangedCurrentQuantity() {
        when(frozen.lockBirthIdentities(TENANT, 101L)).thenReturn(List.of(71L));
        when(details.selectList(any())).thenReturn(List.of());
        assertThat(service.shelf(101L, shelf()).getOk()).isTrue();
        ArgumentCaptor<WmMaterialReceiveDetail> inserted = ArgumentCaptor.forClass(WmMaterialReceiveDetail.class);
        verify(details).insert(inserted.capture());
        assertThat(inserted.getValue().getQuantity()).isEqualByComparingTo("10");
        assertThat(inserted.getValue().getReceiveId()).isEqualTo(101L);
        assertThat(inserted.getValue().getLineId()).isEqualTo(701L);
        verify(lines, never()).update(any(), any()); verify(frozen).lockLineIdentity(TENANT, 701L);
    }
    @Test void pureAllocationMustNotRebindAnExistingDetailToAnotherReceipt() {
        when(frozen.discoverDetailIdentity(TENANT, 901L)).thenReturn(identity(202L, 701L));
        when(frozen.lockDetailIdentity(TENANT, 901L)).thenReturn(identity(202L, 701L));
        assertThatThrownBy(() -> service.shelf(101L, shelf())).isInstanceOf(ServiceException.class); zeroDml();
    }
    @Test void everyAllocationParentIsPrecheckedBeforeTheFirstDetailInsert() {
        when(lines.selectList(any())).thenReturn(List.of(line(701L, "10"), line(702L, "5")));
        when(details.selectList(any())).thenReturn(List.of());
        when(frozen.lockLineIdentity(TENANT, 702L)).thenReturn(identity(202L, 702L));
        assertThatThrownBy(() -> service.shelf(101L, shelf())).isInstanceOf(ServiceException.class); zeroDml();
    }
}