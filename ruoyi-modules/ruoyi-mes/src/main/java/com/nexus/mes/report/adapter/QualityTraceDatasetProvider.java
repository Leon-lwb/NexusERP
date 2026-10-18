package com.nexus.mes.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** 代码维护的业务只读投影，物理标识符不会来自报表定义。 */
@Service
@DS("mes")
public class QualityTraceDatasetProvider extends JdbcDatasetProvider {
    /** @return 与检验页面一致的检验结果字典 */
    @Override
    public Map<String, String> displayDictionaries() {
        return Map.of("inspectResult", "mes_qc_result");
    }

    /**
     * 初始化质量追溯数据集提供者并保存所需依赖或不可变值。
     *
     * @param jdbc 当前操作的 {@code jdbc} 输入
     */
    public QualityTraceDatasetProvider(JdbcTemplate jdbc) {
        super(jdbc, definition(), "qc_iqc q", "q", "q.iqc_id", columns());
    }

    /**
     * 读取满足操作权限的报表定义。
     *
     * @return 当前处理返回的固定结果
     */
    private static DatasetDescriptor definition() {
        return new DatasetDescriptor(
                "MES_IQC_TRACE",
                "来料质量追溯",
                1,
                "mes:qc:iqc:query",
                true,
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
                                "quantity", "来料数量", ValueType.DECIMAL, null, List.of(), false),
                        new DatasetField(
                                "sampleQty", "抽样数量", ValueType.DECIMAL, null, List.of(), false),
                        new DatasetField(
                                "inspectResult", "检验结果", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "inspectorName", "检验人", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "inspectTime", "检验时间", ValueType.DATETIME, null, List.of(), true),
                        new DatasetField(
                                "defectInfo", "缺陷记录", ValueType.STRING, null, List.of(), true)),
                List.of(new DatasetParameter("iqcId", "来料检验ID", ValueType.ID, true, null)),
                List.of("materialCode", "materialName", "quantity", "sampleQty", "inspectResult"));
    }

    /**
     * 声明逻辑字段到可信投影的映射。
     *
     * @return 当前处理返回的固定结果
     */
    private static Map<String, String> columns() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("iqcId", "q.iqc_id");
        values.put("iqcNo", "q.iqc_no");
        values.put("vendorName", "q.vendor_name");
        values.put("materialCode", "q.material_code");
        values.put("materialName", "q.material_name");
        values.put("quantity", "q.quantity");
        values.put("sampleQty", "q.sample_qty");
        values.put("inspectResult", "q.inspect_result");
        values.put("inspectorName", "q.inspector_name");
        values.put("inspectTime", "q.inspect_time");
        values.put("defectInfo", "q.defect_info");
        return values;
    }

    /**
     * 生成契约允许的绑定筛选条件。
     *
     * @param query 已声明字段和筛选参数
     * @return 当前处理返回的固定结果
     */
    protected List<Filter> filters(DatasetQuery query) {
        List<Filter> filters = new ArrayList<>();
        equal(filters, query, "iqcId", "q.iqc_id");
        return filters;
    }
}
