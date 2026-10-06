package com.nexus.erp.ai.adapter;

import org.junit.jupiter.api.Test;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Options;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

/** 固定实际来源查询与同物理事务边界，不能以快照推断代替完整计划归属。 */
class ErpDecisionPurchaseScopeSqlContractTest {
    @Test void scopeGuardRequiresOwnedMandatoryErpBoundary() throws Exception {
        Class<?> guard;
        try { guard=Class.forName("com.nexus.erp.ai.adapter.ErpDecisionPurchaseScopeGuard"); }
        catch(ClassNotFoundException missing) { throw new AssertionError("缺少工单根来源守卫，最终采购尚未独立核归属",missing); }
        int methods=0;
        for(var method:guard.getDeclaredMethods()) {
            if(!method.getName().endsWith("Locked"))continue;
            var tx=method.getAnnotation(Transactional.class);
            assertNotNull(tx,"锁定守卫必须加入已拥有的ERP事务");
            assertEquals(Propagation.MANDATORY,tx.propagation(),"不得在来源守卫开启普通事务"); methods++;
        }
        assertTrue(methods>=3,"必须提供供应商选择、根来源和writer专用归属检查");
    }
    @Test void fixedStatementsBindActualColumnsAndNumericIdsWithoutAnyDatabase() {
        var configuration=new org.apache.ibatis.session.Configuration();
        configuration.addMapper(com.nexus.erp.ai.adapter.mapper.ErpDecisionPurchaseScopeMapper.class);
        String name="com.nexus.erp.ai.adapter.mapper.ErpDecisionPurchaseScopeMapper.";
        var inputs=java.util.Map.<String,Object>of("tenantId","000000","materialId",139L,"planId",31L,
            "detailIds",java.util.List.of(21L,22L),"requirementIds",java.util.List.of(51L),"windowEnd",java.time.LocalDate.of(2026,10,6));
        var requirements=configuration.getMappedStatement(name+"lockRequirementSources").getBoundSql(inputs);
        String sql=requirements.getSql().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
        assertTrue(sql.contains("h.plan_id"),"MRP归属plan_id只能从真实头读取");
        assertFalse(sql.contains("d.wo_id"),"需求明细不存在工单列");
        assertFalse(sql.contains("d.plan_id"),"需求明细不存在计划列");
        assertFalse(sql.contains("d.source_id"),"需求明细不能臆造来源ID列");
        assertTrue(sql.contains("d.unit"),"须复读原始单位字符串而非单位名称兜底");
        var direct=configuration.getMappedStatement(name+"lockDirectWorkOrders").getBoundSql(inputs).getSql().toLowerCase(Locale.ROOT);
        assertTrue(direct.contains("source_type='material_requirement'")&&direct.contains("source_id in"),"直接MRP必须使用工单的真实source_type/source_id");
        assertFalse(direct.contains("plan_id="),"直接MRP唯一性不得仅限当前计划");
        var vendor=configuration.getMappedStatement(name+"lockVendorChoices").getBoundSql(inputs).getSql().toLowerCase(Locale.ROOT);
        assertTrue(vendor.contains("is_default"),"必须复读真实默认供应商列");
        assertFalse(vendor.contains("min_order_qty"),"不能将历史MOQ当作已核条款");
    }
    @Test void scopeDefinitionPinCannotUseMysqlQueryCache() throws Exception {
        var method=com.nexus.erp.ai.adapter.mapper.ErpDecisionPurchaseScopeMapper.class.getMethod("pinScopeDefinition");
        String sql=String.join(" ",method.getAnnotation(Select.class).value()).toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("sql_no_cache"),"新增工单表定义pin必须避开MySQL5.7查询缓存");
        assertTrue(sql.contains("where 1=0"),"定义pin只能是固定空查询而非锁真实业务数据");
    }
    /** 真实MyBatis-Plus租户插件改写真实MappedStatement，仅最终Executor隔离为内存端。 */
    private String throughTenantPlugin(String method,java.util.Map<String,Object> parameters) throws Exception {
        var configuration=new com.baomidou.mybatisplus.core.MybatisConfiguration();
        configuration.addMapper(com.nexus.erp.ai.adapter.mapper.ErpDecisionPurchaseScopeMapper.class);
        var statement=configuration.getMappedStatement("com.nexus.erp.ai.adapter.mapper.ErpDecisionPurchaseScopeMapper."+method);
        var bound=statement.getBoundSql(parameters);
        var properties=new com.nexus.common.tenant.properties.TenantProperties();properties.setExcludes(java.util.List.of());
        var plugins=new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();
        plugins.addInnerInterceptor(new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(
            new com.nexus.common.tenant.handle.PlusTenantLineHandler(properties)));
        var sink=org.mockito.Mockito.mock(org.apache.ibatis.executor.Executor.class);
        org.mockito.Mockito.when(sink.createCacheKey(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any()))
            .thenReturn(new org.apache.ibatis.cache.CacheKey());
        org.mockito.Mockito.doAnswer(call->java.util.List.of(((org.apache.ibatis.mapping.BoundSql)call.getArgument(5)).getSql())).when(sink)
            .query(org.mockito.ArgumentMatchers.any(org.apache.ibatis.mapping.MappedStatement.class),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(org.apache.ibatis.session.RowBounds.class),org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(org.apache.ibatis.cache.CacheKey.class),org.mockito.ArgumentMatchers.any(org.apache.ibatis.mapping.BoundSql.class));
        var actual=(org.apache.ibatis.executor.Executor)plugins.plugin(sink);
        java.util.List<String> rows=actual.query(statement,parameters,org.apache.ibatis.session.RowBounds.DEFAULT,null,new org.apache.ibatis.cache.CacheKey(),bound);
        assertEquals(1,rows.size(),"真实插件必须处理被测MappedStatement后才交给最终端");return rows.get(0);
    }
    @Test void actualTenantPluginDoesNotAppendTenantColumnToFixedInformationSchemaQuery() throws Exception {
        try(var tenant=org.mockito.Mockito.mockStatic(com.nexus.common.tenant.helper.TenantHelper.class)) {
            tenant.when(com.nexus.common.tenant.helper.TenantHelper::getTenantId).thenReturn("000000");
            var rewritten=throughTenantPlugin("scopeTableEngine",java.util.Map.of());
            var parsed=assertInstanceOf(net.sf.jsqlparser.statement.select.PlainSelect.class,
                net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(rewritten),"必须检查经过实际租户插件的SQL语法树");
            assertFalse(parsed.getWhere().toString().toLowerCase(Locale.ROOT).contains("tenant_id"),
                "information_schema.TABLES不存在tenant_id，元数据方法须有界排除租户插件");
        }
    }
    @Test void businessQueriesKeepExplicitTenantAndActualPluginPredicate() throws Exception {
        try(var tenant=org.mockito.Mockito.mockStatic(com.nexus.common.tenant.helper.TenantHelper.class)) {
            tenant.when(com.nexus.common.tenant.helper.TenantHelper::getTenantId).thenReturn("000000");
            var rewritten=throughTenantPlugin("findRootWorkOrder",java.util.Map.of("tenantId","000000","woId",11L));
            var parsed=assertInstanceOf(net.sf.jsqlparser.statement.select.PlainSelect.class,
                net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(rewritten),"业务查询也必须经过真实插件解析");
            String where=parsed.getWhere().toString();
            assertTrue(where.contains("tenant_id = ?"),"业务SQL必须保留调用参数的显式租户");
            assertTrue(where.contains("tenant_id = '000000'"),"业务SQL不能使用元数据排除绕开实际租户插件");
        }
    }
    @Test void everyBusinessCurrentReadStillPassesTenantPluginWithNoGlobalExclusion() throws Exception {
        try(var tenant=org.mockito.Mockito.mockStatic(com.nexus.common.tenant.helper.TenantHelper.class)) {
            tenant.when(com.nexus.common.tenant.helper.TenantHelper::getTenantId).thenReturn("000000");
            var input=java.util.Map.<String,Object>of("tenantId","000000","woId",11L,"planId",31L,"materialId",139L,
                "detailIds",java.util.List.of(21L),"requirementIds",java.util.List.of(51L),"windowEnd",java.time.LocalDate.of(2026,10,6));
            for(String method:java.util.List.of("findRootWorkOrder","lockPlanWorkOrders","lockDirectWorkOrders","lockRequirementSources","lockVendorChoices")) {
                var rewritten=throughTenantPlugin(method,input);
                var parsed=assertInstanceOf(net.sf.jsqlparser.statement.select.PlainSelect.class,
                    net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(rewritten),"每个业务查询都必须经过实际插件语法解析");
                assertTrue(parsed.getWhere().toString().contains("'000000'"),"方法级元数据排除不得污染业务租户插件");
                assertFalse(com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper.willIgnoreTenantLine(
                    "com.nexus.erp.ai.adapter.mapper.ErpDecisionPurchaseScopeMapper."+method),"业务方法不能继承元数据忽略配置");
            }
        }
    }
    @Test void scopeMapperUsesActualSourceColumnsAndUncachedOrderedCurrentReads() throws Exception {
        Class<?> mapper;
        try { mapper=Class.forName("com.nexus.erp.ai.adapter.mapper.ErpDecisionPurchaseScopeMapper"); }
        catch(ClassNotFoundException missing) { throw new AssertionError("缺少完整同计划与MRP实际来源查询",missing); }
        int sources=0;
        for(var method:mapper.getDeclaredMethods()) {
            var select=method.getAnnotation(Select.class);if(select==null)continue;
            String sql=String.join(" ",select.value()).toLowerCase(Locale.ROOT);
            var options=method.getAnnotation(Options.class);
            assertNotNull(options,"来源查询必须显式禁止缓存");
            assertFalse(options.useCache(),"来源不能来自二级缓存");
            assertEquals(Options.FlushCachePolicy.TRUE,options.flushCache(),"当前读须清除会话缓存");
            assertFalse(sql.contains("master"),"ERP物理事务不能读取master运行表");
            assertFalse(sql.contains("ai_decision"),"不得将决策运行表纳入ERP锁定事务");
            if(method.getName().startsWith("lock")) {
                assertTrue(sql.contains("tenant_id=#{tenantid}"),"每条来源查询须显式限定租户");
                assertTrue(sql.contains("order by"),"当前读须按真实主键稳定排序");
                assertTrue(sql.contains("for update"),"来源归属必须以当前行锁重读"); sources++;
            }
        }
        assertTrue(sources>=3,"须锁定完整计划、需求来源和供应关系");
    }
}
