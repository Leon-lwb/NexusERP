package com.nexus.erp.fin.cost.poster;

import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationInterceptor;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.erp.fin.cost.controller.CostController;
import com.nexus.erp.fin.cost.service.CostService;
import com.nexus.erp.fin.gl.constant.GlConstants;
import com.nexus.erp.fin.gl.domain.*;
import com.nexus.erp.fin.gl.domain.vo.GlVoucherVo;
import com.nexus.erp.fin.gl.mapper.*;
import com.nexus.erp.fin.gl.service.*;
import com.nexus.erp.fin.gl.service.impl.GlVoucherServiceImpl;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.support.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 实际 controller/poster/GL + Spring 代理的离线测试。
 * 存储、行锁及事务资源为内存模型；不证明物理数据库并发或回滚，不验证制造会计政策。
 */
@Tag("dev")
class CostPosterPostingSafetyTest {
    private Harness h;
    private MockedStatic<LoginHelper> login;

    @BeforeAll
    static void metadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        for (Class<?> type : List.of(WmMaterialStockTx.class, GlVoucher.class, GlVoucherLine.class, GlAccount.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    @BeforeEach
    void setUp() {
        login = mockStatic(LoginHelper.class);
        login.when(LoginHelper::getUserId).thenReturn(99L);
        h = new Harness();
    }

    @AfterEach
    void cleanUp() {
        login.close();
        DynamicDataSourceContextHolder.clear();
    }

    @ParameterizedTest
    @CsvSource({"PRODUCTION_ISSUE,5001,1405", "PRODUCTION_IN,1405,2202"})
    void normalControllerPathLocksAndUsesOneErpSpringTransactionWithoutChangingTemplates(
        String type, String debitCode, String creditCode) {
        h.type = type;
        h.controller.postTx(901L);
        assertEquals(1, h.manager.begins.get(), "库存锁读、期间、GL 及标记必须是同一 Spring 事务");
        assertEquals(1, h.manager.commits.get());
        assertEquals(1, h.posted);
        assertEquals(1, h.vouchers.size());
        assertEquals(0, h.unlockedReads.get(), "正常路径不得普通 selectById 读取待过账流水");
        assertTrue(h.events.stream().allMatch(e -> e.active && "erp".equals(e.datasource)),
            "全部数据库端口调用必须位于 ERP 事务内：" + h.events);
        assertTrue(h.markerSql.contains("POSTED") && h.markerSql.contains("IS NULL")
            && h.markerSql.contains("TX_ID"), "回写必须绑定流水并只认领未过账/旧 null 状态");
        assertEquals(debitCode, h.details.get(0).getAccountCode(), "固定原借方模板，不引入新政策");
        assertEquals(creditCode, h.details.get(1).getAccountCode(), "固定原贷方模板，制造政策仍未验收");
        assertEquals(new BigDecimal("12.35"), h.vouchers.values().iterator().next().getTotalDebit(),
            "保留单位成本乘数量、两位 HALF_UP 模板");
        assertEquals("COST-901", h.markerNo, "本轮不扩展凭证编号回写模板");
        assertNull(DynamicDataSourceContextHolder.peek(), "调用结束必须清理测试 DS 上下文");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void markerMustAffectExactlyOneRowAndRollBackWholeChain(int rows) {
        h.markerRows = rows;
        assertThrows(ServiceException.class, () -> h.controller.postTx(901L), "标记非恰好一行必须失败");
        assertEmptyRolledBackChain();
    }

    @ParameterizedTest
    @ValueSource(strings = {"head", "line", "balance", "marker"})
    void anyWriteStageFailureRollsBackPeriodVoucherLinesBalanceAndMarker(String stage) {
        h.failure = stage;
        assertThrows(ServiceException.class, () -> h.controller.postTx(901L), "写阶段异常必须整体回滚：" + stage);
        assertEmptyRolledBackChain();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "CANCELLED"})
    void existingNonPostedSourceCannotMarkInventoryPosted(String status) {
        h.seed(status, 7L);
        assertThrows(ServiceException.class, () -> h.controller.postTx(901L), "草稿/作废不能当作已过账来源");
        assertEquals(0, h.posted);
        assertEquals(1, h.vouchers.size(), "不得创建或删除既有异常凭证");
        assertEquals(status, h.vouchers.get(7L).getStatus());
        assertEquals(0, h.periods);
        assertEquals(BigDecimal.ZERO, h.balance);
        assertEquals(1, h.manager.rollbacks.get());
    }

    @Test
    void duplicatedSourceCannotMarkInventoryPosted() {
        h.seed(GlConstants.VOUCHER_STATUS_POSTED, 7L);
        h.seed(GlConstants.VOUCHER_STATUS_POSTED, 8L);
        assertThrows(ServiceException.class, () -> h.controller.postTx(901L), "多张来源凭证必须停止");
        assertEquals(0, h.posted);
        assertEquals(2, h.vouchers.size());
        assertEquals(BigDecimal.ZERO, h.balance);
    }

    @Test
    void uniquePostedSourceCanBeClaimedWithoutApplyingBalanceAgain() {
        h.seed(GlConstants.VOUCHER_STATUS_POSTED, 7L);
        h.controller.postTx(901L);
        assertEquals(1, h.posted);
        assertEquals(1, h.vouchers.size());
        assertEquals(BigDecimal.ZERO, h.balance, "认领既有 POSTED 不得重复增加余额");
    }

    @Test
    void missingPostedVoucherResultCannotSilentlyMarkInventory() {
        h.seed(GlConstants.VOUCHER_STATUS_POSTED, 7L);
        h.missingVo = true;
        assertThrows(ServiceException.class, () -> h.controller.postTx(901L), "缺失已过账结果不能标记流水");
        assertEquals(0, h.posted);
        assertEquals(0, h.periods, "失败认领时派生期间也必须回滚");
    }

    @Test
    void legacyNullPendingMarkerCanBeClaimedConditionally() {
        h.posted = null;
        h.controller.postTx(901L);
        assertEquals(1, h.posted, "保留旧 null 未过账兼容性，但仍须条件回写");
        assertTrue(h.markerSql.contains("IS NULL"));
    }

    @Test
    void twoConcurrentCallsShareOneInventoryRowLockModel() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Void> call = () -> {
                // Mockito 静态 mock 线程隔离，两个离线线程也不得进入真实认证调用。
                try (MockedStatic<LoginHelper> workerLogin = mockStatic(LoginHelper.class)) {
                    workerLogin.when(LoginHelper::getUserId).thenReturn(99L);
                    start.await(); h.controller.postTx(901L); return null;
                }
            };
            Future<Void> first = executor.submit(call);
            Future<Void> second = executor.submit(call);
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertEquals(1, h.vouchers.size(), "模型同 tx 只允许制单一次，不是物理 DB 并发验收");
            assertEquals(1, h.markerWrites.get(), "第二个调用锁读后须看到已过账并跳过");
            assertEquals(0, h.unlockedReads.get());
        } finally { executor.shutdownNow(); }
    }

