package com.nexus.report.dataset;

import com.nexus.common.report.*;

import java.util.*;
import java.util.function.Consumer;

/** 为授权业务值附加字典名称，保留原值、提供者身份和流式快照边界。 */
public final class ReportDictionaryDatasetProvider implements ReportDatasetProvider {
    private final ReportDatasetProvider delegate;
    private final ReportDictionaryLabels labels;
    private final Map<String, String> bindings;

    /**
     * 安装固定绑定，拒绝未知字段、非字符串字段及任意物理标识符。
     *
     * @param delegate 原始业务提供者
     * @param labels 系统字典名称读取入口
     */
    public ReportDictionaryDatasetProvider(ReportDatasetProvider delegate, ReportDictionaryLabels labels) {
        this.delegate = delegate;
        this.labels = labels;
        this.bindings = Map.copyOf(delegate.displayDictionaries());
        for (var binding : bindings.entrySet()) {
            var field = delegate.descriptor().fields().stream()
                    .filter(candidate -> candidate.name().equals(binding.getKey())).findFirst();
            if (field.isEmpty() || field.get().type() != ValueType.STRING
                    || !binding.getValue().matches("[A-Za-z][A-Za-z0-9_]{0,99}"))
                throw new IllegalStateException("Invalid report dictionary binding");
        }
    }

    /** @return 不变的数据集字段与版本契约 */
    @Override
    public DatasetDescriptor descriptor() {
        return delegate.descriptor();
    }

    /** @return 原提供者的固定执行身份 */
    @Override
    public String executionIdentity() {
        return delegate.executionIdentity();
    }

    /** @return 原提供者涉及的全部实体权限 */
    @Override
    public Set<String> requiredPermissions() {
        return delegate.requiredPermissions();
    }

    /** @return 代码固定的字段字典绑定 */
    @Override
    public Map<String, String> displayDictionaries() {
        return bindings;
    }

    /**
     * 在原查询完成权限验证后附加本次固定名称。
     *
     * @param context 执行身份及取消信号
     * @param query 原有精确参数与授权字段
     * @return 保留原始行数和数据时点的结果
     */
    @Override
    public DatasetResult query(ReportExecutionContext context, DatasetQuery query) {
        context.cancellation().check();
        var result = delegate.query(context, query);
        var session = new Session(context, query.fields());
        return new DatasetResult(result.rows().stream().map(session::apply).toList(), result.total(), result.dataTime());
    }

    /**
     * 首个授权业务行到达后读取一次字典，后续行复用不可变名称。
     *
     * @param context 执行身份及取消信号
     * @param query 原有精确参数与授权字段
     * @param consumer 原有流式消费者
     */
    @Override
    public void stream(ReportExecutionContext context, DatasetQuery query, Consumer<Map<String, Object>> consumer) {
        context.cancellation().check();
        var session = new Session(context, query.fields());
        delegate.stream(context, query, row -> consumer.accept(session.apply(row)));
    }

    /** 一次查询独享的名称快照，不在用户、租户或运行间共享可变状态。 */
    private final class Session {
        private final ReportExecutionContext context;
        private final Set<String> fields;
        private Map<String, Map<String, String>> frozen;

        /**
         * 保存本次执行上下文，不提前读取尚未授权的字典。
         *
         * @param context 当前执行上下文
         * @param fields 请求投影
         */
        private Session(ReportExecutionContext context, Set<String> fields) {
            this.context = context;
            this.fields = Set.copyOf(fields);
        }

        /**
         * 复制业务行并仅装饰请求包含的字典字段。
         *
         * @param row 原始授权业务行
         * @return 原值与显示值分离的结果
         */
        private Map<String, Object> apply(Map<String, Object> row) {
            context.cancellation().check();
            if (frozen == null) {
                var types = new LinkedHashSet<String>();
                bindings.forEach((field, type) -> {
                    if (fields.contains(field)) types.add(type);
                });
                Map<String, Map<String, String>> copy = new LinkedHashMap<>();
                if (!types.isEmpty()) labels.load(context.actor(), types)
                        .forEach((type, values) -> copy.put(type, Map.copyOf(values)));
                frozen = Map.copyOf(copy);
                context.cancellation().check();
            }
            Map<String, Object> result = new LinkedHashMap<>(row);
            bindings.forEach((field, type) -> {
                if (!fields.contains(field) || !row.containsKey(field) || row.get(field) == null) return;
                var value = TypedValue.of(ValueType.STRING, row.get(field));
                if (value.value() == null) return;
                var label = frozen.getOrDefault(type, Map.of()).get(value.value());
                if (label != null && !label.isBlank())
                    result.put(field, new TypedValue(ValueType.STRING, value.value(), label));
            });
            return result;
        }
    }
}
