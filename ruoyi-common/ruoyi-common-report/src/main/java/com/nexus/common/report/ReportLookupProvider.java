package com.nexus.common.report;

import java.util.*;

/** 按固定契约与参数提供受控业务候选，所有候选重新检查当前业务范围。 */
public interface ReportLookupProvider {
    /**
     * 列出固定契约已安装且有权限的选择器。
     *
     * @return 当前处理返回的固定结果
     */
    List<ReportLookupBinding> bindings();

    /**
     * 查询，负责当前模块对应的契约或处理职责。
     *
     * @param keyword 当前契约的keyword配置或状态
     * @param page 当前契约的page配置或状态
     * @param size 当前契约的size配置或状态
     */
    record Query(String keyword, int page, int size) {

        /**
         * 初始化查询并保存所需依赖或不可变值。
         *
         * @param keyword 当前操作的 {@code keyword} 输入
         * @param page 当前页
         * @param size 有界页大小
         */
        public Query {
            if (keyword == null) keyword = "";
            if (keyword.length() > 100 || page < 1 || page > 10000 || size < 1 || size > 50)
                throw new DatasetAccessException("INVALID_PARAMETER", "选择器查询范围无效");
        }
    }

    /**
     * Option，负责当前模块对应的契约或处理职责。
     *
     * @param value 当前契约的value配置或状态
     * @param label 用户可见标签
     */
    record Option(String value, String label) {}

    /**
     * 结果，负责当前模块对应的契约或处理职责。
     *
     * @param options 当前契约的options配置或状态
     * @param total 当前契约的total配置或状态
     */
    record Result(List<Option> options, long total) {

        /**
         * 初始化结果并保存所需依赖或不可变值。
         *
         * @param options 当前操作的 {@code options} 输入
         * @param total 当前操作的 {@code total} 输入
         */
        public Result {
            options = List.copyOf(options);
        }
    }

    /**
     * 执行受控只读查询。
     *
     * @param binding 当前操作的 {@code binding} 输入
     * @param context 服务端执行上下文
     * @param query 已声明字段和筛选参数
     * @return 当前处理返回的固定结果
     */
    Result query(ReportLookupBinding binding, ReportExecutionContext context, Query query);
}
