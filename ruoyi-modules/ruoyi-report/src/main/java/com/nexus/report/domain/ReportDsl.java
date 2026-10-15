package com.nexus.report.domain;

import java.util.*;

/**
 * 稳定的一版报表模型，独立于前端编辑器状态和第三方表格格式。
 *
 * @param schemaVersion 当前契约的schemaVersion配置或状态
 * @param code 当前契约的code配置或状态
 * @param title 当前契约的title配置或状态
 * @param kind 当前契约的kind配置或状态
 * @param datasetCode 当前契约的datasetCode配置或状态
 * @param datasetVersionId 当前契约的datasetVersionId配置或状态
 * @param columns 输出列契约
 * @param parameters 固定参数契约
 * @param page 当前契约的page配置或状态
 * @param headerRows 当前契约的headerRows配置或状态
 * @param footerRows 当前契约的footerRows配置或状态
 * @param summaries 当前契约的summaries配置或状态
 * @param repeatHeader 当前契约的repeatHeader配置或状态
 * @param pivot 当前契约的pivot配置或状态
 * @param print 当前契约的print配置或状态
 */
public record ReportDsl(
        int schemaVersion,
        String code,
        String title,
        Kind kind,
        String datasetCode,
        String datasetVersionId,
        List<Column> columns,
        List<Parameter> parameters,
        Page page,
        List<BandRow> headerRows,
        List<BandRow> footerRows,
        List<Summary> summaries,
        Boolean repeatHeader,
        @com.fasterxml.jackson.annotation.JsonInclude(
                        com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                com.nexus.report.pivot.PivotDefinition pivot,
        @com.fasterxml.jackson.annotation.JsonInclude(
                        com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                com.nexus.report.print.PrintDefinition print) {
    /**
     * 保留第一版构造契约，旧模板不增加空扩展字段。
     *
     * @param schemaVersion 当前契约的schemaVersion配置或状态
     * @param code 当前契约的code配置或状态
     * @param title 当前契约的title配置或状态
     * @param kind 当前契约的kind配置或状态
     * @param datasetCode 当前契约的datasetCode配置或状态
     * @param datasetVersionId 当前契约的datasetVersionId配置或状态
     * @param columns 输出列契约
     * @param parameters 固定参数契约
     * @param page 当前契约的page配置或状态
     * @param headerRows 当前契约的headerRows配置或状态
     * @param footerRows 当前契约的footerRows配置或状态
     * @param summaries 当前契约的summaries配置或状态
     * @param repeatHeader 当前契约的repeatHeader配置或状态
     */
    public ReportDsl(
            int schemaVersion,
            String code,
            String title,
            Kind kind,
            String datasetCode,
            String datasetVersionId,
            List<Column> columns,
            List<Parameter> parameters,
            Page page,
            List<BandRow> headerRows,
            List<BandRow> footerRows,
            List<Summary> summaries,
            Boolean repeatHeader) {
        this(
                schemaVersion,
                code,
                title,
                kind,
                datasetCode,
                datasetVersionId,
                columns,
                parameters,
                page,
                headerRows,
                footerRows,
                summaries,
                repeatHeader,
                null,
                null);
    }

    /**
     * 保留交叉阶段的构造方式，打印配置显式为空。
     *
     * @param schemaVersion 当前契约的schemaVersion配置或状态
     * @param code 当前契约的code配置或状态
     * @param title 当前契约的title配置或状态
     * @param kind 当前契约的kind配置或状态
     * @param datasetCode 当前契约的datasetCode配置或状态
     * @param datasetVersionId 当前契约的datasetVersionId配置或状态
     * @param columns 输出列契约
     * @param parameters 固定参数契约
     * @param page 当前契约的page配置或状态
     * @param headerRows 当前契约的headerRows配置或状态
     * @param footerRows 当前契约的footerRows配置或状态
     * @param summaries 当前契约的summaries配置或状态
     * @param repeatHeader 当前契约的repeatHeader配置或状态
     * @param pivot 当前契约的pivot配置或状态
     */
    public ReportDsl(
            int schemaVersion,
            String code,
            String title,
            Kind kind,
            String datasetCode,
            String datasetVersionId,
            List<Column> columns,
            List<Parameter> parameters,
            Page page,
            List<BandRow> headerRows,
            List<BandRow> footerRows,
            List<Summary> summaries,
            Boolean repeatHeader,
            com.nexus.report.pivot.PivotDefinition pivot) {
        this(
                schemaVersion,
                code,
                title,
                kind,
                datasetCode,
                datasetVersionId,
                columns,
                parameters,
                page,
                headerRows,
                footerRows,
                summaries,
                repeatHeader,
                pivot,
                null);
    }

    /** Kind，负责当前模块对应的契约或处理职责。 */
    public enum Kind {
        /** 保存当前 {@code TABLE} 值。 */
        TABLE,
        /** 保存当前 {@code DOCUMENT} 值。 */
        DOCUMENT,
        /** 使用第二版执行器计算固定快照交叉表。 */
        PIVOT
    }

    /**
     * 初始化报表Dsl并保存所需依赖或不可变值。
     *
     * @param schemaVersion 当前操作的 {@code schemaVersion} 输入
     * @param code 业务编码
     * @param title 当前操作的 {@code title} 输入
     * @param kind 受支持类型
     * @param datasetCode 当前操作的 {@code datasetCode} 输入
     * @param datasetVersionId 当前操作的 {@code datasetVersionId} 输入
     * @param columns 当前操作的 {@code columns} 输入
     * @param parameters 按契约解析的输入参数
     * @param page 当前页
     * @param headerRows 当前操作的 {@code headerRows} 输入
     * @param footerRows 当前操作的 {@code footerRows} 输入
     * @param summaries 当前操作的 {@code summaries} 输入
     * @param repeatHeader 当前操作的 {@code repeatHeader} 输入
     * @param pivot 当前契约的pivot配置或状态
     * @param print 当前契约的print配置或状态
     */
    public ReportDsl {
        columns = List.copyOf(columns == null ? List.of() : columns);
        parameters = List.copyOf(parameters == null ? List.of() : parameters);
        page = page == null ? Page.a4() : page;
        headerRows = List.copyOf(headerRows == null ? List.of() : headerRows);
        footerRows = List.copyOf(footerRows == null ? List.of() : footerRows);
        summaries = List.copyOf(summaries == null ? List.of() : summaries);
        repeatHeader = repeatHeader == null ? true : repeatHeader;
    }

    /**
     * 参数，负责当前模块对应的契约或处理职责。
     *
     * @param name 契约名称
     * @param label 用户可见标签
     * @param type 受控类型
     * @param required 是否必须提供该参数
     * @param defaultValue 固定默认值或白名单动态日期规则
     */
    public record Parameter(
            String name, String label, String type, boolean required, String defaultValue) {}

    /**
     * Column，负责当前模块对应的契约或处理职责。
     *
     * @param field 已批准的逻辑字段
     * @param label 用户可见标签
     * @param width 布局宽度
     * @param scale 输出小数位数
     * @param expression 当前契约的expression配置或状态
     * @param style 当前契约的style配置或状态
     */
    public record Column(
            String field, String label, double width, int scale, String expression, Style style) {

        /**
         * 初始化Column并保存所需依赖或不可变值。
         *
         * @param field 当前操作的 {@code field} 输入
         * @param label 展示标题
         * @param width 当前操作的 {@code width} 输入
         * @param scale 当前操作的 {@code scale} 输入
         * @param expression 当前操作的 {@code expression} 输入
         * @param style 当前操作的 {@code style} 输入
         */
        public Column {
            width = width == 0 ? 96 : width;
            style = style == null ? Style.normal() : style;
        }
    }

    /**
     * Page，负责当前模块对应的契约或处理职责。
     *
     * @param width 布局宽度
     * @param height 布局高度
     * @param marginTop 当前契约的marginTop配置或状态
     * @param marginRight 当前契约的marginRight配置或状态
     * @param marginBottom 当前契约的marginBottom配置或状态
     * @param marginLeft 当前契约的marginLeft配置或状态
     * @param rowHeight 当前契约的rowHeight配置或状态
     */
    public record Page(
            double width,
            double height,
            double marginTop,
            double marginRight,
            double marginBottom,
            double marginLeft,
            double rowHeight) {
        /**
         * 完成 {@code a4} 对应的Page处理。
         *
         * @return 当前处理返回的固定结果
         */
        public static Page a4() {
            return new Page(595.28, 841.89, 36, 36, 36, 36, 24);
        }

        /**
         * 计算纸张扣除边距后的可用宽度。
         *
         * @return 当前处理返回的固定结果
         */
        public double usableWidth() {
            return width - marginLeft - marginRight;
        }
    }

    /**
     * Style，负责当前模块对应的契约或处理职责。
     *
     * @param fontSize 当前契约的fontSize配置或状态
     * @param bold 当前契约的bold配置或状态
     * @param align 当前契约的align配置或状态
     * @param color 当前契约的color配置或状态
     * @param background 当前契约的background配置或状态
     */
    public record Style(
            double fontSize, boolean bold, String align, String color, String background) {

        /**
         * 初始化Style并保存所需依赖或不可变值。
         *
         * @param fontSize 当前操作的 {@code fontSize} 输入
         * @param bold 当前操作的 {@code bold} 输入
         * @param align 当前操作的 {@code align} 输入
         * @param color 当前操作的 {@code color} 输入
         * @param background 当前操作的 {@code background} 输入
         */
        public Style {
            fontSize = fontSize == 0 ? 10 : fontSize;
            align = align == null ? "LEFT" : align;
            color = color == null ? "#1f2937" : color;
            background = background == null ? "#ffffff" : background;
        }

        /**
         * 完成 {@code normal} 对应的Style处理。
         *
         * @return 当前处理返回的固定结果
         */
        public static Style normal() {
            return new Style(10, false, "LEFT", null, null);
        }
    }

    /**
     * BandRow，负责当前模块对应的契约或处理职责。
     *
     * @param height 布局高度
     * @param cells 固定坐标的单元格列表
     */
    public record BandRow(double height, List<Cell> cells) {

        /**
         * 初始化BandRow并保存所需依赖或不可变值。
         *
         * @param height 当前操作的 {@code height} 输入
         * @param cells 当前操作的 {@code cells} 输入
         */
        public BandRow {
            cells = List.copyOf(cells == null ? List.of() : cells);
        }
    }

    /**
     * Cell，负责当前模块对应的契约或处理职责。
     *
     * @param column 当前契约的column配置或状态
     * @param colSpan 当前契约的colSpan配置或状态
     * @param rowSpan 当前契约的rowSpan配置或状态
     * @param text 当前契约的text配置或状态
     * @param field 已批准的逻辑字段
     * @param style 当前契约的style配置或状态
     */
    public record Cell(
            int column, int colSpan, int rowSpan, String text, String field, Style style) {

        /**
         * 初始化Cell并保存所需依赖或不可变值。
         *
         * @param column 当前操作的 {@code column} 输入
         * @param colSpan 当前操作的 {@code colSpan} 输入
         * @param rowSpan 当前操作的 {@code rowSpan} 输入
         * @param text 当前操作的 {@code text} 输入
         * @param field 当前操作的 {@code field} 输入
         * @param style 当前操作的 {@code style} 输入
         */
        public Cell {
            colSpan = colSpan == 0 ? 1 : colSpan;
            rowSpan = rowSpan == 0 ? 1 : rowSpan;
            style = style == null ? Style.normal() : style;
        }
    }

    /**
     * Summary，负责当前模块对应的契约或处理职责。
     *
     * @param field 已批准的逻辑字段
     * @param function 白名单聚合函数
     * @param groupBy 当前契约的groupBy配置或状态
     * @param label 用户可见标签
     * @param scale 输出小数位数
     */
    public record Summary(
            String field, String function, List<String> groupBy, String label, int scale) {

        /**
         * 初始化Summary并保存所需依赖或不可变值。
         *
         * @param field 当前操作的 {@code field} 输入
         * @param function 当前操作的 {@code function} 输入
         * @param groupBy 当前操作的 {@code groupBy} 输入
         * @param label 展示标题
         * @param scale 当前操作的 {@code scale} 输入
         */
        public Summary {
            groupBy = List.copyOf(groupBy == null ? List.of() : groupBy);
        }
    }
}
