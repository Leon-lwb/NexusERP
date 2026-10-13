package com.nexus.common.report;
import org.junit.jupiter.api.Test;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class JdbcReportProviderTest {
    private JdbcTemplate database(){var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");var jdbc=new JdbcTemplate(ds);jdbc.execute("CREATE TABLE stock(id BIGINT PRIMARY KEY,tenant_id VARCHAR(20),create_dept BIGINT,create_by BIGINT,code VARCHAR(100),amount DECIMAL(20,6),del_flag CHAR DEFAULT '0')");jdbc.update("INSERT INTO stock VALUES (1,'A',10,7,'0001',0.1,'0'),(2,'A',20,8,'0002',0.2,'0'),(3,'B',10,7,'0003',0.3,'0')");return jdbc;}
    static class Provider extends JdbcDatasetProvider {
        Provider(JdbcTemplate jdbc){super(jdbc,new DatasetDescriptor("TEST","测试",1,"wm:stock:list",false,List.of(new DatasetField("id","ID",ValueType.ID),new DatasetField("code","编码",ValueType.STRING),new DatasetField("amount","成本",ValueType.DECIMAL,"wm:stock:query",List.of())),List.of(new DatasetParameter("code","编码",ValueType.STRING,false)),List.of("id","code")),"stock s","s","s.id",new LinkedHashMap<>(Map.of("id","s.id","code","s.code","amount","s.amount")));}
        protected List<Filter> filters(DatasetQuery query){return query.parameters().get("code")==null?List.of():List.of(new Filter("s.code = ?",List.of(query.parameters().get("code"))));}
    }
    @Test void inheritedProviderMethodsRemainProxyableForDatasourceRouting() {
        var calls=new AtomicInteger();var factory=new org.springframework.aop.framework.ProxyFactory();factory.setTarget(new Provider(database()));factory.setProxyTargetClass(true);
        factory.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation->{calls.incrementAndGet();return invocation.proceed();});
        var proxy=(ReportDatasetProvider)factory.getProxy();
        assertNotNull(proxy.descriptor());
        var actor=new ReportActor("A",7,10,true,List.of());
        proxy.query(new ReportExecutionContext(actor,List.of(ReportScope.all(7))),new DatasetQuery(Map.of(),Set.of("id"),1,50,false,null,false));
        assertTrue(calls.get()>=2,"descriptor and query must pass through Spring's datasource advisor");
    }
    @Test void jdbcResultsRemainTenantAndBusinessRoleScopedDespiteAnOverbroadCallerScope(){
        var actor=new ReportActor("A",7,10,false,List.of(new ReportRole(1,Set.of("report:definition:list"),ReportScope.all(7)),new ReportRole(2,Set.of("wm:stock:list"),new ReportScope(false,Set.of(10L),false,7))));
        var context=new ReportExecutionContext(actor,List.of(ReportScope.all(7)));var result=new Provider(database()).query(context,new DatasetQuery(Map.of(),Set.of("id","code"),1,50,false,null,false));
        assertEquals(1,result.total());assertEquals(1L,result.rows().get(0).get("id"));assertEquals("0001",result.rows().get(0).get("code"));
    }
    @Test void sensitiveFieldScopesIntersectAndValuesAreBoundAsData(){
        var actor=new ReportActor("A",7,10,false,List.of(new ReportRole(1,Set.of("wm:stock:list"),ReportScope.all(7)),new ReportRole(2,Set.of("wm:stock:query"),new ReportScope(false,Set.of(20L),false,7))));
        var provider=new Provider(database());var context=new ReportExecutionContext(actor,List.of(ReportScope.all(7)));
        var result=provider.query(context,new DatasetQuery(Map.of(),Set.of("id","amount"),1,50,false,null,false));assertEquals(1,result.total());assertEquals(2L,result.rows().get(0).get("id"));
        assertEquals(0,provider.query(context,new DatasetQuery(Map.of("code","x' OR '1'='1"),Set.of("id"),1,50,false,null,false)).total());
    }
    @Test void streamHasTheSamePolicyAndUnknownParametersOrSortsCannotReachJdbc(){
        var actor=new ReportActor("A",7,10,true,List.of());var context=new ReportExecutionContext(actor,List.of(ReportScope.all(7)));var provider=new Provider(database());var count=new AtomicInteger();
        provider.stream(context,new DatasetQuery(Map.of(),Set.of("id"),1,100001,true,null,false),row->count.incrementAndGet());assertEquals(2,count.get());
        assertThrows(IllegalArgumentException.class,()->provider.query(context,new DatasetQuery(Map.of("tenantId","B"),Set.of("id"),1,50,false,null,false)));
        assertThrows(IllegalArgumentException.class,()->provider.query(context,new DatasetQuery(Map.of(),Set.of("id"),1,50,false,"id;DROP",false)));
    }
}
