package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.so.domain.SoSalesOrderHeader;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.ISoFulfillmentWritebackService;
import com.nexus.erp.wm.domain.*;
import com.nexus.erp.wm.mapper.*;
import com.nexus.erp.wm.service.IWmSalesIssueService;
import com.nexus.erp.wm.service.SalesIssueSourceGuard;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 离线入口夹具：真实销售出库与计价源码，加内存映射器和 Spring 事务代理。
 * <p>撤销日志及锁仅模拟调用方事务边界，不连接数据库，不证明物理 MySQL 的锁或回滚行为。</p>
 */
class SalesIssueEntryFixture {
    static final String TENANT = "000000";
    final WmSalesIssueMapper issuesMapper = mock(WmSalesIssueMapper.class);
    final WmSalesIssueLineMapper issueLinesMapper = mock(WmSalesIssueLineMapper.class);
    final WmMaterialStockMapper stockMapper = mock(WmMaterialStockMapper.class);
    final WmMaterialStockTxMapper txMapper = mock(WmMaterialStockTxMapper.class);
    final SalesIssueSourceGuardMapper sourceMapper = mock(SalesIssueSourceGuardMapper.class);
    final WmBatchMapper batchMapper = mock(WmBatchMapper.class);
    final SoSalesOrderLineMapper soLinesMapper = mock(SoSalesOrderLineMapper.class);
    final ISoFulfillmentWritebackService writeback = mock(ISoFulfillmentWritebackService.class);
    final Map<Long, WmSalesIssue> issues = new LinkedHashMap<>();
    final Map<Long, List<WmSalesIssueLine>> details = new LinkedHashMap<>();
    final Map<Long, SoSalesOrderLine> sources = new TreeMap<>();
    final Map<Long, WmMaterialStock> stocks = new LinkedHashMap<>();
    final List<WmMaterialStockTx> transactions = new CopyOnWriteArrayList<>();
    final List<String> events = new CopyOnWriteArrayList<>();
    final MemoryTransactions manager = new MemoryTransactions();
    final ReentrantLock headLock = new ReentrantLock();
    final SoSalesOrderHeader header = new SoSalesOrderHeader();
    final WmBatch batch = new WmBatch();
    final IWmSalesIssueService service;
    long existingTxCount;
    int batchRows = 1;
    int salesTxRows = 1;
    int reserveTxRows = 1;
    CyclicBarrier entryBarrier;

    SalesIssueEntryFixture() {
        header.setSoId(7L);
        header.setTenantId(TENANT);
        header.setClientId(2L);
        header.setOrderStatus("CONFIRMED");
        header.setDelFlag("0");
        sources.put(21L, source(21L, "10"));
        addIssue(1L, "10");
        stocks.put(9L, stock(9L, "10", "3.89"));
        batch.setBatchId(90L);
        batch.setMaterialId(100L);
        batch.setTenantId(TENANT);
        batch.setDelFlag("0");
        batch.setReceivedQty(new BigDecimal("10"));
        batch.setIssuedQty(BigDecimal.ZERO);
        stubBoundaries();
        WmSalesIssueServiceImpl target = new WmSalesIssueServiceImpl(issuesMapper, issueLinesMapper,
            stockMapper, txMapper, mock(WmServiceHelper.class), writeback,
            mock(ReliableMessageProducer.class), mock(BdMaterialMapper.class), soLinesMapper,
            new MovingAverageValuationServiceImpl(stockMapper), new SalesIssueSourceGuard(sourceMapper), batchMapper);
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        service = (IWmSalesIssueService) factory.getProxy();
    }

