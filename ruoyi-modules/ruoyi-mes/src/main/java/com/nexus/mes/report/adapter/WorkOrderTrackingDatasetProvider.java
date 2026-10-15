package com.nexus.mes.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** 工单执行跟踪的固定只读投影；字段、条件与联表规则均由代码审核维护。 */
@Service
@DS("mes")
public class WorkOrderTrackingDatasetProvider extends JdbcDatasetProvider {
    /**
     * 装配业务数据源上的只读数据集。
     *
     * @param jdbc 随动态数据源切换的查询入口
     */
    public WorkOrderTrackingDatasetProvider(JdbcTemplate jdbc) {
        super(jdbc, definition(), "pro_work_order m", "m", "m.mes_wo_id", columns());
    }

    /**
     * 声明版本一的字段权限、精确类型与汇总维度。
     *
     * @return 不可变数据集契约
     */
    private static DatasetDescriptor definition() {
        return new DatasetDescriptor(
                "MES_WORK_ORDER_TRACKING",
                "工单执行跟踪",
                1,
                "mes:pro:work:list",
                false,
                List.of(
                        new DatasetField(
                                "workOrderId", "工单ID", ValueType.ID, null, List.of(), true),
                        new DatasetField(
                                "workOrderNo", "工单编号", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "materialCode", "物料编码", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "materialName", "物料名称", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "workshopName", "车间", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "plannedQuantity",
                                "计划数量",
                                ValueType.DECIMAL,
                                null,
                                List.of("materialCode", "unitName"),
                                true),
                        new DatasetField(
                                "completedQuantity",
                                "完成数量",
                                ValueType.DECIMAL,
                                null,
                                List.of("materialCode", "unitName"),
                                true),
                        new DatasetField(
                                "scrapQuantity",
                                "报废数量",
                                ValueType.DECIMAL,
                                null,
                                List.of("materialCode", "unitName"),
                                true),
                        new DatasetField("unitName", "单位", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "planStartTime",
                                "计划开工时间",
                                ValueType.DATETIME,
                                null,
                                List.of(),
                                true),
                        new DatasetField(
                                "planEndTime", "计划完工时间", ValueType.DATETIME, null, List.of(), true),
                        new DatasetField(
                                "actualStartTime",
                                "实际开工时间",
                                ValueType.DATETIME,
                                null,
                                List.of(),
                                true),
                        new DatasetField(
                                "actualEndTime",
                                "实际完工时间",
                                ValueType.DATETIME,
                                null,
                                List.of(),
                                true),
                        new DatasetField("status", "工单状态", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "createdTime", "创建时间", ValueType.DATETIME, null, List.of(), true)),
                List.of(
                        new DatasetParameter(
                                "fromDate", "创建开始日期", ValueType.DATE, false, "MONTHS_AGO_6"),
                        new DatasetParameter("toDate", "创建结束日期", ValueType.DATE, false, "TODAY"),
                        new DatasetParameter("workOrderId", "MES工单", ValueType.ID, false, null),
                        new DatasetParameter("materialId", "物料", ValueType.ID, false, null),
                        new DatasetParameter("workshopId", "车间", ValueType.ID, false, null)),
                List.of(
                        "workOrderNo",
                        "materialCode",
                        "materialName",
                        "workshopName",
                        "plannedQuantity",
                        "completedQuantity",
                        "scrapQuantity",
                        "unitName",
                        "planStartTime",
                        "planEndTime",
                        "actualStartTime",
                        "actualEndTime",
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
        values.put("workOrderId", "m.mes_wo_id");
        values.put("workOrderNo", "m.wo_no");
        values.put("materialCode", "m.material_code");
        values.put("materialName", "m.material_name");
        values.put("workshopName", "m.workshop_name");
        values.put("plannedQuantity", "m.planned_qty");
        values.put("completedQuantity", "m.completed_qty");
        values.put("scrapQuantity", "m.scrap_qty");
        values.put("unitName", "m.unit_name");
        values.put("planStartTime", "m.plan_start_date");
        values.put("planEndTime", "m.plan_end_date");
        values.put("actualStartTime", "m.actual_start_date");
        values.put("actualEndTime", "m.actual_end_date");
        values.put(
                "status",
                "CASE m.wo_status WHEN 'CREATED' THEN '已创建' WHEN 'RELEASED' THEN '已下达' WHEN"
                    + " 'IN_PROGRESS' THEN '生产中' WHEN 'COMPLETED' THEN '已完成' WHEN 'CANCELLED' THEN"
                    + " '已取消' ELSE m.wo_status END");
        values.put("createdTime", "m.create_time");
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
        filters.addAll(dates(query, "m.create_time"));
        equal(filters, query, "workOrderId", "m.mes_wo_id");
        equal(filters, query, "materialId", "m.material_id");
        equal(filters, query, "workshopId", "m.workshop_id");
        return filters;
    }
}
