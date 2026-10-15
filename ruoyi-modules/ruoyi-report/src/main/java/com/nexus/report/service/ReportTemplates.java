package com.nexus.report.service;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportDsl;

import java.util.*;

/** 原生初始模板仅由已登录管理员显式初始化，不在启动时创建。 */
public final class ReportTemplates {
    /** 初始化报表初始模板并保存所需依赖或不可变值。 */
    private ReportTemplates() {}

    /**
     * 根据已授权的固定数据集生成默认列、日期规则和安全汇总。
     *
     * @param d 按当前字段权限裁剪的可信契约
     * @param datasetVersion 已发布且不可变的数据集版本
     * @param code 业务编码
     * @return 可交给一版编译器校验的基础定义
     */
    public static ReportDsl initial(DatasetDescriptor d, String datasetVersion, String code) {
        var page = ReportDsl.Page.a4();
        var selected = d.defaultColumns();
        double totalWeight =
                selected.stream()
                        .mapToDouble(
                                name ->
                                        name.equals("materialName")
                                                        || name.equals("clientName")
                                                        || name.equals("vendorName")
                                                ? 2
                                                : 1)
                        .sum();
        List<ReportDsl.Column> columns = new ArrayList<>();
        for (String name : selected) {
            var field = d.field(name);
            double weight =
                    name.equals("materialName")
                                    || name.equals("clientName")
                                    || name.equals("vendorName")
                            ? 2
                            : 1;
            double width = d.document() ? page.usableWidth() * weight / totalWeight : width(field);
            int scale = field.type() == ValueType.DECIMAL ? (name.endsWith("Amount") ? 2 : 4) : 0;
            columns.add(new ReportDsl.Column(name, field.label(), width, scale, null, null));
        }
        var parameters =
                d.parameters().stream()
                        .map(
                                p ->
                                        new ReportDsl.Parameter(
                                                p.name(),
                                                p.label(),
                                                p.type().name(),
                                                p.required(),
                                                p.defaultValue()))
                        .toList();
        List<ReportDsl.BandRow> header = new ArrayList<>(), footer = new ArrayList<>();
        if (d.document()) {
            String line =
                    switch (d.code()) {
                        case "ERP_PURCHASE_ORDER_DOCUMENT" -> "采购单号：{{orderNo}}";
                        case "ERP_DELIVERY_DOCUMENT" -> "送货单号：{{issueNo}}";
                        case "MES_IQC_TRACE" -> "检验单号：{{iqcNo}}";
                        default -> "记录数：{{row_count}}";
                    };
            String second =
                    switch (d.code()) {
                        case "ERP_PURCHASE_ORDER_DOCUMENT" ->
                                "供应商：{{vendorName}}    日期：{{orderDate}}";
                        case "ERP_DELIVERY_DOCUMENT" -> "客户：{{clientName}}    日期：{{issueDate}}";
                        case "MES_IQC_TRACE" -> "供应商：{{vendorName}}    检验人：{{inspectorName}}";
                        default -> "";
                    };
            header.add(
                    new ReportDsl.BandRow(
                            40,
                            List.of(new ReportDsl.Cell(0, columns.size(), 1, line, null, null))));
            header.add(
                    new ReportDsl.BandRow(
                            48,
                            List.of(new ReportDsl.Cell(0, columns.size(), 1, second, null, null))));
            String finalLine =
                    switch (d.code()) {
                        case "ERP_PURCHASE_ORDER_DOCUMENT" ->
                                has(d, "orderAmount", "currency")
                                        ? "订单金额：{{orderAmount}} {{currency}}"
                                        : "采购确认：";
                        case "MES_IQC_TRACE" -> "检验时间：{{inspectTime}}";
                        default -> "签收：";
                    };
            footer.add(
                    new ReportDsl.BandRow(
                            32,
                            List.of(
                                    new ReportDsl.Cell(
                                            0, columns.size(), 1, finalLine, null, null))));
        }
        return new ReportDsl(
                1,
                code,
                d.name(),
                d.document() ? ReportDsl.Kind.DOCUMENT : ReportDsl.Kind.TABLE,
                d.code(),
                datasetVersion,
                columns,
                parameters,
                page,
                header,
                footer,
                summaries(d),
                true);
    }

