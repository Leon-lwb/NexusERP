package com.nexus.report.engine;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexus.common.report.TypedValue;
import com.nexus.report.domain.ReportDsl;

import java.util.*;

/**
 * 坐标以左上角为原点，单位为点；所有输出格式消费同一份布局。
 *
 * @param schemaVersion 当前契约的schemaVersion配置或状态
 * @param rowCount 当前契约的rowCount配置或状态
 * @param fontName 当前契约的fontName配置或状态
 * @param pages 当前契约的pages配置或状态
 * @param columns 输出列契约
 * @param summaries 当前契约的summaries配置或状态
 * @param rasters 当前契约的rasters配置或状态
 */
public record RenderPlan(
        int schemaVersion,
        long rowCount,
        String fontName,
        List<Page> pages,
        List<ReportDsl.Column> columns,
        List<ReportCalculator.SummaryValue> summaries,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Raster> rasters) {
    /**
     * 保留第一版固定计划构造方式。
     *
     * @param schemaVersion 当前契约的schemaVersion配置或状态
     * @param rowCount 当前契约的rowCount配置或状态
     * @param fontName 当前契约的fontName配置或状态
     * @param pages 当前契约的pages配置或状态
     * @param columns 输出列契约
     * @param summaries 当前契约的summaries配置或状态
     */
    public RenderPlan(
            int schemaVersion,
            long rowCount,
            String fontName,
            List<Page> pages,
            List<ReportDsl.Column> columns,
            List<ReportCalculator.SummaryValue> summaries) {
        this(schemaVersion, rowCount, fontName, pages, columns, summaries, Map.of());
    }

    /**
     * 初始化渲染计划并保存所需依赖或不可变值。
     *
     * @param schemaVersion 当前操作的 {@code schemaVersion} 输入
     * @param rowCount 当前操作的 {@code rowCount} 输入
     * @param fontName 当前操作的 {@code fontName} 输入
     * @param pages 当前操作的 {@code pages} 输入
     * @param columns 当前操作的 {@code columns} 输入
     * @param summaries 当前操作的 {@code summaries} 输入
     * @param rasters 当前契约的rasters配置或状态
     */
    public RenderPlan {
        pages = List.copyOf(pages);
        columns = List.copyOf(columns);
        summaries = List.copyOf(summaries);
        rasters = Map.copyOf(rasters == null ? Map.of() : rasters);
    }

    /**
     * Page，负责当前模块对应的契约或处理职责。
     *
     * @param number 当前契约的number配置或状态
     * @param width 布局宽度
     * @param height 布局高度
     * @param cells 固定坐标的单元格列表
     * @param media 当前契约的media配置或状态
     */
    public record Page(
            int number,
            double width,
            double height,
            List<Cell> cells,
            @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Media> media) {
        /**
         * 初始化Page并保存所需依赖或不可变值。
         *
         * @param number 当前序号
         * @param width 当前操作的 {@code width} 输入
         * @param height 当前操作的 {@code height} 输入
         * @param cells 当前操作的 {@code cells} 输入
         */
        /**
         * 保留第一版页面坐标构造方式。
         *
         * @param number 当前契约的number配置或状态
         * @param width 布局宽度
         * @param height 布局高度
         * @param cells 固定坐标的单元格列表
         */
        public Page(int number, double width, double height, List<Cell> cells) {
            this(number, width, height, cells, List.of());
        }

        /**
         * 初始化当前契约或受控处理依赖。
         *
         * @param number 当前受控操作的number输入
         * @param width 当前受控操作的width输入
         * @param height 当前受控操作的height输入
         * @param cells 当前受控操作的cells输入
         * @param media 当前受控操作的media输入
         */
        public Page {
            cells = List.copyOf(cells);
            media = List.copyOf(media == null ? List.of() : media);
        }
    }

    /**
     * 归一化PNG与checksum，固定计划自身封装资源。
     *
     * @param png 当前契约的png配置或状态
     * @param checksum 实际内容的SHA-256校验和
     */
    public record Raster(String png, String checksum) {}

    /**
     * 页面媒体只引用计划内资源，不包含远程URL。
     *
     * @param resource 当前契约的resource配置或状态
     * @param x 当前契约的x配置或状态
     * @param y 当前契约的y配置或状态
     * @param width 布局宽度
     * @param height 布局高度
     */
    public record Media(String resource, double x, double y, double width, double height) {}

    /**
     * Cell，负责当前模块对应的契约或处理职责。
     *
     * @param band 当前契约的band配置或状态
     * @param rowIndex 当前契约的rowIndex配置或状态
     * @param column 当前契约的column配置或状态
     * @param colSpan 当前契约的colSpan配置或状态
     * @param rowSpan 当前契约的rowSpan配置或状态
     * @param x 当前契约的x配置或状态
     * @param y 当前契约的y配置或状态
     * @param width 布局宽度
     * @param height 布局高度
     * @param lines 当前契约的lines配置或状态
     * @param style 当前契约的style配置或状态
     * @param value 当前契约的value配置或状态
     * @param scale 输出小数位数
     */
    public record Cell(
            String band,
            int rowIndex,
            int column,
            int colSpan,
            int rowSpan,
            double x,
            double y,
            double width,
            double height,
            List<String> lines,
            ReportDsl.Style style,
            TypedValue value,
            int scale) {
        /**
         * 初始化Cell并保存所需依赖或不可变值。
         *
         * @param band 当前操作的 {@code band} 输入
         * @param rowIndex 当前操作的 {@code rowIndex} 输入
         * @param column 当前操作的 {@code column} 输入
         * @param colSpan 当前操作的 {@code colSpan} 输入
         * @param rowSpan 当前操作的 {@code rowSpan} 输入
         * @param x 当前操作的 {@code x} 输入
         * @param y 当前操作的 {@code y} 输入
         * @param width 当前操作的 {@code width} 输入
         * @param height 当前操作的 {@code height} 输入
         * @param lines 当前操作的 {@code lines} 输入
         * @param style 当前操作的 {@code style} 输入
         * @param value 当前操作的 {@code value} 输入
         * @param scale 当前操作的 {@code scale} 输入
         */
        public Cell {
            lines = List.copyOf(lines);
        }
    }
}
