package com.nexus.erp.pu;

import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper.RawRequirementDetail;
import com.nexus.erp.pu.mapper.ErpPurchaseCatalogSourceMapper.EpochMillisDateTypeHandler;
import org.apache.ibatis.executor.BaseExecutor;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.DefaultResultSetHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.sql.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实 MyBatis 映射和 SESSION 一级缓存回归，JDBC 为离线夹具，不是 MySQL 现场证明。 */
class PuRequirementCompetitionPoolTemporalContractTest {
    private static final String PREFIX = PuRequirementCompetitionPoolMapper.class.getName()+".";
    private static Configuration configuration() {
        Configuration c = new Configuration(); c.addMapper(PuRequirementCompetitionPoolMapper.class); return c;
    }
    @ParameterizedTest
    @CsvSource({"lockRequirementDetails,requiredDate,requiredDateEpochMillis", "lockOrderHeads,expectedArrivalDate,expectedArrivalDateEpochMillis",
        "lockRequisitionLines,requiredDate,requiredDateEpochMillis"})
    void businessDateRequiresServerEpochAndExplicitReadOnlyHandler(String method,String property,String column) {
        var ms=configuration().getMappedStatement(PREFIX+method);
        String sql=ms.getBoundSql(Map.of("tenantId","000001","materialId",1L,"windowEnd",LocalDate.of(2026,10,2),"headIds",List.of(1L))).getSql();
        assertTrue(sql.contains("UNIX_TIMESTAMP("),sql);
        var mapping=ms.getResultMaps().get(0).getResultMappings().stream().filter(m -> property.equals(m.getProperty())).findFirst();
        assertTrue(mapping.isPresent(),"不能将 JDBC 默认 DATETIME 解释提升为业务日");
        assertEquals(column,mapping.orElseThrow().getColumn());
        assertInstanceOf(EpochMillisDateTypeHandler.class,mapping.orElseThrow().getTypeHandler());
    }
    @Test void nearMidnightDateUsesNumericEpochNotDriverOrJvmTimezone() throws Exception {
        var c=configuration();var ms=c.getMappedStatement(PREFIX+"lockRequirementDetails");
        var rs=mock(ResultSet.class);var md=mock(ResultSetMetaData.class);var statement=mock(Statement.class);
        long epoch=Instant.parse("2026-10-02T15:30:00Z").toEpochMilli();
        when(rs.getMetaData()).thenReturn(md);when(md.getColumnCount()).thenReturn(1);
        when(md.getColumnLabel(1)).thenReturn("requiredDateEpochMillis");when(md.getColumnName(1)).thenReturn("requiredDateEpochMillis");
        when(md.getColumnType(1)).thenReturn(Types.DECIMAL);when(md.getColumnClassName(1)).thenReturn(BigDecimal.class.getName());
        when(rs.next()).thenReturn(true,false);when(rs.getBigDecimal("requiredDateEpochMillis")).thenReturn(BigDecimal.valueOf(epoch));
        when(statement.getResultSet()).thenReturn(rs);when(statement.getMoreResults()).thenReturn(false);when(statement.getUpdateCount()).thenReturn(-1);
        var handler=new DefaultResultSetHandler(mock(Executor.class),ms,mock(ParameterHandler.class),null,ms.getBoundSql(Map.of()),RowBounds.DEFAULT);
        RawRequirementDetail row=(RawRequirementDetail)handler.handleResultSets(statement).get(0);
        assertNotNull(row,"未知日期列不能被映射为可信业务行");assertNotNull(row.getRequiredDate());assertEquals(epoch,row.getRequiredDate().getTime());
        assertEquals(LocalDate.of(2026,10,2),row.getRequiredDate().toInstant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate());
        verify(rs,never()).getTimestamp(anyString());
    }
    @Test void allCurrentReadsActuallyBypassSessionCacheOnRepeatedInvocation() throws Exception {
        Configuration c=configuration();AtomicInteger reads=new AtomicInteger();
        // 使用产品 MappedStatement 和真实 BaseExecutor 一级缓存逻辑；仅 doQuery 的 JDBC 结果替换为离线序号。
        BaseExecutor executor=new BaseExecutor(c,mock(Transaction.class)) {
            @Override protected int doUpdate(MappedStatement ms,Object parameter) {throw new AssertionError("unexpected write");}
            @Override protected List<BatchResult> doFlushStatements(boolean rollback) {return List.of();}
            @Override protected <E> List<E> doQuery(MappedStatement ms,Object parameter,RowBounds bounds,ResultHandler handler,BoundSql sql) {
                return (List<E>)List.of(reads.incrementAndGet());
            }
            @Override protected <E> Cursor<E> doQueryCursor(MappedStatement ms,Object parameter,RowBounds bounds,BoundSql sql) {throw new AssertionError("unexpected cursor");}
        };
        Map<String,Object> params=Map.of("tenantId","000001","materialId",1L,"windowEnd",LocalDate.of(2026,10,2),"headIds",List.of(1L));
        for(var method:PuRequirementCompetitionPoolMapper.class.getDeclaredMethods()) {
            var ms=c.getMappedStatement(PREFIX+method.getName());int before=reads.get();
            executor.query(ms,params,RowBounds.DEFAULT,null);executor.query(ms,params,RowBounds.DEFAULT,null);
            assertEquals(before+2,reads.get(),method.getName()+" must re-read, not replay first current-lock result");
        }
    }
}
