package com.nexus.common.report;

/**
 * 只有代码注册的数据集契约版本与参数组合可以提供业务选择器。
 *
 * @param datasetCode 当前契约的datasetCode配置或状态
 * @param providerVersion 当前契约的providerVersion配置或状态
 * @param parameter 当前契约的parameter配置或状态
 * @param label 用户可见标签
 * @param permission 当前契约的permission配置或状态
 */
public record ReportLookupBinding(
        String datasetCode,
        int providerVersion,
        String parameter,
        String label,
        String permission) {}
