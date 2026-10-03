package com.nexus.erp.wm.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.wm.domain.*;
import com.nexus.erp.wm.domain.bo.pda.PdaShelfBo;
import com.nexus.erp.wm.domain.vo.WmMaterialReceiveVo;
import com.nexus.erp.wm.mapper.*;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper.Head;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper.SourceIdentity;
import com.nexus.erp.wm.service.impl.PdaReceiveServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实 Spring 代理的事务边界回归；连接、持久状态和外部专属入口均为无服务替身。 */
class PdaReceiveShelfAllocationServiceTest {
    private static final String TENANT = "000007";
    private static final String ALLOCATION_CLASS = "com.nexus.erp.wm.service.PdaReceiveShelfAllocationService";
    private final WmMaterialReceiveMapper heads = mock(WmMaterialReceiveMapper.class);
    private final WmMaterialReceiveLineMapper lines = mock(WmMaterialReceiveLineMapper.class);
    private final WmMaterialReceiveDetailMapper details = mock(WmMaterialReceiveDetailMapper.class);
    private final WmStorageLocationMapper locations = mock(WmStorageLocationMapper.class);
    private final WmCertifiedReceiptPostingMapper frozen = mock(WmCertifiedReceiptPostingMapper.class);
    private final IWmMaterialReceiveService facade = mock(IWmMaterialReceiveService.class);
    private final IWmMaterialReceiveCrudService legacy = mock(IWmMaterialReceiveCrudService.class);
    private final WmCertifiedReceiptPostingService posting = mock(WmCertifiedReceiptPostingService.class);
    private final DataSource datasource = mock(DataSource.class);
    private final List<String> events = new ArrayList<>();
    private MockedStatic<TenantHelper> tenant;
    private DataSourceTransactionManager transactions;
    private PdaReceiveServiceImpl service;
    private Object allocator;
    private boolean certified = true;
    private boolean nativeFailure;
    private boolean nativeFalse;
    private Object allocationHolder;
    private boolean legacyFailure;
    private boolean legacyFalse;
    private int changedRows = 1;
    private int commits;
    private int rollbacks;
    private String status = "APPROVED";
    private String pendingStatus;
    private WmMaterialReceiveDetail committed;
    private WmMaterialReceiveDetail pending;

