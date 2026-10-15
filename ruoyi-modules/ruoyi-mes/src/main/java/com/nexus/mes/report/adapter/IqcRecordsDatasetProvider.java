package com.nexus.mes.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** 来料检验台账的固定只读投影；字段、条件与联表规则均由代码审核维护。 */
@Service
@DS("mes")
public class IqcRecordsDatasetProvider extends JdbcDatasetProvider {
    /**
     * 装配业务数据源上的只读数据集。
     *
     * @param jdbc 随动态数据源切换的查询入口
     */
    public IqcRecordsDatasetProvider(JdbcTemplate jdbc) {
        super(jdbc, definition(), "qc_iqc q", "q", "q.iqc_id", columns());
    }

    /**
     * 声明版本一的字段权限、精确类型与汇总维度。
     *
     * @return 不可变数据集契约
     */
    private static DatasetDescriptor definition() {
        return new DatasetDescriptor(
                "MES_IQC_RECORDS",
                "来料检验台账",
                1,
                "mes:qc:iqc:list",
                false,
                List.of(
                        new DatasetField("iqcId", "检验ID", ValueType.ID, null, List.of(), true),
                        new DatasetField("iqcNo", "检验单号", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "vendorName", "供应商", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "materialCode", "物料编码", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "materialName", "物料名称", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "quantity",
                                "检验数量（原单口径）",
                                ValueType.DECIMAL,
                                null,
                                List.of(),
                                false),
                        new DatasetField(
                                "sampleQuantity",
                                "抽样数量（原单口径）",
                                ValueType.DECIMAL,
                                null,
                                List.of(),
                                false),
                        new DatasetField("result", "检验结果", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "inspectorName", "检验员", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "inspectTime", "检验时间", ValueType.DATETIME, null, List.of(), true),
                        new DatasetField("status", "检验状态", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "createdTime", "创建时间", ValueType.DATETIME, null, List.of(), true)),
                List.of(
                        new DatasetParameter(
                                "fromDate", "创建开始日期", ValueType.DATE, false, "MONTHS_AGO_6"),
                        new DatasetParameter("toDate", "创建结束日期", ValueType.DATE, false, "TODAY"),
                        new DatasetParameter("iqcId", "来料检验单", ValueType.ID, false, null),
                        new DatasetParameter("vendorId", "供应商", ValueType.ID, false, null),
                        new DatasetParameter("materialId", "物料", ValueType.ID, false, null)),
                List.of(
                        "iqcNo",
                        "vendorName",
                        "materialCode",
                        "materialName",
                        "quantity",
                        "sampleQuantity",
                        "result",
                        "inspectorName",
                        "inspectTime",
                        "status",
                        "createdTime"));
    }

    /**
     * 建立逻辑字段到代码固定SQL表达式的映射，未知状态保留原值。
     *
     * @return 可信投影目录
     */
    private static Map<String, String> columns() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("iqcId", "q.iqc_id");
        values.put("iqcNo", "q.iqc_no");
        values.put("vendorName", "q.vendor_name");
        values.put("materialCode", "q.material_code");
        values.put("materialName", "q.material_name");
        values.put("quantity", "q.quantity");
        values.put("sampleQuantity", "q.sample_qty");
        values.put(
                "result",
                "CASE q.inspect_result WHEN 'QUALIFIED' THEN '合格' WHEN 'UNQUALIFIED' THEN '不合格'"
                        + " WHEN 'PENDING' THEN '待判定' ELSE q.inspect_result END");
        values.put("inspectorName", "q.inspector_name");
        values.put("inspectTime", "q.inspect_time");
        values.put(
                "status",
                "CASE q.status WHEN 'PENDING' THEN '待检验' WHEN 'INSPECTING' THEN '检验中' WHEN"
                        + " 'COMPLETED' THEN '已完成' ELSE q.status END");
        values.put("createdTime", "q.create_time");
        return values;
    }

    /**
     * 仅对已声明参数生成绑定条件，业务范围由公共底座附加。
     *
     * @param query 已校验的有界查询
     * @return 只包含可信表达式和绑定值的条件列表
     */
    @Override
    protected List<Filter> filters(DatasetQuery query) {
        List<Filter> filters = new ArrayList<>();
        filters.addAll(dates(query, "q.create_time"));
        equal(filters, query, "iqcId", "q.iqc_id");
        equal(filters, query, "vendorId", "q.vendor_id");
        equal(filters, query, "materialId", "q.material_id");
        return filters;
    }
}
