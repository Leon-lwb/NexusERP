package com.nexus.report.config;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportFault;

import java.util.*;

/** 允许多个契约版本共存，数据集编码和版本重复时拒绝启动。 */
public final class ReportProviderRegistry {
    /** 保存当前 {@code providers} 值。 */
    private final Map<String, NavigableMap<Integer, ReportDatasetProvider>> providers =
            new TreeMap<>();

    /**
     * 初始化报表提供者注册表并保存所需依赖或不可变值。
     *
     * @param list 当前操作的 {@code list} 输入
     */
    public ReportProviderRegistry(Collection<ReportDatasetProvider> list) {
        for (var provider : list) {
            var d = Objects.requireNonNull(provider.descriptor(), "Dataset descriptor");
            if (d.code() == null
                    || d.code().startsWith("CQ_")
                    || !d.code().matches("[A-Z][A-Z0-9_]{0,63}")
                    || d.version() < 1
                    || d.permission() == null
                    || d.permission().isBlank()
                    || d.fields().isEmpty()
                    || d.fields().size() > 60)
                throw new IllegalStateException("Invalid report dataset descriptor");
            Set<String> fields = new HashSet<>();
            for (var field : d.fields())
                if (!fields.add(field.name()) || field.type() == null)
                    throw new IllegalStateException("Duplicate or untyped report dataset field");
            if (!fields.containsAll(d.defaultColumns()))
                throw new IllegalStateException("Default report columns absent");
            if (providers
                            .computeIfAbsent(d.code(), key -> new TreeMap<>())
                            .putIfAbsent(d.version(), provider)
                    != null)
                throw new IllegalStateException(
                        "Duplicate report provider: " + d.code() + " v" + d.version());
        }
    }

    /**
     * 读取配置。
     *
     * @param code 业务编码
     * @param version 固定版本
     * @return 当前处理返回的固定结果
     */
    public ReportDatasetProvider get(String code, int version) {
        var versions = providers.get(code);
        var provider = versions == null ? null : versions.get(version);
        if (provider == null) throw new ReportFault("VERSION_UNSUPPORTED", "所需数据集契约版本未安装");
        return provider;
    }

    /**
     * 读取代码目录中最新契约。
     *
     * @param code 业务编码
     * @return 当前处理返回的固定结果
     */
    public ReportDatasetProvider latest(String code) {
        var versions = providers.get(code);
        if (versions == null) throw new ReportFault("DATASET_UNAVAILABLE", "数据集未安装", 404);
        return versions.lastEntry().getValue();
    }

    /**
     * 读取代码目录中最新契约。
     *
     * @return 当前处理返回的固定结果
     */
    public List<ReportDatasetProvider> latest() {
        return providers.values().stream()
                .map(versions -> versions.lastEntry().getValue())
                .toList();
    }
}