    @BeforeAll static void nativeMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> type : List.of(WmMaterialReceive.class, WmMaterialReceiveLine.class,
            WmMaterialReceiveDetail.class, WmStorageLocation.class, WmArrivalNoticeLine.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    @BeforeEach void arrange() throws Exception {
        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::isEnable).thenReturn(true);
        tenant.when(TenantHelper::getTenantId).thenReturn(TENANT);
        Connection connection = mock(Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        when(datasource.getConnection()).thenReturn(connection);
        doAnswer(i -> {
            events.add("commit"); commits++;
            if (pending != null) committed = copy(pending);
            if (pendingStatus != null) status = pendingStatus;
            pending = null; pendingStatus = null; return null;
        }).when(connection).commit();
        doAnswer(i -> { events.add("rollback"); rollbacks++; pending = null; pendingStatus = null; return null; })
            .when(connection).rollback();
        transactions = new DataSourceTransactionManager(datasource);
        committed = allocation();
        when(heads.selectVoOne(any())).thenAnswer(i -> { assertInTransaction(); return head(); });
        when(lines.selectList(any())).thenAnswer(i -> { assertInTransaction(); return List.of(line()); });
        when(details.selectList(any())).thenAnswer(i -> {
            assertInTransaction(); return committed == null ? List.of() : List.of(copy(committed));
        });
        when(details.updateById(any(WmMaterialReceiveDetail.class))).thenAnswer(i -> {
            assertInTransaction(); allocationHolder = TransactionSynchronizationManager.getResource(datasource); events.add("allocation"); pending = copy(i.getArgument(0)); return changedRows;
        });
        when(details.update(any(), any())).thenAnswer(i -> {
            assertInTransaction(); allocationHolder = TransactionSynchronizationManager.getResource(datasource); events.add("allocation");
            LambdaUpdateWrapper<WmMaterialReceiveDetail> update = i.getArgument(1);
            assertThat(update.getSqlSet()).contains("location_id", "location_code")
                .doesNotContain("quantity", "receive_id", "line_id", "material_id", "base_uom_id");
            pending = copy(committed); pending.setLocationId(7L); pending.setLocationCode("A-01"); return changedRows;
        });
        when(details.insert(any(WmMaterialReceiveDetail.class))).thenAnswer(i -> {
            assertInTransaction(); allocationHolder = TransactionSynchronizationManager.getResource(datasource); events.add("allocation"); pending = copy(i.getArgument(0)); pending.setDetailId(901L);
            return changedRows;
        });
        when(frozen.lockHead(TENANT, 101L)).thenAnswer(i -> {
            assertInTransaction(); return new Head(TENANT, 101L, "RECV-101", 41L, 91L, status, 5L, 6L, null, null, null, "0");
        });
        when(frozen.lockPosting(TENANT, 101L)).thenReturn(null);
        when(frozen.lockBirthIdentities(TENANT, 101L)).thenAnswer(i -> certified ? List.of(71L) : List.of());
        SourceIdentity identity = new SourceIdentity(TENANT, 101L, 701L);
        when(frozen.lockLineIdentity(TENANT, 701L)).thenReturn(identity);
        when(frozen.discoverDetailIdentity(TENANT, 901L)).thenReturn(identity);
        when(frozen.lockDetailIdentity(TENANT, 901L)).thenReturn(identity);
        WmStorageLocation location = new WmStorageLocation(); location.setLocationId(7L); location.setLocationCode("A-01");
        location.setWarehouseId(5L); location.setTenantId(TENANT);
        when(locations.selectOne(any())).thenReturn(location);
        when(posting.handlesShelf(101L)).thenAnswer(i -> { assertNoTransaction(); events.add("classify"); return certified; });
        when(posting.shelf(101L)).thenAnswer(i -> {
            assertNoTransaction(); assertThat(commits).isPositive(); assertThat(committed.getLocationId()).isEqualTo(7L);
            events.add("nativeShelf");
            if (nativeFailure) throw new ServiceException("真实V2或原生预检未通过");
            if (nativeFalse) return false;
            status = "SHELVED"; return true;
        });
        when(legacy.confirmShelf(101L)).thenAnswer(i -> {
            assertInTransaction(); assertThat(commits).isZero(); events.add("legacyShelf");
            assertThat(TransactionSynchronizationManager.getResource(datasource)).isSameAs(allocationHolder);
            assertThat(pending == null ? committed.getLocationId() : pending.getLocationId()).isEqualTo(7L);
            if (legacyFailure) throw new ServiceException("旧质检上架失败");
            if (legacyFalse) return false;
            pendingStatus = "SHELVED"; return true;
        });
        when(facade.selectById(101L)).thenAnswer(i -> head());
        when(facade.confirmShelf(101L)).thenThrow(new ServiceException("分配事务内不得重新调用facade分类"));
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("receiveService", facade); beans.registerSingleton("receiveMapper", heads);
        beans.registerSingleton("lineMapper", lines); beans.registerSingleton("detailMapper", details);
        beans.registerSingleton("locationMapper", locations); beans.registerSingleton("putawayStrategyService", mock(IPutawayStrategyService.class));
        beans.registerSingleton("arrivalNoticeLineMapper", mock(WmArrivalNoticeLineMapper.class));
        beans.registerSingleton("certifiedReceiptGuard", transactional(new WmCertifiedReceiptMutationGuard(frozen)));
        beans.registerSingleton("certifiedPosting", posting);
        beans.registerSingleton("crudService", legacyProxy());
        // 修复前不提供尚不存在的新类；旧真实入口仍能装配并在行为断言处 RED。
        try {
            Class<?> type = Class.forName(ALLOCATION_CLASS);
            allocator = transactional(beans.createBean(type, AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR, false));
            beans.registerSingleton("shelfAllocation", allocator);
        } catch (ClassNotFoundException absentBeforeFix) { allocator = null; }
        service = transactional((PdaReceiveServiceImpl) beans.createBean(PdaReceiveServiceImpl.class,
            AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR, false));
    }

    @SuppressWarnings("unchecked") private <T> T transactional(T target) {
        ProxyFactory proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (T) proxy.getProxy();
    }
    private IWmMaterialReceiveCrudService legacyProxy() {
        ProxyFactory proxy = new ProxyFactory(); proxy.setTarget(legacy); proxy.addInterface(IWmMaterialReceiveCrudService.class);
        NameMatchTransactionAttributeSource attributes = new NameMatchTransactionAttributeSource();
        attributes.addTransactionalMethod("confirmShelf", new RuleBasedTransactionAttribute());
        proxy.addAdvice(new TransactionInterceptor(transactions, attributes));
        return (IWmMaterialReceiveCrudService) proxy.getProxy();
    }
    private void assertInTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        assertThat(TransactionSynchronizationManager.getResource(datasource)).isNotNull();
    }
    private void assertNoTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
        assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
    }
    @AfterEach void clear() { if (tenant != null) tenant.close(); assertNoTransaction(); }
    private PdaShelfBo shelf() { PdaShelfBo bo = new PdaShelfBo(); bo.setLocationCode("A-01"); return bo; }
    private WmMaterialReceiveVo head() {
        WmMaterialReceiveVo head = new WmMaterialReceiveVo(); head.setReceiveId(101L); head.setWarehouseId(5L); head.setStatus(status);
        return head;
    }
    private WmMaterialReceiveLine line() {
        WmMaterialReceiveLine line = new WmMaterialReceiveLine(); line.setTenantId(TENANT); line.setReceiveId(101L);
        line.setLineId(701L); line.setMaterialId(201L); line.setWarehouseId(5L); line.setBaseUomId("301");
        line.setQuantityReceived(BigDecimal.TEN); return line;
    }
    private WmMaterialReceiveDetail allocation() {
        WmMaterialReceiveDetail detail = new WmMaterialReceiveDetail(); detail.setTenantId(TENANT); detail.setReceiveId(101L);
        detail.setDetailId(901L); detail.setLineId(701L); detail.setMaterialId(201L); detail.setQuantity(BigDecimal.TEN);
        return detail;
    }
    private WmMaterialReceiveDetail copy(WmMaterialReceiveDetail original) {
        WmMaterialReceiveDetail detail = new WmMaterialReceiveDetail();
        detail.setTenantId(original.getTenantId()); detail.setReceiveId(original.getReceiveId()); detail.setDetailId(original.getDetailId());
        detail.setLineId(original.getLineId()); detail.setMaterialId(original.getMaterialId()); detail.setQuantity(original.getQuantity());
        detail.setLocationId(original.getLocationId()); detail.setLocationCode(original.getLocationCode()); return detail;
    }
    private void noSourceOrPhysicalPosting() {
        verify(lines, never()).update(any(), any()); verify(heads, never()).update(any(), any());
        verify(facade, never()).confirmShelf(anyLong()); verify(facade, never()).executeReceive(anyLong());
        verify(posting, never()).post(anyLong()); verify(posting, never()).handles(anyLong());
        assertThat(status).isNotEqualTo("FINISHED");
        if (committed != null) assertThat(committed.getQuantity()).isEqualByComparingTo("10");
    }

    @Test void certifiedClassificationHasNoOuterTransactionAndPostingRunsOnlyAfterAllocationCommit() {
        assertThat(service.shelf(101L, shelf()).getOk()).isTrue();
        assertThat(events).containsExactly("classify", "allocation", "commit", "nativeShelf");
        assertThat(commits).isEqualTo(1); assertThat(rollbacks).isZero(); noSourceOrPhysicalPosting();
    }
    @Test void nativeShelfFailureRetainsCommittedAllocationAndApprovedStateWithoutClaimingCompletion() {
        nativeFailure = true;
        var result = service.shelf(101L, shelf());
        assertThat(result.getOk()).isFalse(); assertThat(result.getNextAction()).isNull();
        assertThat(result.getMessage()).contains("分配已提交", "重试");
        assertThat(commits).isEqualTo(1); assertThat(rollbacks).isZero();
        assertThat(committed.getLocationId()).isEqualTo(7L); assertThat(status).isEqualTo("APPROVED"); noSourceOrPhysicalPosting();
    }
    @Test void retryAfterNativeFailureRechecksSameCommittedAllocationWithoutNewQuantityOrDuplicateInsert() {
        nativeFailure = true; assertThat(service.shelf(101L, shelf()).getOk()).isFalse();
        nativeFailure = false; assertThat(service.shelf(101L, shelf()).getOk()).isTrue();
        assertThat(events).containsExactly("classify", "allocation", "commit", "nativeShelf", "classify", "commit", "nativeShelf");
        verify(details, never()).insert(any(WmMaterialReceiveDetail.class));
        assertThat(commits).isEqualTo(2); noSourceOrPhysicalPosting();
    }
    @Test void legacyAllocationAndDirectCrudConfirmationCommitAsOneOrdinaryErpTransaction() {
        certified = false;
        assertThat(service.shelf(101L, shelf()).getOk()).isTrue();
        assertThat(events).containsExactly("classify", "allocation", "legacyShelf", "commit");
        assertThat(status).isEqualTo("SHELVED"); assertThat(commits).isEqualTo(1);
        verify(posting, never()).shelf(anyLong()); noSourceOrPhysicalPosting();
    }
    @Test void legacyConfirmationFailureRollsBackTheAllocationAndKeepsApproved() {
        certified = false; legacyFailure = true;
        assertThatThrownBy(() -> service.shelf(101L, shelf())).isInstanceOf(ServiceException.class);
        assertThat(commits).isZero(); assertThat(rollbacks).isEqualTo(1);
        assertThat(committed.getLocationId()).isNull(); assertThat(status).isEqualTo("APPROVED");
        verify(legacy).confirmShelf(101L); verify(posting, never()).shelf(anyLong()); noSourceOrPhysicalPosting();
    }
    @Test void legacyFalseResultMustRollbackRatherThanCommitAFalseCompletion() {
        certified = false; legacyFalse = true;
        assertThatThrownBy(() -> service.shelf(101L, shelf())).isInstanceOf(ServiceException.class);
        assertThat(commits).isZero(); assertThat(rollbacks).isEqualTo(1); assertThat(committed.getLocationId()).isNull();
        noSourceOrPhysicalPosting();
    }
    @Test void failedAllocationCasRollsBackBeforeTheNativeShelfCall() {
        changedRows = 0;
        assertThatThrownBy(() -> service.shelf(101L, shelf())).isInstanceOf(ServiceException.class);
        assertThat(commits).isZero(); assertThat(rollbacks).isEqualTo(1);
        verify(posting, never()).shelf(anyLong()); assertThat(committed.getLocationId()).isNull(); noSourceOrPhysicalPosting();
    }
    @Test void allocationFailureDoesNotReachEitherCompletionEntry() {
        when(frozen.lockLineIdentity(TENANT, 701L)).thenReturn(new SourceIdentity(TENANT, 202L, 701L));
        assertThatThrownBy(() -> service.shelf(101L, shelf())).isInstanceOf(ServiceException.class);
        assertThat(commits).isZero(); verify(posting, never()).shelf(anyLong()); verify(legacy, never()).confirmShelf(anyLong());
        verify(details, never()).update(any(), any()); noSourceOrPhysicalPosting();
    }
    @Test void allocationRejectedBusinessStateDoesNotAttemptNativeShelf() {
        status = "REJECTED";
        assertThat(service.shelf(101L, shelf()).getOk()).isFalse();
        verify(posting, never()).shelf(anyLong()); verify(legacy, never()).confirmShelf(anyLong()); noSourceOrPhysicalPosting();
    }
    @Test void missingAllocationMayInsertOnlyTheCurrentUnchangedReceiptQuantity() {
        committed = null;
        assertThat(service.shelf(101L, shelf()).getOk()).isTrue();
        assertThat(committed.getQuantity()).isEqualByComparingTo("10");
        assertThat(committed.getReceiveId()).isEqualTo(101L); assertThat(committed.getLineId()).isEqualTo(701L);
        noSourceOrPhysicalPosting();
    }
    @Test void existingAllocationUpdateIsRestrictedToLocationColumns() {
        assertThat(service.shelf(101L, shelf()).getOk()).isTrue();
        verify(details).update(isNull(), any()); verify(details, never()).updateById(any(WmMaterialReceiveDetail.class));
        noSourceOrPhysicalPosting();
    }
    @Test void ordinaryTransactionCallerCannotBeSilentlySuspendedOrCommittedByTheDispatcher() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            assertThatThrownBy(() -> service.shelf(101L, shelf())).isInstanceOf(ServiceException.class);
            verifyNoInteractions(posting); verify(details, never()).update(any(), any());
        });
        assertThat(events).doesNotContain("allocation", "nativeShelf");
    }
    @Test void legacyClassificationCannotAuthorizeAnOrderThatIsNowCertifiedUnderTheHeadLock() {
        certified = false;
        when(frozen.lockCertifiedOrderIdentities(TENANT, 41L)).thenReturn(List.of(41L));
        assertThatThrownBy(() -> service.shelf(101L, shelf())).isInstanceOf(ServiceException.class);
        assertThat(commits).isZero(); assertThat(rollbacks).isEqualTo(1);
        verify(details, never()).update(any(), any()); verify(details, never()).insert(any(WmMaterialReceiveDetail.class));
        verify(legacy, never()).confirmShelf(anyLong()); verify(posting, never()).shelf(anyLong()); noSourceOrPhysicalPosting();
    }
    @Test void nativeFalseMustNotReportFinishedOrUndoTheCommittedAllocation() {
        nativeFalse = true;
        var result = service.shelf(101L, shelf());
        assertThat(result.getOk()).isFalse(); assertThat(result.getNextAction()).isNull();
        assertThat(commits).isEqualTo(1); assertThat(rollbacks).isZero();
        assertThat(committed.getLocationId()).isEqualTo(7L); assertThat(status).isEqualTo("APPROVED"); noSourceOrPhysicalPosting();
    }
    @Test void allocatorRequiresTwoRealSpringTransactionEntrypointsWithoutAFacadeDependency() throws Exception {
        assertThat(allocator).isNotNull();
        Class<?> type = Class.forName(ALLOCATION_CLASS);
        assertThat(type.getMethod("allocateCertified", Long.class, PdaShelfBo.class).getAnnotation(Transactional.class)).isNotNull();
        assertThat(type.getMethod("shelfLegacy", Long.class, PdaShelfBo.class).getAnnotation(Transactional.class)).isNotNull();
        for (var field : type.getDeclaredFields()) assertThat(field.getType()).isNotEqualTo(IWmMaterialReceiveService.class);
        assertThat(PdaReceiveServiceImpl.class.getMethod("shelf", Long.class, PdaShelfBo.class).getAnnotation(Transactional.class)).isNull();
    }
}