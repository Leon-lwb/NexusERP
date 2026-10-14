package com.nexus.common.report;

/**
 * 默认值由代码契约声明，仅允许固定字面量和受限日期标记 TODAY、MONTHS_AGO_6。
 *
 * @param name 契约名称
 * @param label 用户可见标签
 * @param type 受控类型
 * @param required 是否必须提供该参数
 * @param defaultValue 固定默认值或白名单动态日期规则
 */
public record DatasetParameter(
        String name, String label, ValueType type, boolean required, String defaultValue) {

    /**
     * 初始化数据集参数并保存所需依赖或不可变值。
     *
     * @param name 当前操作的 {@code name} 输入
     * @param label 展示标题
     * @param type 当前操作的 {@code type} 输入
     * @param required 当前操作的 {@code required} 输入
     */
    public DatasetParameter(String name, String label, ValueType type, boolean required) {
        this(name, label, type, required, null);
    }
}
