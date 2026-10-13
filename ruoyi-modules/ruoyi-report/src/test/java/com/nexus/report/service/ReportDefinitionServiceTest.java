package com.nexus.report.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.config.*;
import com.nexus.report.domain.*;
import com.nexus.report.engine.*;
import com.nexus.report.mapper.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;
class ReportDefinitionServiceTest {
    private final ReportActor root=new ReportActor("A",1,10,true,List.of());
    private final ReportActor basic=new ReportActor("A",7,10,false,List.of(new ReportRole(2,Set.of("report:definition:add","report:definition:edit","report:definition:publish","report:definition:list","report:dataset:list","wm:stock:list"),ReportScope.all(7))));
    private static final class Provider implements ReportDatasetProvider {
        public DatasetDescriptor descriptor(){return new DatasetDescriptor("TEST","测试",1,"wm:stock:list",false,List.of(new DatasetField("code","编码",ValueType.STRING),new DatasetField("amount","金额",ValueType.DECIMAL,"wm:stock:query",List.of("currency")),new DatasetField("currency","币种",ValueType.STRING)),List.of(),List.of("code"));}
        public DatasetResult query(ReportExecutionContext context,DatasetQuery query){return new DatasetResult(List.of(Map.of("code","001")),1,java.time.Instant.now());}
    }
    private JdbcReportStore store(){var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");var jdbc=new JdbcTemplate(ds);try{var matcher=Pattern.compile("CREATE TABLE IF NOT EXISTS `rpt_[^`]+` \\([\\s\\S]*?\\) ENGINE[^;]*;").matcher(Files.readString(Path.of("../../script/sql/update/2026-10-14-report-p0.sql")));while(matcher.find())jdbc.execute(matcher.group().replaceAll("\\) ENGINE[^;]*;", ");").replaceAll("(?m)^  KEY `[^`]+` \\([^\\n]+\\),?\\n", "").replaceAll(",\\n\\)","\n)"));}catch(Exception e){throw new IllegalStateException(e);}return new JdbcReportStore(jdbc,new ObjectMapper());}
    private ReportDefinitionService service(JdbcReportStore store){return new ReportDefinitionService(store,new ReportProviderRegistry(List.of(new Provider())),new ReportAuthorizationPort(){public ReportActor resolve(String tenant,long user){return user==1?root:basic;}public boolean subjectExists(String tenant,String type,long id){return tenant.equals("A")&&id>0;}},new ReportCompiler(new ObjectMapper()));}
    private ReportDsl dsl(String version,String field){return new ReportDsl(1,"TEST_REPORT","测试",ReportDsl.Kind.TABLE,"TEST",version,List.of(new ReportDsl.Column(field,field,100,2,null,null)),List.of(),null,List.of(),List.of(),List.of(),true);}
    @Test void fieldPrivilegesAreCheckedBeforeCreatingOrPublishingADefinition(){
        var store=store();var service=service(store);var dataset=service.registerDataset(root,"TEST",1);
        store.replaceAcl(root,"DATASET",dataset.id(),List.of(new ReportRecords.Grant("USER","7","VIEW")));
        var fault=assertThrows(ReportFault.class,()->service.create(basic,dsl(dataset.publishedVersion(),"amount"),null));assertEquals("SCOPE_DENIED",fault.code());
        var definition=service.create(basic,dsl(dataset.publishedVersion(),"code"),null);assertEquals("DRAFT",definition.status());
        service.publish(basic,definition.id(),1);assertEquals("PUBLISHED",store.definition("A",definition.id()).orElseThrow().status());
    }
    @Test void datasetAclAndReportAclAreIndependentAndCrossTenantCannotUseAVersion(){
        var store=store();var service=service(store);var dataset=service.registerDataset(root,"TEST",1);
        assertThrows(ReportFault.class,()->service.create(basic,dsl(dataset.publishedVersion(),"code"),null));
        var other=new ReportActor("B",1,10,true,List.of());assertThrows(ReportFault.class,()->service.create(other,dsl(dataset.publishedVersion(),"code"),null));
    }
    @Test void descriptorCatalogRejectsDuplicateProviderVersions(){assertThrows(IllegalStateException.class,()->new ReportProviderRegistry(List.of(new Provider(),new Provider())));}
}
