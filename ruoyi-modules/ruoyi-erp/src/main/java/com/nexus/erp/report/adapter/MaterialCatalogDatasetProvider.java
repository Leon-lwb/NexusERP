package com.nexus.erp.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** 物料基础台账的固定只读投影；字段、条件与联表规则均由代码审核维护。 */
@Service
@DS("erp")
public class MaterialCatalogDatasetProvider extends JdbcDatasetProvider {
    /**
     * 装配业务数据源上的只读数据集。
     *
     * @param jdbc 随动态数据源切换的查询入口
     */
    public MaterialCatalogDatasetProvider(JdbcTemplate jdbc) {
        super(jdbc, definition(), "bd_material p", "p", "p.material_id", columns());
    }

    /**
     * 声明版本一的字段权限、精确类型与汇总维度。
     *
     * @return 不可变数据集契约
     */
    private static DatasetDescriptor definition() {
        return new DatasetDescriptor(
                "ERP_MATERIAL_CATALOG",
                "物料基础台账",
                1,
                "bd:material:list",
                false,
                List.of(
                        new DatasetField("materialId", "物料ID", ValueType.ID, null, List.of(), true),
                        new DatasetField(
                                "materialCode", "物料编码", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "materialName", "物料名称", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "specification", "规格型号", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "materialType", "物料类型", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "sourceType", "来源类型", ValueType.STRING, null, List.of(), true),
                        new DatasetField("status", "状态", ValueType.STRING, null, List.of(), true)),
                List.of(new DatasetParameter("materialId", "物料", ValueType.ID, false, null)),
                List.of(
                        "materialCode",
                        "materialName",
                        "specification",
                        "materialType",
                        "sourceType",
                        "status"));
    }

    /**
     * 建立逻辑字段到代码固定SQL表达式的映射，未知状态保留原值。
     *
     * @return 可信投影目录
     */
    private static Map<String, String> columns() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("materialId", "p.material_id");
        values.put("materialCode", "p.material_code");
        values.put("materialName", "p.material_name");
        values.put("specification", "p.material_spec");
        values.put(
                "materialType",
                "CASE p.material_type WHEN '1' THEN '原材料' WHEN '2' THEN '半成品' WHEN '3' THEN '成品'"
                        + " WHEN '4' THEN '辅料' ELSE CAST(p.material_type AS CHAR(32)) END");
        values.put(
                "sourceType",
                "CASE p.source_type WHEN 'PROCESS' THEN '加工' WHEN 'PROCUREMENT' THEN '采购' WHEN"
                    + " 'SUBCONTRACT' THEN '委外' WHEN 'PHANTOM' THEN '虚拟件' WHEN 'CFM' THEN '客户供料'"
                    + " WHEN 'TRANSFER' THEN '调拨' ELSE p.source_type END");
        values.put(
                "status",
                "CASE p.status WHEN '0' THEN '停用' WHEN '1' THEN '启用' ELSE CAST(p.status AS"
                        + " CHAR(32)) END");
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
        equal(filters, query, "materialId", "p.material_id");
        return filters;
    }
}
