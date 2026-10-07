package com.nexus.erp.wm;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmProductionRecpt;
import com.nexus.erp.wm.domain.bo.WmProductionRecptBo;
import com.nexus.erp.wm.mapper.WmProductionRecptGuardMapper;
import com.nexus.erp.wm.service.impl.WmProductionRecptServiceImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.Advised;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 真实入口、守卫、MyBatis 映射 SQL 和 REQUIRED 代理的离线读视图反例。
 * 模拟 JDBC 按实际执行的 SQL 区分旧 RR 快照与当前锁读；不是实库并发验收。
 */
class WmProductionRecptCurrentReadTest {
    private WmProductionRecptTransactionTest fixture;
    private WmProductionRecptServiceImpl service;
    private PrdWorkOrder wo;
    private DataSource dataSource;
    private DataSourceTransactionManager manager;
    private WmProductionRecptGuardMapper sqlMapper;
    private ReadViewJdbc jdbc;

    @BeforeAll
    static void tableInfo() {
        WmProductionRecptTransactionTest.tableInfo();
    }

    @BeforeEach
    void before() throws Exception {
        fixture = new WmProductionRecptTransactionTest();
        fixture.before();
        service = field("service");
        wo = field("wo");
        TransactionInterceptor interceptor = (TransactionInterceptor) ((Advised) service).getAdvisors()[0].getAdvice();
        manager = (DataSourceTransactionManager) interceptor.getTransactionManager();
        dataSource = Objects.requireNonNull(manager.getDataSource());
        Connection connection = dataSource.getConnection();
        jdbc = new ReadViewJdbc();
        when(connection.prepareStatement(anyString())).thenAnswer(call -> jdbc.statement(call.getArgument(0)));
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("离线读视图", new SpringManagedTransactionFactory(), dataSource));
        configuration.addMapper(WmProductionRecptGuardMapper.class);
        sqlMapper = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration))
            .getMapper(WmProductionRecptGuardMapper.class);
    }

    @AfterEach
    void after() {
        fixture.after();
    }

    @SuppressWarnings("unchecked")
    private <T> T field(String name) throws Exception {
        Field field = WmProductionRecptTransactionTest.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(fixture);
    }

    private void prepareDraft(String quantity) throws Exception {
        wo.setWoStatus("IN_PROGRESS");
        wo.setCompletedQty(BigDecimal.ZERO);
        wo.setActualEndDate(null);
        WmProductionRecptBo draft = new WmProductionRecptBo();
        draft.setWoId(201L); draft.setQuantity(d(quantity));
        draft.setWarehouseId(3L); draft.setLocationId(28L); draft.setRecptDate(new java.util.Date());
        service.insertByBo(draft);
        assertEquals("DRAFT", this.<WmProductionRecpt>field("stored").getStatus(), "先建立合法零效果草稿");
        WmProductionRecptGuardMapper source = field("source");
        when(source.findWorkOrder("000000", 101L)).thenAnswer(call -> sqlMapper.findWorkOrder("000000", 101L));
        when(source.lockWorkOrder("000000", 201L)).thenAnswer(call -> sqlMapper.lockWorkOrder("000000", 201L));
        when(source.approvedReportedQuantity("000000", 201L))
            .thenAnswer(call -> sqlMapper.approvedReportedQuantity("000000", 201L));
        clearInvocations(dataSource);
    }

    private Outcome execute(boolean outer) throws Exception {
        if (outer) {
            return new TransactionTemplate(manager).execute(status -> {
                // 普通 SELECT 先建立外层旧快照；随后服务的 REQUIRED 必须加入同一事务。
                sqlMapper.findWorkOrder("000000", 101L);
                Outcome result = runExecution();
                if (result.rejection() != null) status.setRollbackOnly();
                return result;
            });
        }
        return runExecution();
    }

    private Outcome runExecution() {
        ServiceException rejection = null;
        try { service.execute(101L); } catch (ServiceException failed) { rejection = failed; }
        try {
            WmProductionRecpt receipt = field("stored");
            WmMaterialStock stock = field("stock");
            List<WmMaterialStockTx> transactions = field("transactions");
            Outcome outcome = new Outcome(rejection, receipt.getStatus(), stock.getStockQuantity(), transactions.size());
            System.out.printf("读视图反例：旧合计=%s 当前报工=%s 当前累计=%s 真实本次=4 拒绝=%s 状态=%s 库存=%s 流水=%d SQL=%s%n",
                jdbc.snapshotApproved, jdbc.currentApproved, wo.getCompletedQty(), rejection != null,
                outcome.status(), outcome.stock(), outcome.transactions(), jdbc.statements);
            return outcome;
        } catch (Exception failed) { throw new AssertionError("无法读取离线落账结果", failed); }
    }

    private void assertRejectedWithoutEffects(Outcome outcome) throws Exception {
        assertNotNull(outcome.rejection(), "真实本次完成4，旧快照报工0不能授权草稿6并新造库存6");
        assertEquals("DRAFT", outcome.status(), "错误草稿必须保留未执行状态");
        assertEquals(0, outcome.stock().signum(), "错误草稿不能产生库存");
        assertEquals(0, outcome.transactions(), "错误草稿不能产生库存流水");
        verifyNoInteractions(this.<Object>field("stockOperation"), this.<Object>field("batches"));
        verify(dataSource, times(1)).getConnection();
    }

    @Test
    @DisplayName("手工执行：旧快照报工0与父锁累计6不能授权错误草稿6")
    void manualOldSnapshotCannotAuthorizeSix() throws Exception {
        prepareDraft("6");
        assertRejectedWithoutEffects(execute(false));
    }

    @Test
    @DisplayName("加入外层 REQUIRED：先前普通读取不应污染父锁后的已审核报工事实")
    void requiredOuterOldSnapshotCannotAuthorizeSix() throws Exception {
        prepareDraft("6");
        assertRejectedWithoutEffects(execute(true));
    }

    @Test
    @DisplayName("手工执行正控：已有旧快照仍可将真实本次4落账一次")
    void manualOldSnapshotCanPostRealFour() throws Exception {
        prepareDraft("4");
        Outcome result = execute(false);
        assertNull(result.rejection(), "当前锁读应减已审核2后放行真实本次4，而非全部拒绝");
        assertEquals("EXECUTED", result.status(), "真实本次4应完成原单");
        assertEquals(d("4"), result.stock(), "只能落账真实本次4");
        assertEquals(1, result.transactions(), "只能生成一条原始流水");
        verify(dataSource, times(1)).getConnection();
        assertTrue(jdbc.reportLocked, "真实 MyBatis 报工语句必须执行当前锁读");
    }

    @Test
    @DisplayName("外层 REQUIRED 正控：锁读真实4不新开连接或事务")
    void requiredOuterOldSnapshotCanPostRealFour() throws Exception {
        prepareDraft("4");
        int committedBefore = field("committed");
        Outcome result = execute(true);
        assertNull(result.rejection(), "加入旧快照外层事务后仍应识别真实本次4");
        assertEquals(d("4"), result.stock(), "加入外层事务不能按旧合计入库6");
        assertEquals(1, result.transactions(), "外层事务中也只能落账一次");
        assertEquals(committedBefore + 1, this.<Integer>field("committed"), "只由外层提交一次");
        verify(dataSource, times(1)).getConnection();
    }

    @Test
    @DisplayName("无旧视图正控：当前报工2本来就必须拒绝草稿6")
    void freshSnapshotRejectsSix() throws Exception {
        prepareDraft("6");
        jdbc.commitReportAndCompletion();
        assertRejectedWithoutEffects(execute(false));
    }

    @Test
    @DisplayName("逻辑删除报工仍已计入累计量，当前锁读不能漏减2")
    void logicallyDeletedApprovedReportStillCounts() throws Exception {
        prepareDraft("4");
        jdbc.reportDeleted = true;
        Outcome result = execute(false);
        assertNull(result.rejection(), "逻辑删除不能抹掉已累加工单的审核报工2");
        assertEquals(d("4"), result.stock(), "逻辑删除历史也必须先减2");
        String reportSql = jdbc.statements.stream().filter(sql -> sql.contains("PRD_WORK_REPORT")).findFirst().orElseThrow();
        assertFalse(reportSql.contains("DEL_FLAG"), "固定报工事实查询不得过滤逻辑删除行");
    }

    private record Outcome(ServiceException rejection, String status, BigDecimal stock, int transactions) { }
    private static BigDecimal d(String number) { return new BigDecimal(number); }

    /** 只解释本用例执行到的固定 SQL；不包含真实数据库、锁调度或 MySQL 方言实现。 */
    private final class ReadViewJdbc {
        private BigDecimal currentApproved = BigDecimal.ZERO;
        private BigDecimal snapshotApproved;
        private boolean reportDeleted;
        private boolean reportLocked;
        private boolean parentLocked;
        private final List<String> statements = new ArrayList<>();

        private void commitReportAndCompletion() {
            currentApproved = d("2");
            wo.setCompletedQty(d("6")); wo.setWoStatus("COMPLETED"); wo.setActualEndDate(new java.util.Date());
        }

        private PreparedStatement statement(String sql) throws SQLException {
            PreparedStatement statement = mock(PreparedStatement.class);
            Map<Integer, Object> parameters = new HashMap<>();
            doAnswer(call -> { parameters.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(statement).setString(anyInt(), anyString());
            doAnswer(call -> { parameters.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(statement).setLong(anyInt(), anyLong());
            ResultSet[] result = new ResultSet[1];
            when(statement.execute()).thenAnswer(call -> {
                result[0] = query(sql, parameters);
                return true;
            });
            when(statement.getResultSet()).thenAnswer(call -> result[0]);
            when(statement.getUpdateCount()).thenReturn(-1);
            return statement;
        }

        private ResultSet query(String sql, Map<Integer, Object> parameters) throws SQLException {
            String normalized = sql.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
            statements.add(normalized);
            assertEquals("000000", parameters.get(1), "实际固定 SQL 必须显式绑定可信租户");
            if (normalized.contains("FROM WM_PRODUCTION_RECPT")) {
                if (snapshotApproved == null) snapshotApproved = currentApproved;
                return resultSet(List.of("wo_id"), Collections.singletonList(new Object[]{201L}));
            }
            assertEquals(201L, parameters.get(2), "实际固定 SQL 必须绑定同一工单");
            if (normalized.contains("FROM PRD_WORK_ORDER")) {
                assertTrue(normalized.endsWith("FOR UPDATE"), "工单必须使用父行当前锁读");
                commitReportAndCompletion(); parentLocked = true;
                return resultSet(List.of("wo_id", "wo_no", "tenant_id", "del_flag", "material_id", "material_code", "unit_id",
                    "planned_qty", "completed_qty", "actual_end_date", "wo_status", "quality_status"),
                    Collections.singletonList(new Object[]{wo.getWoId(), wo.getWoNo(), wo.getTenantId(), wo.getDelFlag(),
                        wo.getMaterialId(), wo.getMaterialCode(), wo.getUnitId(), wo.getPlannedQty(), wo.getCompletedQty(),
                        new Timestamp(wo.getActualEndDate().getTime()), wo.getWoStatus(), wo.getQualityStatus()}));
            }
            assertTrue(normalized.contains("FROM PRD_WORK_REPORT"), "模型只允许本次固定报工读取");
            assertTrue(parentLocked, "报工读取必须在父工单锁之后");
            assertTrue(normalized.contains("REPORT_STATUS='APPROVED'"), "只扣减已审核事实");
            reportLocked = normalized.endsWith("FOR UPDATE");
            if (!reportLocked && snapshotApproved == null) snapshotApproved = currentApproved;
            BigDecimal approved = reportLocked ? currentApproved : snapshotApproved;
            if (normalized.contains("SUM(")) {
                return resultSet(List.of("approved_quantity"), Collections.singletonList(new Object[]{approved}));
            }
            assertTrue(normalized.contains("ORDER BY REPORT_ID"), "逐行当前锁读必须按报工编号固定锁序");
            boolean visible = !(reportDeleted && normalized.contains("DEL_FLAG"));
            return resultSet(List.of("reported_qty"), visible && approved.signum() > 0
                ? Collections.singletonList(new Object[]{approved}) : List.of());
        }

        private ResultSet resultSet(List<String> columns, List<Object[]> rows) throws SQLException {
            ResultSet result = mock(ResultSet.class);
            ResultSetMetaData metadata = mock(ResultSetMetaData.class);
            when(result.getMetaData()).thenReturn(metadata);
            when(metadata.getColumnCount()).thenReturn(columns.size());
            for (int i = 0; i < columns.size(); i++) {
                int index = i + 1; String name = columns.get(i);
                Object sample = rows.isEmpty() ? BigDecimal.ZERO : rows.get(0)[i];
                int type = sample instanceof BigDecimal ? Types.DECIMAL : sample instanceof Long ? Types.BIGINT
                    : sample instanceof Timestamp ? Types.TIMESTAMP : Types.VARCHAR;
                when(metadata.getColumnLabel(index)).thenReturn(name);
                when(metadata.getColumnName(index)).thenReturn(name);
                when(metadata.getColumnType(index)).thenReturn(type);
                when(metadata.getColumnClassName(index)).thenReturn(sample.getClass().getName());
            }
            AtomicInteger cursor = new AtomicInteger(-1);
            AtomicBoolean wasNull = new AtomicBoolean();
            when(result.next()).thenAnswer(call -> cursor.incrementAndGet() < rows.size());
            java.util.function.Function<Object, Object> value = key -> {
                int index = key instanceof Integer ? (Integer) key - 1 : columns.indexOf(key.toString().toLowerCase(Locale.ROOT));
                Object v = rows.get(cursor.get())[index]; wasNull.set(v == null); return v;
            };
            when(result.wasNull()).thenAnswer(call -> wasNull.get());
            when(result.getBigDecimal(anyInt())).thenAnswer(call -> value.apply(call.getArgument(0)));
            when(result.getBigDecimal(anyString())).thenAnswer(call -> value.apply(call.getArgument(0)));
            when(result.getString(anyString())).thenAnswer(call -> Objects.toString(value.apply(call.getArgument(0)), null));
            when(result.getLong(anyString())).thenAnswer(call -> { Object v = value.apply(call.getArgument(0)); return v == null ? 0L : ((Number) v).longValue(); });
            when(result.getTimestamp(anyString())).thenAnswer(call -> value.apply(call.getArgument(0)));
            return result;
        }
    }
}
