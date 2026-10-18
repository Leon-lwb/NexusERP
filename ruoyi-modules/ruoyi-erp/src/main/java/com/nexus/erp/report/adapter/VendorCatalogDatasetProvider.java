package com.nexus.erp.report.adapter;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.report.*;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** 供应商基础台账的固定只读投影；字段、条件与联表规则均由代码审核维护。 */
@Service
@DS("erp")
public class VendorCatalogDatasetProvider extends JdbcDatasetProvider {
    /** @return 与供应商页面一致的分类、行业和区域字典 */
    @Override
    public Map<String, String> displayDictionaries() {
        return Map.of("category", "bd_vendor_category", "industry", "bd_vendor_industry", "region", "bd_region");
    }

    /**
     * 装配业务数据源上的只读数据集。
     *
     * @param jdbc 随动态数据源切换的查询入口
     */
    public VendorCatalogDatasetProvider(JdbcTemplate jdbc) {
        super(jdbc, definition(), "bd_vendor p", "p", "p.vendor_id", columns());
    }

    /**
     * 声明版本一的字段权限、精确类型与汇总维度。
     *
     * @return 不可变数据集契约
     */
    private static DatasetDescriptor definition() {
        return new DatasetDescriptor(
                "ERP_VENDOR_CATALOG",
                "供应商基础台账",
                1,
                "bd:vendor:list",
                false,
                List.of(
                        new DatasetField("vendorId", "供应商ID", ValueType.ID, null, List.of(), true),
                        new DatasetField(
                                "vendorCode", "供应商编码", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "vendorName", "供应商名称", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "category", "供应商分类", ValueType.STRING, null, List.of(), true),
                        new DatasetField("industry", "行业", ValueType.STRING, null, List.of(), true),
                        new DatasetField("region", "区域", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "buyerName", "采购员", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "departmentName", "所属部门", ValueType.STRING, null, List.of(), true),
                        new DatasetField("status", "状态", ValueType.STRING, null, List.of(), true),
                        new DatasetField(
                                "createdTime", "创建时间", ValueType.DATETIME, null, List.of(), true)),
                List.of(new DatasetParameter("vendorId", "供应商", ValueType.ID, false, null)),
                List.of(
                        "vendorCode",
                        "vendorName",
                        "category",
                        "industry",
                        "region",
                        "buyerName",
                        "departmentName",
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
        values.put("vendorId", "p.vendor_id");
        values.put("vendorCode", "p.vendor_code");
        values.put("vendorName", "p.vendor_name");
        values.put("category", "p.category");
        values.put("industry", "p.industry");
        values.put("region", "p.region");
        values.put("buyerName", "p.buyer_name");
        values.put("departmentName", "p.dept_name");
        values.put(
                "status",
                "CASE p.status WHEN 'ACTIVE' THEN '启用' WHEN 'FROZEN' THEN '冻结' ELSE p.status END");
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
        equal(filters, query, "vendorId", "p.vendor_id");
        return filters;
    }
}
