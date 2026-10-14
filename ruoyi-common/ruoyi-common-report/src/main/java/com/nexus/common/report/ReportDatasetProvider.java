package com.nexus.common.report;

import java.util.*;
import java.util.function.Consumer;

/** 只读数据提供者契约：业务数据由提供者负责，展示和计算由报表引擎负责。 */
public interface ReportDatasetProvider {
    /**
     * 读取固定代码契约描述。
     *
     * @return 当前处理返回的固定结果
     */
    DatasetDescriptor descriptor();

    /**
     * 执行受控只读查询。
     *
     * @param context 服务端执行上下文
     * @param query 已声明字段和筛选参数
     * @return 当前处理返回的固定结果
     */
    DatasetResult query(ReportExecutionContext context, DatasetQuery query);

    /**
     * 在取消与容量控制下逐行读取。
     *
     * @param context 服务端执行上下文
     * @param query 已声明字段和筛选参数
     * @param consumer 当前操作的 {@code consumer} 输入
     */
    default void stream(
            ReportExecutionContext context,
            DatasetQuery query,
            Consumer<Map<String, Object>> consumer) {
        DatasetResult result =
                query(
                        context,
                        new DatasetQuery(
                                query.parameters(),
                                query.fields(),
                                1,
                                5001,
                                true,
                                query.sortField(),
                                query.descending()));
        if (result.total() > 5000)
            throw new DatasetAccessException("CAPACITY_EXCEEDED", "该扩展数据集未实现大规模流式读取");
        result.rows()
                .forEach(
                        row -> {
                            context.cancellation().check();
                            consumer.accept(row);
                        });
    }

    /**
     * 查询数据集的额外固定身份；代码数据集保持旧指纹兼容。
     *
     * @return 当前受控处理的固定结果
     */
    default String executionIdentity() {
        return "";
    }

    /**
     * 查询涉及的全部实体与隐藏字段权限，纳入运行与交付范围指纹。
     *
     * @return 当前受控处理的固定结果
     */
    default Set<String> requiredPermissions() {
        return Set.of();
    }
}
