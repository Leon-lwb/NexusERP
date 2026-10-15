package com.nexus.report.dataset;

import com.nexus.common.report.*;
import com.nexus.report.config.ReportProviderRegistry;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.domain.ReportRecords.DatasetVersion;
import com.nexus.report.query.QueryDatasetResolver;

/** 统一解析代码提供者与目录查询数据集，始终使用调用方绑定的固定版本。 */
public final class ReportDatasetResolver {
    /** 保存当前providers状态或配置。 */
    private final ReportProviderRegistry providers;

    /** 保存当前queries状态或配置。 */
    private final QueryDatasetResolver queries;

    /**
     * 兼容原有代码数据集和隔离测试。
     *
     * @param providers 当前契约的providers配置或状态
     */
    public ReportDatasetResolver(ReportProviderRegistry providers) {
        this(providers, null);
    }

    /**
     * 同时安装固定查询解析器，未配置时拒绝查询数据集。
     *
     * @param providers 当前契约的providers配置或状态
     * @param queries 当前契约的queries配置或状态
     */
    public ReportDatasetResolver(ReportProviderRegistry providers, QueryDatasetResolver queries) {
        this.providers = providers;
        this.queries = queries;
    }

    /**
     * 服务端身份和元数据关联共同限定数据集解析。
     *
     * @param actor 执行用户
     * @param version 当前契约的version配置或状态
     * @return 当前受控处理的固定结果
     */
    public ReportDatasetProvider resolve(ReportActor actor, DatasetVersion version) {
        if (version.provider().startsWith("CQ_")) {
            if (queries == null) throw new ReportFault("QUERY_SOURCE_DISABLED", "查询数据集解析器未安装", 409);
            return queries.resolve(actor, version);
        }
        return providers.get(version.provider(), version.providerVersion());
    }
}
