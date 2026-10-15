package com.nexus.mes.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** MES工单与检验单候选仅查询MES库，按当前MES业务权限过滤。 */
@Service
@DS("mes")
public class MesReportLookupProvider extends JdbcReportLookupProvider {
    /**
     * 安装旧质量单据及新增台账的有界选择器。
     *
     * @param jdbc MES动态数据源入口
     */
    public MesReportLookupProvider(JdbcTemplate jdbc) {
        super(
                jdbc,
                List.of(
                        new Rule(
                                new ReportLookupBinding(
                                        "MES_IQC_TRACE", 1, "iqcId", "来料检验单", "mes:qc:iqc:query"),
                                "qc_iqc",
                                "iqc_id",
                                "iqc_no",
                                "material_name",
                                ValueType.ID),
                        new Rule(
                                new ReportLookupBinding(
                                        "MES_IQC_RECORDS", 1, "iqcId", "来料检验单", "mes:qc:iqc:list"),
                                "qc_iqc",
                                "iqc_id",
                                "iqc_no",
                                "material_name",
                                ValueType.ID),
                        new Rule(
                                new ReportLookupBinding(
                                        "MES_WORK_ORDER_TRACKING",
                                        1,
                                        "workOrderId",
                                        "生产工单",
                                        "mes:pro:work:list"),
                                "pro_work_order",
                                "mes_wo_id",
                                "wo_no",
                                "material_name",
                                ValueType.ID)));
    }
}