    private void assertEmptyRolledBackChain() {
        assertEquals(0, h.posted);
        assertEquals(0, h.periods, "最早派生期间也须在外层事务中回滚");
        assertTrue(h.vouchers.isEmpty(), "凭证头必须回滚");
        assertTrue(h.details.isEmpty(), "凭证明细必须回滚");
        assertEquals(BigDecimal.ZERO, h.balance, "余额端口模型必须回滚");
        assertEquals(1, h.manager.rollbacks.get(), "同一 Spring 事务必须回滚");
        assertEquals(0, h.manager.commits.get());
    }

    private record Event(String stage, boolean active, String datasource) { }
    private record Snapshot(Integer posted, int periods, Map<Long, GlVoucher> vouchers,
                            List<GlVoucherLine> details, BigDecimal balance, String markerNo) { }

    private static final class Harness {
        private volatile Integer posted = 0;
        private volatile int periods;
        private String type = "PRODUCTION_IN";
        private String failure = "";
        private int markerRows = 1;
        private boolean missingVo;
        private BigDecimal balance = BigDecimal.ZERO;
        private String markerSql = "";
        private String markerNo;
        private final Map<Long, GlVoucher> vouchers = new ConcurrentHashMap<>();
        private final List<GlVoucherLine> details = Collections.synchronizedList(new ArrayList<>());
        private final List<Event> events = Collections.synchronizedList(new ArrayList<>());
        private final ReentrantLock rowLock = new ReentrantLock();
        private final AtomicInteger unlockedReads = new AtomicInteger();
        private final AtomicInteger markerWrites = new AtomicInteger();
        private final AtomicInteger ids = new AtomicInteger(100);
        private final MemoryTransactions manager = new MemoryTransactions(this);
        private final CostController controller;

