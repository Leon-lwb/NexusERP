package com.nexus.report.service;
import com.nexus.common.report.*;
import com.nexus.report.domain.ReportDsl;
import java.util.*;
/** Original native starter templates, created only by the authenticated bootstrap action. */
public final class ReportTemplates {
    private ReportTemplates() { }
    public static ReportDsl initial(DatasetDescriptor d,String datasetVersion,String code){
        var page=ReportDsl.Page.a4();var selected=d.defaultColumns();double totalWeight=selected.stream().mapToDouble(name->name.equals("materialName")||name.equals("clientName")||name.equals("vendorName")?2:1).sum();
        List<ReportDsl.Column> columns=new ArrayList<>();for(String name:selected){var field=d.field(name);double weight=name.equals("materialName")||name.equals("clientName")||name.equals("vendorName")?2:1;double width=d.document()?page.usableWidth()*weight/totalWeight:120;
            int scale=field.type()==ValueType.DECIMAL?(name.endsWith("Amount")?2:4):0;columns.add(new ReportDsl.Column(name,field.label(),width,scale,null,null));}
        var parameters=d.parameters().stream().map(p->new ReportDsl.Parameter(p.name(),p.label(),p.type().name(),p.required(),null)).toList();List<ReportDsl.BandRow> header=new ArrayList<>(),footer=new ArrayList<>();
        if(d.document()){
            String line=switch(d.code()){case "ERP_PURCHASE_ORDER_DOCUMENT"->"采购单号：{{orderNo}}";case "ERP_DELIVERY_DOCUMENT"->"送货单号：{{issueNo}}";case "MES_IQC_TRACE"->"检验单号：{{iqcNo}}";default->"记录数：{{row_count}}";};
            String second=switch(d.code()){case "ERP_PURCHASE_ORDER_DOCUMENT"->"供应商：{{vendorName}}    日期：{{orderDate}}";case "ERP_DELIVERY_DOCUMENT"->"客户：{{clientName}}    日期：{{issueDate}}";case "MES_IQC_TRACE"->"供应商：{{vendorName}}    检验人：{{inspectorName}}";default->"";};
            header.add(new ReportDsl.BandRow(40,List.of(new ReportDsl.Cell(0,columns.size(),1,line,null,null))));header.add(new ReportDsl.BandRow(48,List.of(new ReportDsl.Cell(0,columns.size(),1,second,null,null))));
            String finalLine=switch(d.code()){case "ERP_PURCHASE_ORDER_DOCUMENT"->"订单金额：{{orderAmount}} {{currency}}";case "MES_IQC_TRACE"->"检验时间：{{inspectTime}}";default->"签收：";};
            footer.add(new ReportDsl.BandRow(32,List.of(new ReportDsl.Cell(0,columns.size(),1,finalLine,null,null))));
        }
        return new ReportDsl(1,code,d.name(),d.document()?ReportDsl.Kind.DOCUMENT:ReportDsl.Kind.TABLE,d.code(),datasetVersion,columns,parameters,page,header,footer,List.of(new ReportDsl.Summary(null,"COUNT_ROWS",List.of(),d.document()?"明细条数":"记录条数",0)),true);
    }
}
