package com.nexus.report.export;

import com.nexus.common.report.*;
import com.nexus.report.config.ReportProperties;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.domain.ReportRecords.*;
import com.nexus.report.mapper.JdbcReportStore;
import com.nexus.report.runtime.ReportRuntimeService;
import com.nexus.report.storage.ReportArtifactStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Each execution owns a persisted attempt fence; local threads hold no login token. */
public class ReportExportWorker implements AutoCloseable {
    @FunctionalInterface public interface Renderer { void write(ExportJob job,ReportRuntimeService.Access access,OutputStream output,ReportCancellation cancellation) throws IOException; }
    private static final Logger LOG=LoggerFactory.getLogger(ReportExportWorker.class);
    private static final Set<String> RETRYABLE=Set.of("ARTIFACT_STORE_UNAVAILABLE","STORAGE_UNAVAILABLE","STORAGE_FAILED","PDF_EXPORT_FAILED","XLSX_EXPORT_FAILED","REPORT_EXECUTION_FAILED");
    private final JdbcReportStore store;private final ReportRuntimeService runtime;private final ReportArtifactStore artifacts;private final ReportProperties properties;private final Renderer renderer;
    private final ExecutorService executor;private final ScheduledExecutorService heartbeat;
    private final Map<String,ReportCancellation> active=new ConcurrentHashMap<>();private final String owner=UUID.randomUUID().toString();private volatile boolean closed;

    public ReportExportWorker(JdbcReportStore store,ReportRuntimeService runtime,ReportArtifactStore artifacts,ReportProperties properties,Renderer renderer){
        this.store=store;this.runtime=runtime;this.artifacts=artifacts;this.properties=properties;this.renderer=renderer;
        executor=Executors.newFixedThreadPool(properties.getWorkers(),r->{var thread=new Thread(r,"report-export");thread.setDaemon(true);return thread;});
        heartbeat=Executors.newSingleThreadScheduledExecutor(r->{var thread=new Thread(r,"report-export-lease");thread.setDaemon(true);return thread;});
    }

    @Scheduled(fixedDelay=3000,initialDelay=5000)
    public synchronized void scan(){
        if(closed)return;
        try{
            for(var candidate:store.claimableJobs()){
                if(active.size()>=properties.getWorkers())break;
                if(active.containsKey(candidate.id()))continue;
                var acquired=store.claimJob(candidate,owner,Instant.now().plusSeconds(30));if(acquired.isEmpty())continue;
                var job=acquired.get();active.put(job.id(),new ReportCancellation());
                try{executor.submit(()->execute(job));}catch(RejectedExecutionException e){active.remove(job.id());store.retryJob(job,owner,"REPORT_EXECUTION_FAILED");}
            }
        }catch(RuntimeException e){LOG.warn("Report queue scan failed ({})",ReportRuntimeService.code(e));}
    }

    private static String kind(ExportJob job){
        return switch(job.format()){
            case "PDF" -> "pdf";
            case "XLSX_DATA","XLSX_LAYOUT" -> "xlsx";
            default -> throw ReportFault.invalid("导出格式不支持");
        };
    }

    private void checkLease(ExportJob job,ReportCancellation cancellation,Instant deadline){
        if(cancellation.cancelled()){cancellation.check();return;}
        var now=Instant.now();
        if(!now.isBefore(deadline))cancellation.cancel("TIMEOUT");
        else if(!job.expires().isAfter(now))cancellation.cancel("SNAPSHOT_EXPIRED");
        else{
            var current=store.job(job.tenant(),job.id()).orElse(null);
            if(current==null||current.attempts()!=job.attempts()||!Objects.equals(current.leaseOwner(),job.leaseOwner()))cancellation.cancel("LEASE_LOST");
            else if(current.cancelled()||current.state().equals("CANCEL_REQUESTED"))cancellation.cancel("CANCELLED");
            else if(!current.expires().isAfter(now))cancellation.cancel("SNAPSHOT_EXPIRED");
            else if(!current.state().equals("RUNNING")||current.leaseUntil()==null||!current.leaseUntil().isAfter(now))cancellation.cancel("LEASE_LOST");
        }
        cancellation.check();
    }

    void renewLease(ExportJob job,ReportCancellation cancellation,Instant deadline){
        try{
            checkLease(job,cancellation,deadline);runtime.execution(job);cancellation.check();
            if(!store.heartbeat(job,job.leaseOwner(),Instant.now().plusSeconds(30)))cancellation.cancel("LEASE_LOST");
        }catch(RuntimeException e){if(!cancellation.cancelled())cancellation.cancel(ReportRuntimeService.code(e));}
    }

