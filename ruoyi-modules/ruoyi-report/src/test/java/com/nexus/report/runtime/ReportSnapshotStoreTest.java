package com.nexus.report.runtime;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import com.nexus.report.engine.*;
import com.nexus.report.storage.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
class ReportSnapshotStoreTest {
    @TempDir Path directory;
    private static final class Provider implements ReportDatasetProvider {
        public DatasetDescriptor descriptor(){return new DatasetDescriptor("TEST","测试",1,"wm:stock:list",false,List.of(new DatasetField("id","ID",ValueType.ID),new DatasetField("label","编码",ValueType.STRING),new DatasetField("quantity","数值",ValueType.DECIMAL)),List.of(),List.of("id","label","quantity"));}
        public DatasetResult query(ReportExecutionContext context,DatasetQuery query){throw new UnsupportedOperationException("stream only");}
        public void stream(ReportExecutionContext context,DatasetQuery query,Consumer<Map<String,Object>> consume){for(int i=1;i<=10000;i++){context.cancellation().check();consume.accept(Map.of("id",9007199254740993L+i,"label","00"+i,"quantity",new BigDecimal("0.123400")));}}
    }
    @Test void largeSnapshotsUseStreamingAndKeepExactWireTypes(){
        var provider=new Provider();var actor=new ReportActor("A",1,10,true,List.of());
        var dsl=new ReportDsl(1,"TEST_REPORT","测试",ReportDsl.Kind.TABLE,"TEST","1",List.of(new ReportDsl.Column("id","ID",100,0,null,null),new ReportDsl.Column("label","编码",100,0,null,null),new ReportDsl.Column("quantity","数值",100,4,null,null)),List.of(),null,List.of(),List.of(),List.of(new ReportDsl.Summary(null,"COUNT_ROWS",List.of(),"条数",0)),true);
        var report=new ReportCompiler(new ObjectMapper()).compile(dsl,provider.descriptor(),actor);var store=new ReportSnapshotStore(new LocalReportArtifactStore(directory,20*1024*1024),new ObjectMapper());
        var snapshot=store.create(report,provider,actor,Map.of(),new ReportCancellation());assertEquals(10000,snapshot.rows());assertEquals("10000",snapshot.summaries().get(0).value().value());
        var page=store.page("A",snapshot.reference(),9900,5);assertEquals(5,page.size());assertEquals("9007199254750894",page.get(0).get("id").value());assertEquals("009901",page.get(0).get("label").value());assertEquals("0.123400",page.get(0).get("quantity").value());
    }
}
