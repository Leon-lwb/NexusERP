package com.nexus.report.version;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportDsl;
import com.nexus.report.engine.*;

import java.nio.file.Path;
import java.util.*;

/** 将既有一版实现封装在原不可变引擎标识下，保留历史执行语义。 */
public class V1ReportEngineExecutor implements ReportEngineExecutor {
    /** 报表编译器。 */
    private final ReportCompiler compiler;

    /**
     * 初始化一版报表引擎执行器并保存所需依赖或不可变值。
     *
     * @param compiler 报表编译器
     */
    public V1ReportEngineExecutor(ReportCompiler compiler) {
        this.compiler = compiler;
    }

    /**
     * 读取执行器支持的定义格式版本。
     *
     * @return 当前处理返回的固定结果
     */
    public int schemaVersion() {
        return 1;
    }

    /**
     * 读取不可变执行器版本标识。
     *
     * @return 当前处理返回的固定结果
     */
    public String engineVersion() {
        return ReportCompiler.ENGINE_VERSION;
    }

    /**
     * 列出已安装执行器的格式与能力。
     *
     * @return 当前处理返回的固定结果
     */
    public Set<String> capabilities() {
        return Set.of(
                "TABLE",
                "DOCUMENT",
                "PDF",
                "XLSX_DATA",
                "XLSX_LAYOUT",
                "FIXED_BANDS",
                "EXACT_VALUES");
    }

    /**
     * 解析并校验受支持的定义结构。
     *
     * @param json 待处理定义文本
     * @return 当前处理返回的固定结果
     */
    public ReportDsl parse(String json) {
        return compiler.parse(json);
    }

    /**
     * 编译定义并检查字段、类型和业务权限。
     *
     * @param dsl 报表定义
     * @param descriptor 可信数据集契约
     * @param actor 当前服务端身份
     * @return 当前处理返回的固定结果
     */
    public CompiledReport compile(ReportDsl dsl, DatasetDescriptor descriptor, ReportActor actor) {
        return compiler.compile(dsl, descriptor, actor);
    }

    /**
     * 基于精确值计算列与汇总。
     *
     * @param report 已编译报表
     * @param rows 待处理业务行
     * @param parameters 按契约解析的输入参数
     * @return 当前处理返回的固定结果
     */
    public ReportCalculator.CalculatedData calculate(
            CompiledReport report, List<Map<String, Object>> rows, Map<String, Object> parameters) {
        return new ReportCalculator().calculate(report, rows, parameters);
    }

    /**
     * 计算单行精确展示值。
     *
     * @param report 已编译报表
     * @param row 当前精确业务行
     * @param parameters 按契约解析的输入参数
     * @return 当前处理返回的固定结果
     */
    public Map<String, TypedValue> calculateRow(
            CompiledReport report, Map<String, Object> row, Map<String, Object> parameters) {
        return new ReportCalculator().calculateRow(report, row, parameters);
    }

    /**
     * 创建独立执行的精确汇总会话。
     *
     * @param report 已编译报表
     * @return 当前处理返回的固定结果
     */
    public SummarySession summaries(CompiledReport report) {
        var delegate = new ReportCalculator.SummaryAccumulator(report);
        return new SummarySession() {
            /**
             * 将当前精确行加入本次计算会话。
             *
             * @param row 当前精确业务行
             */
            public void accept(Map<String, TypedValue> row) {
                delegate.accept(row);
            }

            /**
             * 关闭计算会话并返回固定结果。
             *
             * @return 当前处理返回的固定结果
             */
            public List<ReportCalculator.SummaryValue> finish() {
                return delegate.finish();
            }
        };
    }

    /**
     * 使用固定字体计算页面坐标和断行。
     *
     * @param report 已编译报表
     * @param data 精确数据结果
     * @param parameters 按契约解析的输入参数
     * @param font 可信私有字体路径
     * @return 当前处理返回的固定结果
     */
    public RenderPlan layout(
            CompiledReport report,
            ReportCalculator.CalculatedData data,
            Map<String, Object> parameters,
            Path font) {
        return new DocumentLayoutEngine().layout(report, data, parameters, font);
    }
}
