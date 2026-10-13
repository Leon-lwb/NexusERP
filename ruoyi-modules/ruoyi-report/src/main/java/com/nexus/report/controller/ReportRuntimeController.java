package com.nexus.report.controller;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.domain.ReportRecords.*;
import com.nexus.report.engine.RenderPlan;
import com.nexus.report.runtime.ReportRuntimeService;
import com.nexus.report.storage.ReportArtifactStore;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ContentDisposition;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
@RestController
@RequestMapping("/report/runtime")
public class ReportRuntimeController {
    private final ReportRequestActor actors;private final ReportRuntimeService runtime;private final ReportArtifactStore artifacts;
    public ReportRuntimeController(ReportRequestActor actors,ReportRuntimeService runtime,ReportArtifactStore artifacts){this.actors=actors;this.runtime=runtime;this.artifacts=artifacts;}
    public record RunRequest(String reportId,String mode,Map<String,Object> parameters) { }
    public record ExportRequest(String format,String requestKey) { }
    public record RunView(String id,String reportId,String version,String mode,String state,long rows,Instant dataTime,Instant expires,String error) { }
    public record JobView(String id,String runId,String format,String state,int attempts,Long bytes,Instant expires,String error) { }
    static RunView view(Run run){return new RunView(run.id(),run.reportId(),run.version(),run.mode(),run.state(),run.rows(),run.dataTime(),run.expires(),run.error());}
    static JobView view(ExportJob job){return new JobView(job.id(),job.runId(),job.format(),job.state(),job.attempts(),job.bytes(),job.expires(),job.error());}
    @SaCheckPermission("report:runtime:preview") @PostMapping("/runs")
    public R<RunView> create(@RequestBody RunRequest body){return R.ok(view(runtime.create(actors.current(),body.reportId(),body.mode(),body.parameters())));}
    @SaCheckPermission("report:runtime:preview") @GetMapping("/runs/{id}")
    public R<RunView> status(@PathVariable String id){return R.ok(view(runtime.status(actors.current(),id)));}
    @SaCheckPermission("report:runtime:preview") @GetMapping("/runs/{id}/page")
    public R<ReportRuntimeService.PageData> page(@PathVariable String id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="50") int size,@RequestParam(required=false) String sort,@RequestParam(defaultValue="false") boolean descending){return R.ok(runtime.page(actors.current(),id,page,size,sort,descending));}
    @SaCheckPermission("report:runtime:preview") @GetMapping("/runs/{id}/layout")
    public R<RenderPlan> layout(@PathVariable String id){return R.ok(runtime.layout(actors.current(),id,"report:runtime:preview","VIEW"));}
    @SaCheckPermission("report:runtime:export") @PostMapping("/runs/{id}/exports")
    public R<JobView> enqueue(@PathVariable String id,@RequestBody ExportRequest body){return R.ok(view(runtime.enqueue(actors.current(),id,body.format(),body.requestKey())));}
    @SaCheckPermission("report:runtime:export") @GetMapping("/exports")
    public R<List<JobView>> jobs(){return R.ok(runtime.jobs(actors.current()).stream().map(ReportRuntimeController::view).toList());}
    @SaCheckPermission("report:runtime:export") @GetMapping("/exports/{id}")
    public R<JobView> job(@PathVariable String id){return R.ok(view(runtime.job(actors.current(),id)));}
    @SaCheckPermission("report:runtime:export") @PostMapping("/exports/{id}/cancel")
    public R<Void> cancel(@PathVariable String id){runtime.cancel(actors.current(),id);return R.ok();}
    @SaCheckPermission("report:runtime:export") @GetMapping("/exports/{id}/file")
    public void file(@PathVariable String id,HttpServletResponse response)throws IOException{
        var actor=actors.current();var job=runtime.job(actor,id);
        if(!job.expires().isAfter(Instant.now())||job.state().equals("EXPIRED"))throw new ReportFault("SNAPSHOT_EXPIRED","导出文件已过期",410);
        if(!job.state().equals("SUCCEEDED")||job.file()==null)throw new ReportFault("RUN_NOT_READY","导出文件尚未生成",409);
        var file=artifacts.read(actor.tenantId(),job.file());String extension=job.format().equals("PDF")?"pdf":"xlsx";
        long size=Files.size(file);
        try(var input=Files.newInputStream(file)){
            response.setContentType(extension.equals("pdf")?"application/pdf":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Cache-Control","no-store, private");response.setHeader("X-Content-Type-Options","nosniff");
            response.setHeader("Content-Disposition",ContentDisposition.attachment().filename("nexus-report-"+job.id()+"."+extension).build().toString());
            response.setContentLengthLong(size);input.transferTo(response.getOutputStream());
        }
    }
}
