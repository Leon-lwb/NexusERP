package com.nexus.mes.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Code-owned read-only business projection; raw identifiers never come from report JSON. */
@Service
@DS("mes")
public class QualityTraceDatasetProvider extends JdbcDatasetProvider {
    public QualityTraceDatasetProvider(JdbcTemplate jdbc){super(jdbc,definition(),"qc_iqc q","q","q.iqc_id",columns());}
    private static DatasetDescriptor definition(){return new DatasetDescriptor("MES_IQC_TRACE","来料质量追溯",1,"mes:qc:iqc:query",true,List.of(
        new DatasetField("iqcId","检验ID",ValueType.ID,null,List.of(),true),
        new DatasetField("iqcNo","检验单号",ValueType.STRING,null,List.of(),true),
        new DatasetField("vendorName","供应商",ValueType.STRING,null,List.of(),true),
        new DatasetField("materialCode","物料编码",ValueType.STRING,null,List.of(),true),
        new DatasetField("materialName","物料名称",ValueType.STRING,null,List.of(),true),
        new DatasetField("quantity","来料数量",ValueType.DECIMAL,null,List.of(),false),
        new DatasetField("sampleQty","抽样数量",ValueType.DECIMAL,null,List.of(),false),
        new DatasetField("inspectResult","检验结果",ValueType.STRING,null,List.of(),true),
        new DatasetField("inspectorName","检验人",ValueType.STRING,null,List.of(),true),
        new DatasetField("inspectTime","检验时间",ValueType.DATETIME,null,List.of(),true),
        new DatasetField("defectInfo","缺陷记录",ValueType.STRING,null,List.of(),true)),List.of(
        new DatasetParameter("iqcId","来料检验ID",ValueType.ID,true,null)),List.of("materialCode","materialName","quantity","sampleQty","inspectResult"));}
    private static Map<String,String> columns(){Map<String,String> values=new LinkedHashMap<>();
        values.put("iqcId","q.iqc_id");
        values.put("iqcNo","q.iqc_no");
        values.put("vendorName","q.vendor_name");
        values.put("materialCode","q.material_code");
        values.put("materialName","q.material_name");
        values.put("quantity","q.quantity");
        values.put("sampleQty","q.sample_qty");
        values.put("inspectResult","q.inspect_result");
        values.put("inspectorName","q.inspector_name");
        values.put("inspectTime","q.inspect_time");
        values.put("defectInfo","q.defect_info");
        return values;}
    protected List<Filter> filters(DatasetQuery query){List<Filter> filters=new ArrayList<>();
        equal(filters,query,"iqcId","q.iqc_id");
        return filters;}
}
