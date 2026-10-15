package com.nexus.report.query;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.domain.ReportRecords.DatasetVersion;
import com.nexus.report.engine.ReportCompiler;
import com.nexus.report.extension.ReportResourceStore;

import java.util.*;

/** 解析查询数据集的固定版本，任何依赖停用、撤权或变化都拒绝后续交付。 */
public final class QueryDatasetResolver {
    /**
     * 发布时冻结的目录完整性和输出契约。
     *
     * @param catalogVersion 当前契约的catalogVersion配置或状态
     * @param catalogChecksum 当前契约的catalogChecksum配置或状态
     * @param descriptor 当前契约的descriptor配置或状态
     * @param plannerVersion 当前契约的plannerVersion配置或状态
     */
    public record Contract(
            int catalogVersion,
            String catalogChecksum,
            DatasetDescriptor descriptor,
            String plannerVersion) {
        /**
         * 创建固定依赖时显式绑定当前规划语义。
         *
         * @param catalogVersion 当前契约的catalogVersion配置或状态
         * @param catalogChecksum 当前契约的catalogChecksum配置或状态
         * @param descriptor 当前契约的descriptor配置或状态
         */
        public Contract(int catalogVersion, String catalogChecksum, DatasetDescriptor descriptor) {
            this(catalogVersion, catalogChecksum, descriptor, QueryPlanner.VERSION);
        }
    }

    /** 保存当前bindings状态或配置。 */
    private final QueryDatasetBindingStore bindings;

    /** 保存当前resources状态或配置。 */
    private final ReportResourceStore resources;

    /** 保存当前catalogs状态或配置。 */
    private final ReportCatalogRegistry catalogs;

    /** 保存当前executor状态或配置。 */
    private final CatalogQueryExecutor executor;

    /** 保存当前json状态或配置。 */
    private final ReportCompiler json;

    /**
     * 注入系统元数据、固定目录及独立只读执行器。
     *
     * @param bindings 当前契约的bindings配置或状态
     * @param resources 当前契约的resources配置或状态
     * @param catalogs 当前契约的catalogs配置或状态
     * @param executor 当前契约的executor配置或状态
     * @param json 固定版本的契约正文
     */
    public QueryDatasetResolver(
            QueryDatasetBindingStore bindings,
            ReportResourceStore resources,
            ReportCatalogRegistry catalogs,
            CatalogQueryExecutor executor,
            ReportCompiler json) {
        this.bindings = bindings;
        this.resources = resources;
        this.catalogs = catalogs;
        this.executor = executor;
        this.json = json;
    }

    /**
     * 精确读取固定查询，不使用当前发布指针替代旧发布。
     *
     * @param actor 执行用户
     * @param dataset 当前契约的dataset配置或状态
     * @return 当前受控处理的固定结果
     */
    public ReportDatasetProvider resolve(ReportActor actor, DatasetVersion dataset) {
        var link =
                bindings.read(actor.tenantId(), dataset.id())
                        .orElseThrow(() -> new ReportFault("VERSION_INVALID", "查询数据集关联缺失", 409));
        var version =
                resources
                        .version("QUERY", actor.tenantId(), link.queryVersion())
                        .orElseThrow(() -> new ReportFault("VERSION_UNSUPPORTED", "查询发布版本缺失", 409));
        var source = resources.access("QUERY", actor, version.resourceId(), "VIEW");
        if (!source.status().equals("PUBLISHED"))
            throw new ReportFault("QUERY_OFFLINE", "查询资源已停用", 409);
        var contract = json.read(version.dependency(), Contract.class);
        var model = json.read(version.json(), QueryModel.class);
        if (!QueryPlanner.VERSION.equals(contract.plannerVersion()))
            throw new ReportFault("VERSION_UNSUPPORTED", "查询规划语义未安装或已变化，请重新发布查询", 409);
        if (!version.checksum().equals(link.queryChecksum())
                || contract.catalogVersion() != link.catalogVersion()
                || !contract.catalogChecksum().equals(link.catalogChecksum())
                || !catalogs.checksum(contract.catalogVersion()).equals(contract.catalogChecksum())
                || !dataset.provider().equals(contract.descriptor().code())
                || dataset.providerVersion() != contract.descriptor().version())
            throw new ReportFault("VERSION_INVALID", "查询或目录依赖不匹配", 409);
        return new CatalogQueryDatasetProvider(
                new QueryPlanner(catalogs.require(contract.catalogVersion())),
                model,
                contract.descriptor(),
                executor,
                version.id()
                        + ":"
                        + version.checksum()
                        + ":"
                        + contract.catalogChecksum()
                        + ":planner:"
                        + contract.plannerVersion(),
                actor);
    }
}
