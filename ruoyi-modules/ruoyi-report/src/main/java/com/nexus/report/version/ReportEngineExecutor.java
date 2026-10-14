package com.nexus.report.version;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportDsl;
import com.nexus.report.engine.*;

import java.nio.file.Path;
import java.util.*;

/** 代码注册的执行契约，已发布引擎标识不随新的默认版本变化。 */
public interface ReportEngineExecutor {
    /**
     * 读取执行器支持的定义格式版本。
     *
     * @return 当前处理返回的固定结果
     */
    int schemaVersion();

    /**
     * 读取不可变执行器版本标识。
     *
     * @return 当前处理返回的固定结果
     */
    String engineVersion();

    /**
     * 列出已安装执行器的格式与能力。
     *
     * @return 当前处理返回的固定结果
     */
    Set<String> capabilities();

    /**
     * 解析并校验受支持的定义结构。
     *
     * @param json 待处理定义文本
     * @return 当前处理返回的固定结果
     */
    ReportDsl parse(String json);

    /**
     * 编译定义并检查字段、类型和业务权限。
     *
     * @param dsl 报表定义
     * @param descriptor 可信数据集契约
     * @param actor 当前服务端身份
     * @return 当前处理返回的固定结果
     */
    CompiledReport compile(ReportDsl dsl, DatasetDescriptor descriptor, ReportActor actor);

    /**
     * 基于精确值计算列与汇总。
     *
     * @param report 已编译报表
     * @param rows 待处理业务行
     * @param parameters 按契约解析的输入参数
     * @return 当前处理返回的固定结果
     */
    ReportCalculator.CalculatedData calculate(
            CompiledReport report, List<Map<String, Object>> rows, Map<String, Object> parameters);

    /**
     * 计算单行精确展示值。
     *
     * @param report 已编译报表
     * @param row 当前精确业务行
     * @param parameters 按契约解析的输入参数
     * @return 当前处理返回的固定结果
     */
    Map<String, TypedValue> calculateRow(
            CompiledReport report, Map<String, Object> row, Map<String, Object> parameters);

    /** SummarySession，负责当前模块对应的契约或处理职责。 */
    interface SummarySession {
        /**
         * 将当前精确行加入本次计算会话。
         *
         * @param row 当前精确业务行
         */
        void accept(Map<String, TypedValue> row);

        /**
         * 关闭计算会话并返回固定结果。
         *
         * @return 当前处理返回的固定结果
         */
        List<ReportCalculator.SummaryValue> finish();
    }

    /**
     * 创建独立执行的精确汇总会话。
     *
     * @param report 已编译报表
     * @return 当前处理返回的固定结果
     */
    SummarySession summaries(CompiledReport report);

    /**
     * 使用固定字体计算页面坐标和断行。
     *
     * @param report 已编译报表
     * @param data 精确数据结果
     * @param parameters 按契约解析的输入参数
     * @param font 可信私有字体路径
     * @return 当前处理返回的固定结果
     */
    RenderPlan layout(
            CompiledReport report,
            ReportCalculator.CalculatedData data,
            Map<String, Object> parameters,
            Path font);
}
