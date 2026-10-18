package com.nexus.erp.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** 客户基础台账的固定只读投影；字段、条件与联表规则均由代码审核维护。 */
@Service
@DS("erp")
public class ClientCatalogDatasetProvider extends JdbcDatasetProvider {
    /** @return 与客户页面一致的客户类型字典 */
    @Override
    public Map<String, String> displayDictionaries() {
        return Map.of("clientType", "bd_client_type");
    }

    /**
     * 装配业务数据源上的只读数据集。
     *
     * @param jdbc 随动态数据源切换的查询入口
     */
    public ClientCatalogDatasetProvider(JdbcTemplate jdbc) {
        super(jdbc, definition(), "bd_client p", "p", "p.client_id", columns());
    }

    /**
     * 声明版本一的字段权限、精确类型与汇总维度。
     *
     * @return 不可变数据集契约
     */
    private static DatasetDescriptor definition() {
        return new DatasetDescriptor(
                "ERP_CLIENT_CATALOG",
                "客户基础台账",
                1,
                "bd:client:list",
                false,
                List.of(
                        new DatasetField("clientId", "客户ID", ValueType.ID, null, List.of(), true),
                        new DatasetField(
                                "clientCode", "客户编码", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "clientName", "客户名称", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "shortName", "客户简称", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "clientType", "客户类型", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "salesRepName", "业务员", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "departmentName", "所属部门", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "createdTime", "创建时间", ValueType.DATETIME, null, List.of(), true)),
                List.of(new DatasetParameter("clientId", "客户", ValueType.ID, false, null)),
                List.of(
                        "clientCode",
                        "clientName",
                        "shortName",
                        "clientType",
                        "salesRepName",
                        "departmentName",
                        "createdTime"));
    }

    /**
     * 建立逻辑字段到代码固定SQL表达式的映射，未知状态保留原值。
     *
     * @return 可信投影目录
     */
    private static Map<String, String> columns() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("clientId", "p.client_id");
        values.put("clientCode", "p.client_code");
        values.put("clientName", "p.client_name");
        values.put("shortName", "p.client_nick");
        values.put("clientType", "p.client_type");
        values.put("salesRepName", "p.sales_rep_name");
        values.put("departmentName", "p.dept_name");
        values.put("createdTime", "p.create_time");
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
        equal(filters, query, "clientId", "p.client_id");
        return filters;
    }
}
