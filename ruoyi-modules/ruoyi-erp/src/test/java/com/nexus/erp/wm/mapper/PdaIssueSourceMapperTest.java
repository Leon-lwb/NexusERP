package com.nexus.erp.wm.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.mapping.BoundSql;
import java.util.Arrays;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 只解析真实映射器的动态 SQL，不创建连接，不访问任何数据库。 */
@Tag("dev")
@Tag("local")
class PdaIssueSourceMapperTest {
    private final Configuration configuration = configuration();

    @ParameterizedTest
    @ValueSource(strings = {"lockHead", "lockLines", "lockWarehouse", "lockMaterial", "lockLocations",
        "lockArea", "lockBatchById", "lockBatchesByCode", "lockStocks"})
    void allQueriesAreTenantScopedCurrentLocksWithoutArbitraryLimit(String method) {
        String sql = sql(method, parameters());
        assertAll("所有读取必须锁定真实当前记录",
            () -> assertTrue(sql.contains("tenant_id = ?"), "必须显式限制当前租户"),
            () -> assertTrue(sql.endsWith("for update"), "来源检查必须使用当前锁定读"),
            () -> assertFalse(sql.contains("limit"), "禁止任取一条隐藏重复来源"),
            () -> assertFalse(sql.contains("000007"), "租户和扫码值必须参数绑定，不拼接 SQL"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"lockHead", "lockLines", "lockWarehouse", "lockMaterial", "lockLocations",
        "lockArea", "lockBatchById", "lockBatchesByCode", "lockStocks"})
    void currentLockQueriesCannotReuseCachedRows(String method) {
        Options options = Arrays.stream(PdaIssueSourceMapper.class.getMethods())
            .filter(candidate -> candidate.getName().equals(method)).findFirst().orElseThrow().getAnnotation(Options.class);
        assertNotNull(options, "当前锁定读必须显式控制缓存，不能只在 SQL 中写锁语句");
        assertFalse(options.useCache(), "锁定查询不能读二级缓存");
        assertEquals(Options.FlushCachePolicy.TRUE, options.flushCache(), "查询前必须清理会话缓存以真正重读并加锁");
    }

    @Test void expiryDateIsReadFromDatabaseAndCannotBeReplacedBySystemClockOrCache() throws Exception {
        assertEquals("select curdate()", sql("currentDatabaseDate", parameters()), "效期边界必须读取同一 ERP 连接的数据库当日");
        Options options = PdaIssueSourceMapper.class.getMethod("currentDatabaseDate").getAnnotation(Options.class);
        assertFalse(options.useCache(), "数据库日期不得复用二级缓存");
        assertEquals(Options.FlushCachePolicy.TRUE, options.flushCache(), "数据库日期必须实时读取而非会话旧值");
    }
    @Test void parentAreaIsLockedByTenantAndIdentityWithoutHidingDisabledRecords() {
        String sql = sql("lockArea", parameters());
        assertTrue(sql.contains("area_id = ?"), "必须锁定库位关联的真实父库区标识");
        assertFalse(sql.contains("enable_flag ="), "停用父库区不能被过滤后另找替代来源");
        assertFalse(sql.contains("frozen_flag ="), "冻结父库区不能被过滤后另找替代来源");
        assertFalse(sql.contains("del_flag ="), "删除父库区也须由真实身份核验拒绝");
    }

    @Test void stockedBatchQueryMatchesActualExecutionDimensions() {
        String sql = sql("lockStocks", parameters());
        assertAll("有批次时执行范围完整一致",
            () -> assertTrue(sql.contains("material_id = ?"), "必须限定当前物料"),
            () -> assertTrue(sql.contains("warehouse_id = ?"), "必须限定有效头仓库"),
            () -> assertTrue(sql.contains("location_id = ?"), "必须限定解析出的真实库位"),
            () -> assertTrue(sql.contains("batch_id = ?"), "必须限定将实际扣减的批次标识"),
            () -> assertTrue(sql.contains("order by stock_id"), "库存锁按标识排序"),
            () -> assertFalse(sql.contains("frozen_flag ="), "不能过滤冻结来源后另选记录"),
            () -> assertFalse(sql.contains("del_flag ="), "不能隐藏既有引擎范围内的删除库存"));
    }
    @Test void nullBatchLocksCompleteExecutionRangeRatherThanOnlyNullBatches() {
        Map<String, Object> parameters = parameters(); parameters.put("batchId", null);
        String sql = sql("lockStocks", parameters);
        assertFalse(sql.contains("batch_id ="), "空标识时必须核验引擎会读取的全部批次，不缩小范围假装安全");
        assertFalse(sql.contains("batch_id is null"), "空批次不能只验证非批次子集再让引擎扣其他批次");
        assertTrue(sql.contains("location_id = ?"), "即使非批次也必须锁定真实库位");
    }
    @Test void zeroBatchRemainsAnExplicitExecutionDimension() {
        Map<String, Object> parameters = parameters(); parameters.put("batchId", 0L);
        assertTrue(sql("lockStocks", parameters).contains("batch_id = ?"), "真实零标识不能被转换成任意批次范围");
    }
    @Test void batchCodeLookupCannotHideWrongMaterialOrDeletedDuplicates() {
        String sql = sql("lockBatchesByCode", parameters());
        assertTrue(sql.contains("batch_code = ?"), "必须核验原始扫码编码");
        assertFalse(sql.contains("material_id ="), "错误物料或重复编码不能靠过滤变成唯一来源");
        assertFalse(sql.contains("del_flag ="), "删除的重复编码也必须被识别并拒绝");
        assertTrue(sql.contains("order by batch_id"), "批次锁必须按标识排序");
    }
    @Test void locationCodeLookupAndAllocatedIdLookupAreDistinct() {
        Map<String, Object> parameters = parameters();
        String scanned = sql("lockLocations", parameters);
        parameters.put("locationCode", null);
        String allocated = sql("lockLocations", parameters);
        assertTrue(scanned.contains("location_code = ?"), "扫码时按真实头仓库下的库位编码解析");
        assertFalse(scanned.contains("location_id = ?"), "不能用已分配标识过滤掉编码冲突或重复");
        assertTrue(allocated.contains("location_id = ?"), "无扫码时只允许核验已分配库位标识");
        assertTrue(allocated.contains("warehouse_id = ?"), "无扫码也不能跨仓库查库位");
    }
    @Test void lineQueryLocksAllLinesOfOnlyTheRequestedHead() {
        String sql = sql("lockLines", parameters());
        assertTrue(sql.contains("issue_id = ?"), "只能锁定并清零当前头的明细");
        assertTrue(sql.contains("order by line_id"), "所有行按固定顺序锁定");
        assertFalse(sql.contains("line_id = ?"), "不得只锁目标行却写未锁定的其他行");
        assertEquals("erp", PdaIssueSourceMapper.class.getAnnotation(DS.class).value(), "专属来源查询必须走 ERP 数据源");
    }

    private String sql(String method, Map<String, Object> parameters) {
        BoundSql bound = configuration.getMappedStatement(PdaIssueSourceMapper.class.getName() + "." + method).getBoundSql(parameters);
        return bound.getSql().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
    private static Map<String, Object> parameters() {
        Map<String, Object> result = new HashMap<>();
        result.put("tenantId", "000007"); result.put("issueId", 10L); result.put("materialId", 5L);
        result.put("areaId", 401L); result.put("warehouseId", 201L); result.put("locationId", 301L); result.put("locationCode", "库位一");
        result.put("batchId", 702L); result.put("batchCode", "批次B"); return result;
    }
    private static Configuration configuration() {
        Configuration result = new Configuration(); result.setMapUnderscoreToCamelCase(true);
        result.addMapper(PdaIssueSourceMapper.class); return result;
    }
}