    void execute(ExportJob job){
        String leaseOwner=job.leaseOwner();var cancellation=active.computeIfAbsent(job.id(),key->new ReportCancellation());
        Instant deadline=Instant.now().plusSeconds(properties.getTaskSeconds());
        ScheduledFuture<?> monitor=null;Path temporary=null;String file=null;
        try{
            if(closed)cancellation.cancel("SHUTDOWN");
            else if(job.cancelled()||job.state().equals("CANCEL_REQUESTED"))cancellation.cancel("CANCELLED");
            checkLease(job,cancellation,deadline);String kind=kind(job);
            monitor=heartbeat.scheduleAtFixedRate(()->renewLease(job,cancellation,deadline),5,5,TimeUnit.SECONDS);
            var access=runtime.execution(job);cancellation.check();runtime.progress(access,"SNAPSHOT",0,null,cancellation);access=runtime.buildSnapshot(access,cancellation,100000);
            runtime.progress(access,job.format().equals("XLSX_DATA")?"WRITING":"LAYOUT",0,access.run()==null?null:access.run().rows(),cancellation);
            checkLease(job,cancellation,deadline);
            temporary=artifacts.temporary(job.tenant(),kind);final long[] bytes={0};
            try(var output=new FilterOutputStream(artifacts.output(job.tenant(),temporary)){
                @Override public void write(int value)throws IOException{out.write(value);bytes[0]++;}
                @Override public void write(byte[] data,int offset,int length)throws IOException{out.write(data,offset,length);bytes[0]+=length;}
            }){renderer.write(job,access,output,cancellation);}
            if(access.run()!=null)runtime.progress(access,"WRITING",access.run().rows(),access.run().rows(),cancellation);
            checkLease(job,cancellation,deadline);runtime.execution(job);checkLease(job,cancellation,deadline);
            file=artifacts.commit(job.tenant(),temporary,kind);temporary=null;
            checkLease(job,cancellation,deadline);
            if(!store.finishJob(job,leaseOwner,"SUCCEEDED",file,bytes[0],null)){delete(job.tenant(),file);file=null;}
            else{
                try{store.audit(access.actor(),access.run()==null?null:access.run().reportId(),"EXPORT_FINISH","SUCCEEDED",null);}
                catch(RuntimeException e){LOG.warn("Report completion audit failed ({})",ReportRuntimeService.code(e));}
            }
        }catch(Exception e){
            discard(job.tenant(),temporary);delete(job.tenant(),file);
            String code=cancellation.cancelled()?cancellation.reason():ReportRuntimeService.code(e);
            try{
                if(code.equals("CANCELLED")||code.equals("SHUTDOWN"))store.finishJob(job,leaseOwner,"CANCELLED",null,null,code);
                else if(RETRYABLE.contains(code))store.retryJob(job,leaseOwner,code);
                else if(!code.equals("LEASE_LOST"))store.finishJob(job,leaseOwner,"FAILED",null,null,code);
            }catch(RuntimeException transitionFailure){LOG.warn("Report attempt completion failed ({})",ReportRuntimeService.code(transitionFailure));}
        }finally{
            if(monitor!=null)monitor.cancel(false);cancellation.detach();active.remove(job.id(),cancellation);
        }
    }

    private void discard(String tenant,Path temporary){
        if(temporary==null)return;
        try{artifacts.discard(tenant,temporary);}catch(RuntimeException e){LOG.warn("Report temporary cleanup failed ({})",ReportRuntimeService.code(e));}
    }
    private void delete(String tenant,String file){
        if(file==null)return;
        try{artifacts.delete(tenant,file);}catch(RuntimeException e){LOG.warn("Report artifact cleanup failed ({})",ReportRuntimeService.code(e));}
    }

    private boolean cleanup(String tenant,String reference){
        if(reference==null||reference.startsWith("build/"))return true;
        try{artifacts.delete(tenant,reference);return true;}
        catch(RuntimeException e){LOG.warn("Report artifact cleanup failed ({})",ReportRuntimeService.code(e));return false;}
    }

    @Scheduled(fixedDelay=60000,initialDelay=15000)
    public void expire(){
        if(closed)return;
        try{
            for(var job:store.expiredJobs()){
                try{if((job.state().equals("EXPIRED")||store.tryExpireJob(job))&&cleanup(job.tenant(),job.file()))store.ackJobCleanup(job);}
                catch(RuntimeException e){LOG.warn("Report job expiry failed ({})",ReportRuntimeService.code(e));}
            }
            for(var run:store.expiredRuns()){
                try{if((run.state().equals("EXPIRED")||store.expireRun(run))&&cleanup(run.tenant(),run.snapshot())&&cleanup(run.tenant(),run.plan()))store.ackRunCleanup(run);}
                catch(RuntimeException e){LOG.warn("Report run expiry failed ({})",ReportRuntimeService.code(e));}
            }
            artifacts.reap(Instant.now().minusSeconds(Math.max(properties.getRetentionHours()*3600L, properties.getTaskSeconds()*2L)),store::artifactReferenced);
        }catch(RuntimeException e){LOG.warn("Report artifact expiry failed ({})",ReportRuntimeService.code(e));}
    }

    @Override public synchronized void close(){closed=true;active.values().forEach(c->c.cancel("SHUTDOWN"));executor.shutdownNow();heartbeat.shutdownNow();}
}