    int complete(long id) {
        try (MockedStatic<TenantHelper> tenant = mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::getTenantId).thenReturn(TENANT);
            return service.completeSalesIssue(id);
        }
    }

    WmSalesIssue addIssue(long id, String qty) {
        WmSalesIssue issue = new WmSalesIssue();
        issue.setIssueId(id);
        issue.setIssueCode("销售出库-" + id);
        issue.setStatus("CONFIRMED");
        issue.setSoId(7L);
        issue.setClientId(2L);
        issue.setWarehouseId(5L);
        issue.setTenantId(TENANT);
        issue.setDelFlag("0");
        issues.put(id, issue);
        details.put(id, new ArrayList<>(List.of(detail(id, 100L + id, qty))));
        return issue;
    }

    WmSalesIssueLine detail(long issueId, long lineId, String qty) {
        WmSalesIssueLine line = new WmSalesIssueLine();
        line.setLineId(lineId);
        line.setIssueId(issueId);
        line.setTenantId(TENANT);
        line.setDelFlag("0");
        line.setMaterialId(100L);
        line.setMaterialName("成品");
        line.setQuantityIssue(qty == null ? null : new BigDecimal(qty));
        line.setSourceDocLineId(21L);
        return line;
    }

    SoSalesOrderLine source(long id, String qty) {
        SoSalesOrderLine line = new SoSalesOrderLine();
        line.setLineId(id);
        line.setSoId(7L);
        line.setTenantId(TENANT);
        line.setDelFlag("0");
        line.setMaterialId(100L);
        line.setOrderedQty(new BigDecimal(qty));
        line.setDeliveredQty(BigDecimal.ZERO);
        line.setFulfilledQty(BigDecimal.ZERO);
        line.setReservedQty(BigDecimal.ZERO);
        line.setLineStatus("SCHEDULED");
        line.setOqcStatus("QUALIFIED");
        return line;
    }

    WmMaterialStock stock(long id, String qty, String cost) {
        WmMaterialStock stock = new WmMaterialStock();
        stock.setStockId(id);
        stock.setTenantId(TENANT);
        stock.setMaterialId(100L);
        stock.setWarehouseId(5L);
        stock.setDelFlag("0");
        stock.setBatchId(90L);
        stock.setStockQuantity(new BigDecimal(qty));
        stock.setReservedQuantity(BigDecimal.ZERO);
        stock.setUnitCost(new BigDecimal(cost));
        stock.setTotalCost(new BigDecimal(qty).multiply(new BigDecimal(cost)).setScale(4, RoundingMode.HALF_UP));
        stock.setVersion(0);
        return stock;
    }

    private void stubBoundaries() {
        when(sourceMapper.lockHeader(anyString(), anyLong())).thenAnswer(a -> {
            events.add("尝试锁头");
            manager.lock(headLock);
            events.add("锁头");
            if (!Objects.equals(header.getTenantId(), a.getArgument(0))
                || !Objects.equals(header.getSoId(), a.getArgument(1)) || !"0".equals(header.getDelFlag())) return null;
            return copy(header, SoSalesOrderHeader.class);
        });
        when(sourceMapper.lockLines(anyString(), anyLong(), anyList())).thenAnswer(a -> {
            if (!headLock.isHeldByCurrentThread()) throw new AssertionError("必须先锁头再锁行");
            List<Long> ids = a.getArgument(2);
            if (!ids.equals(ids.stream().sorted().toList())) throw new AssertionError("来源行必须按行 ID 有序锁定");
            events.add("锁行:" + ids);
            return ids.stream().map(sources::get).filter(Objects::nonNull)
                .filter(row -> Objects.equals(row.getTenantId(), a.getArgument(0))
                    && Objects.equals(row.getSoId(), a.getArgument(1)) && "0".equals(row.getDelFlag()))
                .map(row -> copy(row, SoSalesOrderLine.class)).toList();
        });
        when(issuesMapper.selectById(any())).thenAnswer(a -> copy(issues.get(a.getArgument(0)), WmSalesIssue.class));
        when(issueLinesMapper.selectList(any())).thenAnswer(a -> {
            var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) a.getArgument(0);
            query.getSqlSegment();
            Long id = (Long) query.getParamNameValuePairs().values().stream().filter(Long.class::isInstance).findFirst().orElseThrow();
            return details.get(id);
        });
        when(issuesMapper.update(isNull(), any())).thenAnswer(a -> {
            LambdaUpdateWrapper<?> wrapper = a.getArgument(1);
            wrapper.getSqlSegment();
            Long id = (Long) wrapper.getParamNameValuePairs().values().stream().filter(Long.class::isInstance).findFirst().orElseThrow();
            WmSalesIssue issue = issues.get(id);
            synchronized (issue) {
                if (!"CONFIRMED".equals(issue.getStatus())) return 0;
                String old = issue.getStatus();
                manager.undo(() -> issue.setStatus(old));
                issue.setStatus("EXECUTING");
                events.add("认领:" + id);
                return 1;
            }
        });
        when(issuesMapper.updateById(any(WmSalesIssue.class))).thenAnswer(a -> {
            WmSalesIssue changed = a.getArgument(0);
            WmSalesIssue stored = issues.get(changed.getIssueId());
            String old = stored.getStatus();
            manager.undo(() -> stored.setStatus(old));
            stored.setStatus(changed.getStatus());
            return 1;
        });
        when(txMapper.selectCount(any())).thenAnswer(a -> {
            if (entryBarrier != null) {
                try { entryBarrier.await(5, TimeUnit.SECONDS); }
                catch (Exception ex) { throw new AssertionError("内存并发入口等待失败", ex); }
            }
            return existingTxCount;
        });
        when(stockMapper.selectList(any())).thenAnswer(a -> {
            events.add("查询库存");
            var query = (com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?>) a.getArgument(0);
            query.getSqlSegment();
            Collection<Object> values = query.getParamNameValuePairs().values();
            return stocks.values().stream().filter(s -> s.getStockQuantity().signum() > 0 && values.contains(s.getWarehouseId()))
                .map(s -> copy(s, WmMaterialStock.class)).toList();
        });
        when(stockMapper.selectById(any())).thenAnswer(a -> copy(stocks.get(a.getArgument(0)), WmMaterialStock.class));
        when(stockMapper.deductStockAvailable(anyLong(), any(), any())).thenAnswer(a -> deduct(a.getArgument(0), a.getArgument(1), BigDecimal.ZERO, a.getArgument(2)));
        when(stockMapper.deductStockWithReserved(anyLong(), any(), any(), any())).thenAnswer(a -> deduct(a.getArgument(0), a.getArgument(1), a.getArgument(2), a.getArgument(3)));
        when(stockMapper.issueStockCost(anyLong(), any())).thenAnswer(a -> {
            WmMaterialStock stock = stocks.get(a.getArgument(0));
            WmMaterialStock before = copy(stock, WmMaterialStock.class);
            manager.undo(() -> BeanUtils.copyProperties(before, stock));
            BigDecimal qty = a.getArgument(1);
            stock.setTotalCost(stock.getTotalCost().subtract(stock.getUnitCost().multiply(qty)).setScale(4, RoundingMode.HALF_UP));
            return 1;
        });
        when(batchMapper.increaseIssuedQty(anyLong(), anyLong(), anyString(), any())).thenAnswer(a -> {
            events.add("批次累计");
            BigDecimal qty = a.getArgument(3);
            if (!Objects.equals(batch.getBatchId(), a.getArgument(0)) || !Objects.equals(batch.getMaterialId(), a.getArgument(1))
                || !Objects.equals(batch.getTenantId(), a.getArgument(2)) || !"0".equals(batch.getDelFlag())
                || qty.signum() <= 0 || batch.getIssuedQty().add(qty).compareTo(batch.getReceivedQty()) > 0) return 0;
            if (batchRows > 0) {
                BigDecimal old = batch.getIssuedQty();
                manager.undo(() -> batch.setIssuedQty(old));
                batch.setIssuedQty(old.add(qty));
            }
            return batchRows;
        });
        when(txMapper.insert(any(WmMaterialStockTx.class))).thenAnswer(a -> {
            WmMaterialStockTx tx = a.getArgument(0);
            int rows = "SALES_OUT".equals(tx.getTxType()) ? salesTxRows : reserveTxRows;
            if (rows > 0) {
                transactions.add(tx);
                manager.undo(() -> transactions.remove(tx));
            }
            return rows;
        });
        when(soLinesMapper.selectById(any())).thenAnswer(a -> copy(sources.get(a.getArgument(0)), SoSalesOrderLine.class));
        when(soLinesMapper.update(isNull(), any())).thenAnswer(a -> {
            LambdaUpdateWrapper<?> wrapper = a.getArgument(1);
            wrapper.getSqlSegment();
            var matcher = java.util.regex.Pattern.compile("line_id = #\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)\\}")
                .matcher(wrapper.getSqlSegment());
            if (!matcher.find()) throw new AssertionError("缺少明确的来源行更新条件");
            Long id = (Long) wrapper.getParamNameValuePairs().get(matcher.group(1));
            BigDecimal amount = new BigDecimal(wrapper.getSqlSet().substring(wrapper.getSqlSet().lastIndexOf(" - ") + 3));
            SoSalesOrderLine source = sources.get(id);
            if (source.getReservedQty().compareTo(amount) < 0) return 0;
            BigDecimal old = source.getReservedQty();
            manager.undo(() -> source.setReservedQty(old));
            source.setReservedQty(old.subtract(amount));
            return 1;
        });
        doAnswer(a -> {
            SoSalesOrderLine source = sources.get(a.getArgument(0));
            if (source == null) return null;
            SoSalesOrderLine old = copy(source, SoSalesOrderLine.class);
            manager.undo(() -> BeanUtils.copyProperties(old, source));
            BigDecimal qty = a.getArgument(1);
            synchronized (source) {
                source.setDeliveredQty(source.getDeliveredQty().add(qty));
                source.setFulfilledQty(source.getFulfilledQty().add(qty));
            }
            return null;
        }).when(writeback).addShippedQty(anyLong(), any());
    }

    private int deduct(Long id, BigDecimal qty, BigDecimal reserve, Integer version) {
        WmMaterialStock stock = stocks.get(id);
        if (!Objects.equals(version, stock.getVersion()) || stock.getReservedQuantity().compareTo(reserve) < 0
            || stock.getStockQuantity().subtract(stock.getReservedQuantity()).add(reserve).compareTo(qty) < 0) return 0;
        WmMaterialStock before = copy(stock, WmMaterialStock.class);
        manager.undo(() -> BeanUtils.copyProperties(before, stock));
        events.add("扣库");
        stock.setStockQuantity(stock.getStockQuantity().subtract(qty));
        stock.setReservedQuantity(stock.getReservedQuantity().subtract(reserve));
        stock.setVersion(version + 1);
        return 1;
    }

    static <T> T copy(T value, Class<T> type) {
        if (value == null) return null;
        try {
            T copied = type.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(value, copied);
            return copied;
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("夹具复制失败", ex);
        }
    }

    /** 仅提供内存撤销日志与事务结束释放锁，不具备数据库隔离级别语义。 */
    static final class MemoryTransactions extends AbstractPlatformTransactionManager {
        private final ThreadLocal<State> active = new ThreadLocal<>();
        static final class State {
            final List<Runnable> undo = new ArrayList<>();
            final List<ReentrantLock> locks = new ArrayList<>();
        }
        @Override protected Object doGetTransaction() { return active.get() == null ? new State() : active.get(); }
        @Override protected boolean isExistingTransaction(Object tx) { return tx == active.get(); }
        @Override protected void doBegin(Object tx, TransactionDefinition definition) { active.set((State) tx); }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) {
            List<Runnable> undo = ((State) status.getTransaction()).undo;
            for (int i = undo.size() - 1; i >= 0; i--) undo.get(i).run();
        }
        @Override protected void doCleanupAfterCompletion(Object tx) {
            List<ReentrantLock> locks = ((State) tx).locks;
            for (int i = locks.size() - 1; i >= 0; i--) locks.get(i).unlock();
            active.remove();
        }
        void undo(Runnable action) {
            if (active.get() == null) throw new AssertionError("写操作未进入原入口事务代理");
            active.get().undo.add(action);
        }
        void lock(ReentrantLock lock) {
            if (active.get() == null) throw new AssertionError("源单锁未进入原入口事务代理");
            lock.lock();
            active.get().locks.add(lock);
        }
    }
}
