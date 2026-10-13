package com.nexus.report.runtime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.config.*;
import com.nexus.report.domain.*;
import com.nexus.report.engine.*;
import com.nexus.report.mapper.*;
import com.nexus.report.service.*;
import com.nexus.report.storage.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
class ReportRuntimeServiceTest {
    @TempDir Path directory;
    private record Fixture(JdbcReportStore store,ReportDefinitionService definitions,ReportRuntimeService runtime,AtomicReference<ReportActor> current,ReportRecords.Definition definition) { }
    private Fixture fixture(){
        var json=new ObjectMapper();var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");var jdbc=new JdbcTemplate(ds);
        try{var matcher=Pattern.compile("CREATE TABLE IF NOT EXISTS `rpt_[^`]+` \\([\\s\\S]*?\\) ENGINE[^;]*;").matcher(Files.readString(Path.of("../../script/sql/update/2026-10-14-report-p0.sql")));while(matcher.find())jdbc.execute(matcher.group().replaceAll("\\) ENGINE[^;]*;", ");").replaceAll("(?m)^  KEY `[^`]+` \\([^\\n]+\\),?\\n", "").replaceAll(",\\n\\)","\n)"));}catch(Exception e){throw new IllegalStateException(e);}
        var actor=new ReportActor("A",7,10,false,List.of(new ReportRole(1,Set.of("*:*:*"),ReportScope.all(7))));var current=new AtomicReference<>(actor);
        var provider=new ReportDatasetProvider(){public DatasetDescriptor descriptor(){return new DatasetDescriptor("TEST","测试",1,"wm:stock:list",false,List.of(new DatasetField("code","编码",ValueType.STRING)),List.of(),List.of("code"));}public DatasetResult query(ReportExecutionContext context,DatasetQuery query){return new DatasetResult(List.of(Map.of("code","001")),1,java.time.Instant.now());}};
        var store=new JdbcReportStore(jdbc,json);var definitions=new ReportDefinitionService(store,new ReportProviderRegistry(List.of(provider)),new ReportAuthorizationPort(){public ReportActor resolve(String tenant,long id){return current.get();}public boolean subjectExists(String tenant,String type,long id){return true;}},new ReportCompiler(json));
        var dataset=definitions.registerDataset(actor,"TEST",1);var dsl=new ReportDsl(1,"TEST_REPORT","测试",ReportDsl.Kind.TABLE,"TEST",dataset.publishedVersion(),List.of(new ReportDsl.Column("code","编码",100,0,null,null)),List.of(),null,List.of(),List.of(),List.of(),true);var definition=definitions.create(actor,dsl,null);definitions.publish(actor,definition.id(),1);
        var artifacts=new LocalReportArtifactStore(directory,20*1024*1024);var runtime=new ReportRuntimeService(store,definitions,new ReportCompiler(json),new ReportSnapshotStore(artifacts,json),artifacts,new ReportProperties(),json);
        return new Fixture(store,definitions,runtime,current,definition);
    }
    @Test void permissionChangeMakesAnExistingSnapshotUndeliverable(){
        var f=fixture();var run=f.runtime.create(f.current.get(),f.definition.id(),"SNAPSHOT",Map.of());assertEquals("001",f.runtime.page(f.current.get(),run.id(),1,50,null,false).rows().get(0).get("code").value());
        f.current.set(new ReportActor("A",7,10,false,List.of(new ReportRole(1,Set.of("*:*:*"),new ReportScope(false,Set.of(20L),false,7)))));
        assertEquals("SCOPE_CHANGED",assertThrows(ReportFault.class,()->f.runtime.page(f.current.get(),run.id(),1,50,null,false)).code());
    }
    @Test void exportingALiveRunPinsItsOriginalVersionAndRequestKeyIsIdempotent(){
        var f=fixture();var actor=f.current.get();var live=f.runtime.create(actor,f.definition.id(),"LIVE",Map.of());var original=f.store.version("A",live.version()).orElseThrow();
        var changed=f.definitions.detail(actor,f.definition.id());f.store.saveDraft(actor,changed.id(),changed.revision(),"新版",changed.json());f.definitions.publish(actor,changed.id(),changed.revision()+1);
        var job=f.runtime.enqueue(actor,live.id(),"XLSX_DATA","request-1");assertEquals(original.id(),f.store.run("A",job.runId()).orElseThrow().version());
        assertEquals(job.id(),f.runtime.enqueue(actor,live.id(),"XLSX_DATA","request-1").id());
        assertEquals(409,assertThrows(ReportFault.class,()->f.runtime.enqueue(actor,live.id(),"PDF","request-1")).status());
    }
}
