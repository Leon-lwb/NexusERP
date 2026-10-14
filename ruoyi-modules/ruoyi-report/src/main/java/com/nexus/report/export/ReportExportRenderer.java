package com.nexus.report.export;
import com.nexus.common.report.ReportCancellation;
import com.nexus.report.config.ReportProperties;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.domain.ReportRecords.ExportJob;
import com.nexus.report.engine.*;
import com.nexus.report.runtime.ReportRuntimeService;
import java.io.OutputStream;
/** Extend this adapter with a new exporter without changing definitions or dataset providers. */
public final class ReportExportRenderer implements ReportExportWorker.Renderer {
    private final ReportRuntimeService runtime;private final long temporaryBudget;
    public ReportExportRenderer(ReportRuntimeService runtime,ReportProperties properties){this.runtime=runtime;this.temporaryBudget=properties.getMaxArtifactBytes();}
    public void write(ExportJob job,ReportRuntimeService.Access access,OutputStream output,ReportCancellation cancellation){long started=System.nanoTime();boolean success=false;try{render(job,access,output,cancellation);success=true;}finally{runtime.recordExport(success,System.nanoTime()-started);}}
    private void render(ExportJob job,ReportRuntimeService.Access access,OutputStream output,ReportCancellation cancellation){
        if(job.format().equals("XLSX_DATA")){runtime.spreadsheetExporter(access,temporaryBudget).data(access.binding().compiled().dsl(),consumer->runtime.each(access,consumer,cancellation),output,cancellation);return;}
        if(access.run().rows()>5000)throw ReportFault.capacity();
        var plan=runtime.layout(access,cancellation);
        cancellation.check();
        if(job.format().equals("PDF"))runtime.withLayoutFont(access,cancellation,font->new ReportPdfExporter().write(plan,output,font,cancellation));
        else if(job.format().equals("XLSX_LAYOUT"))new ReportSpreadsheetExporter().layout(plan,output,cancellation);
        else throw ReportFault.invalid("导出格式不支持");
    }
}
