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
    private final ReportRuntimeService runtime;private final ReportProperties properties;
    public ReportExportRenderer(ReportRuntimeService runtime,ReportProperties properties){this.runtime=runtime;this.properties=properties;}
    public void write(ExportJob job,ReportRuntimeService.Access access,OutputStream output,ReportCancellation cancellation){
        if(job.format().equals("XLSX_DATA")){new ReportSpreadsheetExporter().data(access.binding().compiled().dsl(),consumer->runtime.each(access,consumer,cancellation),output,cancellation);return;}
        if(access.run().rows()>5000)throw ReportFault.capacity();
        var rows=new java.util.ArrayList<java.util.Map<String,com.nexus.common.report.TypedValue>>();runtime.each(access,rows::add,cancellation);
        var font=FontSupport.find(properties.getFontPath());var plan=new DocumentLayoutEngine().layout(access.binding().compiled(),new ReportCalculator.CalculatedData(rows,access.payload().summaries()),access.payload().input(),font);
        cancellation.check();
        if(job.format().equals("PDF"))new ReportPdfExporter().write(plan,output,font,cancellation);
        else if(job.format().equals("XLSX_LAYOUT"))new ReportSpreadsheetExporter().layout(plan,output,cancellation);
        else throw ReportFault.invalid("导出格式不支持");
    }
}
