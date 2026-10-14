package com.nexus.report.runtime;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.nexus.common.report.*;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.engine.*;
import com.nexus.report.storage.ReportArtifactStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;
/** JSON-lines snapshots keep raw ids/decimals exact and allow bounded page/export readers. */
public final class ReportSnapshotStore {
    private static final TypeReference<Map<String,TypedValue>> ROW=new TypeReference<>(){};
    private final ReportArtifactStore artifacts;private final ObjectMapper json;
    public ReportSnapshotStore(ReportArtifactStore artifacts,ObjectMapper json){this.artifacts=artifacts;this.json=json.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);}
    public record Snapshot(String reference,String checksum,long rows,List<ReportCalculator.SummaryValue> summaries) { }
    public Snapshot create(CompiledReport compiled,ReportDatasetProvider provider,ReportActor actor,Map<String,Object> parameters,ReportCancellation cancellation){
        return create(compiled,provider,actor,parameters,cancellation,100000);
    }
    public Snapshot create(CompiledReport compiled,ReportDatasetProvider provider,ReportActor actor,Map<String,Object> parameters,ReportCancellation cancellation,int maximum){
        return create(compiled,provider,actor,parameters,cancellation,maximum,new com.nexus.report.version.V1ReportEngineExecutor(new ReportCompiler(json)));
    }
    public Snapshot create(CompiledReport compiled,ReportDatasetProvider provider,ReportActor actor,Map<String,Object> parameters,ReportCancellation cancellation,int maximum,com.nexus.report.version.ReportEngineExecutor executor){
        return create(compiled,provider,actor,parameters,cancellation,maximum,executor,rows->{});
    }
    public Snapshot create(CompiledReport compiled,ReportDatasetProvider provider,ReportActor actor,Map<String,Object> parameters,ReportCancellation cancellation,int maximum,com.nexus.report.version.ReportEngineExecutor executor,java.util.function.LongConsumer progress){
        if(maximum<1||maximum>100000)throw ReportFault.capacity();
        cancellation.check();
        int limit=compiled.dsl().kind()==com.nexus.report.domain.ReportDsl.Kind.DOCUMENT?Math.min(maximum,5000):maximum;
        Path temporary=artifacts.temporary(actor.tenantId(),"rows");String committed=null;long[] count={0};var calculator=executor;var accumulator=executor.summaries(compiled);java.util.concurrent.atomic.AtomicReference<List<ReportCalculator.SummaryValue>> calculatedSummaries=new java.util.concurrent.atomic.AtomicReference<>();
        try(var writer=new BufferedWriter(new OutputStreamWriter(artifacts.output(actor.tenantId(),temporary),StandardCharsets.UTF_8))){
            var context=new ReportExecutionContext(actor,compiled.context(actor).scopes(),cancellation);var query=new DatasetQuery(parameters,compiled.requiredFields(),1,100001,true,null,false);
            if(compiled.aggregateExpressions()){
                List<Map<String,Object>> source=new ArrayList<>();provider.stream(context,query,row->{cancellation.check();if(source.size()>=Math.min(5000,limit)){cancellation.cancel("CAPACITY_EXCEEDED");throw ReportFault.capacity();}source.add(new LinkedHashMap<>(row));});
                var data=calculator.calculate(compiled,source,parameters);calculatedSummaries.set(data.summaries());for(var row:data.rows()){cancellation.check();write(writer,row);accumulator.accept(row);count[0]++;if(count[0]%512==0)progress.accept(count[0]);}
            } else {
                int cost=compiled.expressions().values().stream().mapToInt(SafeExpression::cost).sum();
                provider.stream(context,query,row->{cancellation.check();if(++count[0]>limit||count[0]*cost>2_000_000){cancellation.cancel("CAPACITY_EXCEEDED");throw ReportFault.capacity();}var typed=calculator.calculateRow(compiled,row,parameters);write(writer,typed);accumulator.accept(typed);if(count[0]%512==0)progress.accept(count[0]);});
            }
            progress.accept(count[0]);writer.flush();cancellation.check();
        }catch(RuntimeException|IOException e){artifacts.discard(actor.tenantId(),temporary);if(e instanceof RuntimeException runtime)throw runtime;throw storageError();}
        try{cancellation.check();committed=artifacts.commit(actor.tenantId(),temporary,"rows");cancellation.check();String checksum=artifacts.checksum(actor.tenantId(),committed);cancellation.check();return new Snapshot(committed,checksum,count[0],calculatedSummaries.get()!=null?calculatedSummaries.get():accumulator.finish());}
        catch(RuntimeException e){artifacts.discard(actor.tenantId(),temporary);if(committed!=null)artifacts.delete(actor.tenantId(),committed);throw e;}
    }
    private void write(BufferedWriter writer,Map<String,TypedValue> row){try{String line=json.writeValueAsString(row);if(line.length()>1_000_000)throw ReportFault.capacity();writer.write(line);writer.newLine();}catch(IOException e){throw storageError();}}
    public List<Map<String,TypedValue>> page(String tenant,String reference,long offset,int size){
        if(offset<0||size<1||size>5000)throw new ReportFault("INVALID_PARAMETER","快照分页范围无效");List<Map<String,TypedValue>> rows=new ArrayList<>();long[] count={0};
        try(var reader=Files.newBufferedReader(artifacts.read(tenant,reference),StandardCharsets.UTF_8)){
            String line;while((line=reader.readLine())!=null){if(count[0]++<offset)continue;if(rows.size()>=size)break;rows.add(parse(line));}return rows;
        }catch(IOException e){throw storageError();}
    }
    public void each(String tenant,String reference,Consumer<Map<String,TypedValue>> consume,ReportCancellation cancellation){
        try(var reader=Files.newBufferedReader(artifacts.read(tenant,reference),StandardCharsets.UTF_8)){String line;long count=0;while((line=reader.readLine())!=null){cancellation.check();if(++count>100000)throw ReportFault.capacity();consume.accept(parse(line));}}
        catch(IOException e){throw storageError();}
    }
    private Map<String,TypedValue> parse(String line){if(line.length()>1_000_000)throw ReportFault.capacity();try{return json.readValue(line,ROW);}catch(IOException e){throw new ReportFault("SNAPSHOT_INVALID","快照内容无法解析",500);}}
    public void verify(String tenant,String reference,String expected){if(expected==null||!expected.equals(artifacts.checksum(tenant,reference)))throw new ReportFault("SNAPSHOT_INVALID","快照完整性校验失败",500);}
    private static ReportFault storageError(){return new ReportFault("ARTIFACT_STORE_UNAVAILABLE","报表快照读写失败",500);}
}