    /**
     * 列出当前契约可生成的基础报表，已有同编码定义由调用方保留。
     *
     * @param descriptor 按当前字段权限裁剪的契约
     * @param datasetVersion 已发布数据集版本
     * @return 默认模板及满足字段条件的扩展模板
     */
    public static List<ReportDsl> foundation(DatasetDescriptor descriptor, String datasetVersion) {
        var base = initial(descriptor, datasetVersion, "BASE_" + descriptor.code());
        if (base.columns().isEmpty()) return List.of();
        List<ReportDsl> templates = new ArrayList<>();
        templates.add(base);
        if (descriptor.code().equals("ERP_STOCK_BALANCE")
                && has(descriptor, "quantity", "reservedQuantity", "materialCode", "unitName")) {
            var columns = new ArrayList<>(base.columns());
            columns.add(
                    new ReportDsl.Column(
                            "availableQuantity",
                            "可用量（现存减预留）",
                            190,
                            4,
                            "quantity-reservedQuantity",
                            null));
            var summaries = new ArrayList<>(base.summaries());
            addSum(summaries, descriptor, "quantity");
            addSum(summaries, descriptor, "reservedQuantity");
            templates.add(
                    copy(base, "BASE_ERP_STOCK_BALANCE_AVAILABLE", "库存可用量明细", columns, summaries));
        }
        if (Set.of("ERP_SALES_EXECUTION", "ERP_PURCHASE_EXECUTION").contains(descriptor.code())
                && has(descriptor, "totalAmount", "currency")) {
            var summaries = new ArrayList<>(base.summaries());
            addSum(summaries, descriptor, "totalAmount");
            templates.add(
                    copy(
                            base,
                            base.code() + "_TOTALS",
                            descriptor.name() + "与分币种合计",
                            base.columns(),
                            summaries));
        }
        return List.copyOf(templates);
    }

    /**
     * 为表格推荐业务名称、长单号、时间和数量的可读列宽。
     *
     * @param field 可信逻辑字段
     * @return 以像素表示的推荐宽度
     */
    private static double width(DatasetField field) {
        if (field.name().endsWith("Name") || field.name().equals("specification")) return 200;
        if (field.type() == ValueType.DATETIME) return 180;
        if (field.type() == ValueType.DATE) return 130;
        if (field.type() == ValueType.DECIMAL)
            return Math.max(140, Math.min(260, field.label().length() * 14));
        if (field.name().endsWith("No") || field.name().endsWith("Code")) return 180;
        return 140;
    }

    /**
     * 仅对允许汇总的原始字段生成有维度的合计。
     *
     * @param descriptor 已授权的契约
     * @return 记录计数与业务数量、金额合计
     */
    private static List<ReportDsl.Summary> summaries(DatasetDescriptor descriptor) {
        List<ReportDsl.Summary> result = new ArrayList<>();
        result.add(
                new ReportDsl.Summary(
                        null, "COUNT_ROWS", List.of(), descriptor.document() ? "明细条数" : "记录条数", 0));
        switch (descriptor.code()) {
            case "ERP_SALES_ORDER_LINES" -> {
                for (String field :
                        List.of(
                                "orderedQuantity",
                                "deliveredQuantity",
                                "returnedQuantity",
                                "remainingQuantity",
                                "lineAmount")) addSum(result, descriptor, field);
            }
            case "ERP_PURCHASE_ORDER_LINES" -> {
                for (String field :
                        List.of(
                                "orderedQuantity",
                                "receivedQuantity",
                                "returnedQuantity",
                                "remainingQuantity",
                                "lineAmount")) addSum(result, descriptor, field);
            }
            case "MES_WORK_ORDER_TRACKING" -> {
                for (String field :
                        List.of("plannedQuantity", "completedQuantity", "scrapQuantity"))
                    addSum(result, descriptor, field);
            }
            default -> {}
        }
        return List.copyOf(result);
    }

    /**
     * 添加保留全部口径维度且已通过字段授权的求和规则。
     *
     * @param target 待填充的汇总列表
     * @param descriptor 当前字段契约
     * @param name 原始度量名称
     */
    private static void addSum(
            List<ReportDsl.Summary> target, DatasetDescriptor descriptor, String name) {
        if (!has(descriptor, name)) return;
        var field = descriptor.field(name);
        if (!field.aggregatable()
                || !has(descriptor, field.aggregationKeys().toArray(String[]::new))) return;
        target.add(
                new ReportDsl.Summary(
                        name,
                        "SUM",
                        field.aggregationKeys(),
                        field.label() + "合计",
                        name.endsWith("Amount") ? 2 : 4));
    }

    /**
     * 判断模板引用的所有字段是否仍在授权契约内。
     *
     * @param descriptor 当前契约
     * @param names 必须同时存在的字段
     * @return 全部存在时为真
     */
    private static boolean has(DatasetDescriptor descriptor, String... names) {
        return Arrays.stream(names)
                .allMatch(
                        name ->
                                descriptor.fields().stream()
                                        .anyMatch(field -> field.name().equals(name)));
    }

    /**
     * 创建使用同一固定数据集版本的独立模板变体。
     *
     * @param base 默认模板
     * @param code 新模板稳定编码
     * @param title 用户可见标题
     * @param columns 新模板的列
     * @param summaries 新模板的可信汇总
     * @return 一版独立报表定义
     */
    private static ReportDsl copy(
            ReportDsl base,
            String code,
            String title,
            List<ReportDsl.Column> columns,
            List<ReportDsl.Summary> summaries) {
        return new ReportDsl(
                base.schemaVersion(),
                code,
                title,
                base.kind(),
                base.datasetCode(),
                base.datasetVersionId(),
                columns,
                base.parameters(),
                base.page(),
                base.headerRows(),
                base.footerRows(),
                summaries,
                base.repeatHeader());
    }
}