        @SuppressWarnings("unchecked")
        private Harness() {
            WmMaterialStockTxMapper stockTx = mock(WmMaterialStockTxMapper.class);
            WmMaterialStockMapper stock = mock(WmMaterialStockMapper.class);
            GlAccountMapper accounts = mock(GlAccountMapper.class);
            GlVoucherMapper voucherMapper = mock(GlVoucherMapper.class);
            GlVoucherLineMapper lineMapper = mock(GlVoucherLineMapper.class);
            IGlPeriodService periodService = mock(IGlPeriodService.class);
            IGlAccountBalanceService balances = mock(IGlAccountBalanceService.class);
            when(stockTx.selectById(anyLong())).thenAnswer(inv -> { unlockedReads.incrementAndGet(); event("普通流水读取"); return tx(); });
            when(stockTx.selectOne(any(Wrapper.class))).thenAnswer(inv -> {
                Wrapper<?> wrapper = inv.getArgument(0);
                assertTrue(wrapper.getSqlSegment().toUpperCase(Locale.ROOT).endsWith("FOR UPDATE"), "流水必须锁定当前读");
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), "库存锁须在 Spring 事务中持有");
                rowLock.lock();
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) { rowLock.unlock(); }
                });
                event("锁定流水读取"); return tx();
            });
            when(stockTx.update(isNull(), any(Wrapper.class))).thenAnswer(inv -> {
                event("流水标记");
                markerSql = ((Wrapper<?>) inv.getArgument(1)).getSqlSegment().toUpperCase(Locale.ROOT);
                if (markerRows == 1) { posted = 1; markerNo = "COST-901"; markerWrites.incrementAndGet(); }
                fail("marker"); return markerRows;
            });
            WmMaterialStock record = new WmMaterialStock(); record.setMaterialType(3L);
            when(stock.selectById(anyLong())).thenAnswer(inv -> { event("库存类型"); return record; });
            when(accounts.selectOne(any(Wrapper.class))).thenAnswer(inv -> {
                event("科目编码");
                var wrapper = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GlAccount>) inv.getArgument(0);
                wrapper.getSqlSegment();
                String code = wrapper.getParamNameValuePairs().values().iterator().next().toString();
                return account(Long.parseLong(code));
            });
            when(accounts.selectById(anyLong())).thenAnswer(inv -> { event("末级科目"); return account(inv.getArgument(0)); });
            when(periodService.getOrDerivePeriod(any())).thenAnswer(inv -> {
                event("派生期间"); periods = 1;
                GlPeriod period = new GlPeriod(); period.setPeriodId(9L); period.setStatus(GlConstants.PERIOD_OPEN); return period;
            });
            when(voucherMapper.selectOne(any(Wrapper.class))).thenAnswer(inv -> {
                event("旧来源读取"); return vouchers.values().stream().findFirst().orElse(null);
            });
            when(voucherMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> {
                event("来源当前读取"); return new ArrayList<>(vouchers.values());
            });
            when(voucherMapper.insert(any(GlVoucher.class))).thenAnswer(inv -> {
                event("凭证头"); GlVoucher voucher = inv.getArgument(0); voucher.setVoucherId((long) ids.incrementAndGet());
                vouchers.put(voucher.getVoucherId(), voucher); fail("head"); return 1;
            });
            when(lineMapper.insert(any(GlVoucherLine.class))).thenAnswer(inv -> {
                event("凭证行"); details.add(inv.getArgument(0)); fail("line"); return 1;
            });
            when(voucherMapper.selectVoById(any())).thenAnswer(inv -> {
                if (missingVo) { return null; }
                GlVoucher voucher = vouchers.get(inv.getArgument(0)); if (voucher == null) { return null; }
                GlVoucherVo vo = new GlVoucherVo(); vo.setVoucherId(voucher.getVoucherId());
                vo.setVoucherNo(voucher.getVoucherNo()); vo.setStatus(voucher.getStatus()); return vo;
            });
            when(lineMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> new ArrayList<>(details));
            doAnswer(inv -> { event("余额写入"); balance = balance.add(new BigDecimal("12.35")); fail("balance"); return null; })
                .when(balances).applyPosting(anyLong(), anyList());
            GlVoucherServiceImpl gl = new GlVoucherServiceImpl(voucherMapper, lineMapper, accounts,
                mock(GlPeriodMapper.class), periodService, balances);
            IGlVoucherService glProxy = proxy(gl);
            CostPoster poster = new CostPoster(stockTx, stock, mock(com.nexus.erp.bd.mapper.BdMaterialMapper.class),
                accounts, mock(GlPeriodMapper.class), periodService, glProxy);
            controller = new CostController(proxy(poster), mock(CostService.class), mock(WmMaterialReceiveMapper.class));
        }
        private void event(String stage) { events.add(new Event(stage,
            TransactionSynchronizationManager.isActualTransactionActive(), DynamicDataSourceContextHolder.peek())); }
        private void fail(String stage) { if (failure.equals(stage)) { throw new ServiceException("测试注入写异常：" + stage); } }
        private WmMaterialStockTx tx() {
            WmMaterialStockTx tx = new WmMaterialStockTx(); tx.setTxId(901L); tx.setPosted(posted);
            tx.setTxType(type); tx.setMaterialStockId(19L); tx.setTxDate(new Date(0));
            tx.setUnitCost(new BigDecimal("1.234567")); tx.setTxQuantity(new BigDecimal("10"));
            tx.setRemark("测试来源"); tx.setMaterialName("测试物料"); return tx;
        }
        private GlAccount account(Long id) {
            GlAccount account = new GlAccount(); account.setAccountId(id); account.setAccountCode(id.toString());
            account.setAccountName("测试科目"); account.setIsLeaf(GlConstants.YES); return account;
        }
        private void seed(String status, Long id) {
            GlVoucher voucher = new GlVoucher(); voucher.setVoucherId(id); voucher.setVoucherSource(GlConstants.SOURCE_COST);
            voucher.setSourceId(901L); voucher.setStatus(status); vouchers.put(id, voucher);
        }
        @SuppressWarnings("unchecked")
        private <T> T proxy(T target) {
            ProxyFactory factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
            // 实际 DS advice 先于事务 advice；固定 erp，不启动真实数据源。
            factory.addAdvice(new DynamicDataSourceAnnotationInterceptor(true, null));
            factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
            return (T) factory.getProxy();
        }
        private synchronized Snapshot snapshot() { return new Snapshot(posted, periods,
            new HashMap<>(vouchers), new ArrayList<>(details), balance, markerNo); }
        private synchronized void restore(Snapshot s) {
            posted = s.posted; periods = s.periods; vouchers.clear(); vouchers.putAll(s.vouchers);
            details.clear(); details.addAll(s.details); balance = s.balance; markerNo = s.markerNo;
        }
    }

    /** Spring 事务资源的快照模型：故障用例无并发；并发只验证成功路径的模型行锁。 */
    private static final class MemoryTransactions extends AbstractPlatformTransactionManager {
        private final Harness h;
        private final ThreadLocal<State> state = new ThreadLocal<>();
        private final AtomicInteger begins = new AtomicInteger();
        private final AtomicInteger commits = new AtomicInteger();
        private final AtomicInteger rollbacks = new AtomicInteger();
        private MemoryTransactions(Harness h) { this.h = h; }
        @Override protected Object doGetTransaction() {
            State current = state.get(); if (current == null) { current = new State(); state.set(current); } return current;
        }
        @Override protected boolean isExistingTransaction(Object transaction) { return ((State) transaction).active; }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
            State s = (State) transaction; s.active = true; s.before = h.snapshot(); begins.incrementAndGet();
        }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits.incrementAndGet(); }
        @Override protected void doRollback(DefaultTransactionStatus status) {
            h.restore(((State) status.getTransaction()).before); rollbacks.incrementAndGet();
        }
        @Override protected void doSetRollbackOnly(DefaultTransactionStatus status) { ((State) status.getTransaction()).rollback = true; }
        @Override protected void doCleanupAfterCompletion(Object transaction) { ((State) transaction).active = false; state.remove(); }
        private static final class State implements SmartTransactionObject {
            private boolean active;
            private boolean rollback;
            private Snapshot before;
            @Override public boolean isRollbackOnly() { return rollback; }
            @Override public void flush() { }
        }
    }
}
