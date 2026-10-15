package com.nexus.report.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.common.report.catalog.ReportEntityCatalogProvider;
import com.nexus.report.dataset.ReportDatasetResolver;
import com.nexus.report.engine.ReportCompiler;
import com.nexus.report.export.*;
import com.nexus.report.extension.ReportResourceStore;
import com.nexus.report.mapper.JdbcReportStore;
import com.nexus.report.query.*;
import com.nexus.report.runtime.*;
import com.nexus.report.service.ReportDatasetVersionService;
import com.nexus.report.service.ReportDefinitionService;
import com.nexus.report.service.ReportLookupService;
import com.nexus.report.service.ReportTemplatePackageService;
import com.nexus.report.storage.*;
import com.nexus.report.version.*;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Path;
import java.util.List;

/** 装配报表执行器、契约、授权、私有存储、运行与后台导出服务。 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({
    ReportProperties.class,
    ReportQueryProperties.class,
    ReportStorageProperties.class,
    com.nexus.report.subscription.ReportSubscriptionProperties.class
})
public class ReportConfiguration {
    /**
     * 装配运行指标，复用统一权限和存储契约。
     *
     * @param registries 当前操作的 {@code registries} 输入
     * @return 当前处理返回的固定结果
     */
    @Bean
    public ReportRuntimeMetrics reportRuntimeMetrics(
            org.springframework.beans.factory.ObjectProvider<
                            io.micrometer.core.instrument.MeterRegistry>
                    registries) {
        return new ReportRuntimeMetrics(
                registries.getIfAvailable(
                        io.micrometer.core.instrument.simple.SimpleMeterRegistry::new));
    }

    /**
     * 装配提供者注册表，复用统一权限和存储契约。
     *
     * @param providers 当前操作的 {@code providers} 输入
     * @return 当前处理返回的固定结果
     */
    @Bean
    public ReportProviderRegistry reportProviderRegistry(List<ReportDatasetProvider> providers) {
        return new ReportProviderRegistry(providers);
    }

    /**
     * 装配Compiler，复用统一权限和存储契约。
     *
     * @param json 待处理定义文本
     * @return 当前处理返回的固定结果
     */
    @Bean
    public ReportCompiler reportCompiler(ObjectMapper json) {
        return new ReportCompiler(json);
    }

    /**
     * 装配一版执行器，复用统一权限和存储契约。
     *
     * @param compiler 报表编译器
     * @return 当前处理返回的固定结果
     */
    @Bean
    public ReportEngineExecutor reportV1Executor(ReportCompiler compiler) {
        return new V1ReportEngineExecutor(compiler);
    }

    /**
     * 装配引擎注册表，复用统一权限和存储契约。
     *
     * @param executors 当前操作的 {@code executors} 输入
     * @param properties 当前操作的 {@code properties} 输入
     * @return 当前处理返回的固定结果
     */
    /**
     * 安装独立的第二版执行器。
     *
     * @param compiler 当前契约的compiler配置或状态
     * @param assets 当前契约的assets配置或状态
     * @return 当前受控处理的固定结果
     */
    @Bean
    public ReportEngineExecutor reportV2Executor(
            ReportCompiler compiler, com.nexus.report.asset.ReportAssetService assets) {
        return new V2ReportEngineExecutor(compiler, assets);
    }

    /**
     * 执行reportEngineRegistry对应的受控处理。
     *
     * @param executors 当前受控操作的executors输入
     * @param properties 当前受控操作的properties输入
     * @return 当前固定契约或处理结果
     */
    @Bean
    public ReportEngineRegistry reportEngineRegistry(
            List<ReportEngineExecutor> executors, ReportProperties properties) {
        return new ReportEngineRegistry(
                executors,
                java.util.Map.of(1, ReportCompiler.ENGINE_VERSION, 2, "2.0.0"),
                properties.getDisabledEngines());
    }

    /**
     * 装配选择器服务，复用统一权限和存储契约。
     *
     * @param versions 当前操作的 {@code versions} 输入
     * @param definitions 当前操作的 {@code definitions} 输入
     * @param providers 当前操作的 {@code providers} 输入
     * @return 当前处理返回的固定结果
     */
    @Bean
    public ReportLookupService reportLookupService(
            ReportDatasetVersionService versions,
            ReportDefinitionService definitions,
            List<ReportLookupProvider> providers) {
        return new ReportLookupService(versions, definitions, providers);
    }

    /**
     * 装配模板模板包服务，复用统一权限和存储契约。
     *
     * @param definitions 当前操作的 {@code definitions} 输入
     * @param compiler 报表编译器
     * @return 当前处理返回的固定结果
     */
    @Bean
    public ReportTemplatePackageService reportTemplatePackageService(
            ReportDefinitionService definitions, ReportCompiler compiler) {
        return new ReportTemplatePackageService(definitions, compiler);
    }

    /**
     * 装配数据集版本服务，复用统一权限和存储契约。
     *
     * @param store 租户化元数据或私有存储
     * @param registry 代码契约注册表
     * @param definitions 当前操作的 {@code definitions} 输入
     * @param compiler 报表编译器
     * @return 当前处理返回的固定结果
     */
    @Bean
    public ReportDatasetVersionService reportDatasetVersionService(
            JdbcReportStore store,
            ReportProviderRegistry registry,
            ReportDefinitionService definitions,
            ReportCompiler compiler) {
        return new ReportDatasetVersionService(store, registry, definitions, compiler);
    }

    /**
     * 装配产物存储，复用统一权限和存储契约。
     *
     * @param properties 当前操作的 {@code properties} 输入
     * @return 当前处理返回的固定结果
     * @param storage 当前契约的storage配置或状态
     * @param jdbc 当前契约的jdbc配置或状态
     */
    @Bean(destroyMethod = "close")
    public ReportArtifactStore reportArtifactStore(
            ReportProperties properties, ReportStorageProperties storage, JdbcTemplate jdbc) {
        ReportArtifactStore base;
        if (storage.getBackend().equals("S3")) {
            if (!storage.isManifestEnabled())
                throw new IllegalArgumentException("共享存储必须启用产物清单和硬配额");
            base =
                    new S3ReportArtifactStore(
                            storage,
                            Path.of(properties.getArtifactRoot(), "staging"),
                            properties.getMaxArtifactBytes());
        } else if (storage.getBackend().equals("LOCAL"))
            base =
                    new LocalReportArtifactStore(
                            Path.of(properties.getArtifactRoot()),
                            properties.getMaxArtifactBytes());
        else throw new IllegalArgumentException("报表存储类型未安装");
        if (!storage.isManifestEnabled()) return base;
        var quota =
                new ReportQuotaService(
                        jdbc,
                        storage.getGlobalBytes(),
                        storage.getTenantBytes(),
                        storage.getGlobalObjects(),
                        storage.getTenantObjects());
        return new QuotaReportArtifactStore(
                base,
                quota,
                properties.getMaxArtifactBytes(),
                "node_"
                        + com.nexus.report.service.ReportDefinitionService.hash(
                                        System.getenv()
                                                        .getOrDefault(
                                                                "COMPUTERNAME",
                                                                System.getenv()
                                                                        .getOrDefault(
                                                                                "HOSTNAME",
                                                                                "local"))
                                                + "|"
                                                + Path.of(properties.getArtifactRoot())
                                                        .toAbsolutePath()
                                                        .normalize())
                                .substring(0, 24));
    }

    /**
     * 装配快照存储，复用统一权限和存储契约。
     *
     * @param artifacts 当前操作的 {@code artifacts} 输入
     * @param json 待处理定义文本
     * @return 当前处理返回的固定结果
     * @param jdbc 当前契约的jdbc配置或状态
     * @param properties 当前契约的properties配置或状态
     */
    @Bean
    public ReportSnapshotStore reportSnapshotStore(
            ReportArtifactStore artifacts,
            ObjectMapper json,
            JdbcTemplate jdbc,
            ReportStorageProperties properties) {
        var snapshots = new ReportSnapshotStore(artifacts, json);
        if (properties.isManifestEnabled())
            snapshots.indexes(new ReportSnapshotIndexService(jdbc, artifacts, json));
        return snapshots;
    }

    /**
     * 装配定义服务，复用统一权限和存储契约。
     *
     * @param store 租户化元数据或私有存储
     * @param registry 代码契约注册表
     * @param authorization 当前授权解析接口
     * @param compiler 报表编译器
     * @param engines 当前操作的 {@code engines} 输入
     * @param metrics 当前操作的 {@code metrics} 输入
     * @return 当前处理返回的固定结果
     * @param datasets 当前契约的datasets配置或状态
     */
    @Bean
    public ReportDefinitionService reportDefinitionService(
            JdbcReportStore store,
            ReportProviderRegistry registry,
            ReportAuthorizationPort authorization,
            ReportCompiler compiler,
            ReportEngineRegistry engines,
            ReportRuntimeMetrics metrics,
            ReportDatasetResolver datasets) {
        return new ReportDefinitionService(
                store, registry, authorization, compiler, engines, metrics, datasets);
    }

    /**
     * 装配运行服务，复用统一权限和存储契约。
     *
     * @param store 租户化元数据或私有存储
     * @param definitions 当前操作的 {@code definitions} 输入
     * @param compiler 报表编译器
     * @param snapshots 当前操作的 {@code snapshots} 输入
     * @param artifacts 当前操作的 {@code artifacts} 输入
     * @param properties 当前操作的 {@code properties} 输入
     * @param json 待处理定义文本
     * @param metrics 当前操作的 {@code metrics} 输入
     * @return 当前处理返回的固定结果
     * @param storage 当前契约的storage配置或状态
     * @param jdbc 当前契约的jdbc配置或状态
     * @param resources 当前契约的resources配置或状态
     * @param authorization 当前契约的authorization配置或状态
     */
    @Bean
    public ReportRuntimeService reportRuntimeService(
            JdbcReportStore store,
            ReportDefinitionService definitions,
            ReportCompiler compiler,
            ReportSnapshotStore snapshots,
            ReportArtifactStore artifacts,
            ReportProperties properties,
            ObjectMapper json,
            ReportRuntimeMetrics metrics,
            ReportStorageProperties storage,
            JdbcTemplate jdbc,
            ReportResourceStore resources,
            ReportAuthorizationPort authorization) {
        if (storage.isManifestEnabled()) {
            store.admissionLimits(
                    storage.getQueuedPerUser(),
                    storage.getQueuedPerTenant(),
                    storage.getQueuedGlobal());
            store.fairDispatch(true, 8, 2, 1);
        }
        var runtime =
                new ReportRuntimeService(
                        store, definitions, compiler, snapshots, artifacts, properties, json);
        runtime.metrics(metrics);
        runtime.constraints(
                new com.nexus.report.subscription.ReportSubscriptionAccessGuard(
                                resources, jdbc, authorization, compiler, definitions)
                        ::check);
        if (storage.isManifestEnabled())
            runtime.fontResources(new ReportFontResourceStore(jdbc, artifacts));
        return runtime;
    }

    /**
     * 装配导出工作器，复用统一权限和存储契约。
     *
     * @param store 租户化元数据或私有存储
     * @param runtime 当前操作的 {@code runtime} 输入
     * @param artifacts 当前操作的 {@code artifacts} 输入
     * @param properties 当前操作的 {@code properties} 输入
     * @return 当前处理返回的固定结果
     */
    @Bean(destroyMethod = "close")
    public ReportExportWorker reportExportWorker(
            JdbcReportStore store,
            ReportRuntimeService runtime,
            ReportArtifactStore artifacts,
            ReportProperties properties) {
        return new ReportExportWorker(
                store,
                runtime,
                artifacts,
                properties,
                new ReportExportRenderer(runtime, properties));
    }

    /** 装配不复用业务写账户的独立只读查询池。 */
    /**
     * 持久订阅与接收人独立任务。
     *
     * @param resources 当前契约的resources配置或状态
     * @param jdbc 当前契约的jdbc配置或状态
     * @param store 逻辑存储标识
     * @param definitions 当前契约的definitions配置或状态
     * @param runtime 当前契约的runtime配置或状态
     * @param compiler 当前契约的compiler配置或状态
     * @param properties 当前契约的properties配置或状态
     * @return 当前受控处理的固定结果
     */
    @Bean
    public com.nexus.report.subscription.ReportSubscriptionService reportSubscriptionService(
            ReportResourceStore resources,
            JdbcTemplate jdbc,
            JdbcReportStore store,
            ReportDefinitionService definitions,
            ReportRuntimeService runtime,
            ReportCompiler compiler,
            com.nexus.report.subscription.ReportSubscriptionProperties properties) {
        return new com.nexus.report.subscription.ReportSubscriptionService(
                resources, jdbc, store, definitions, runtime, compiler, properties);
    }

    /**
     * 站内事件适配器不会在启动阶段发送通知。
     *
     * @param events 当前契约的events配置或状态
     * @return 当前受控处理的固定结果
     */
    @Bean
    public ReportDeliveryNotificationPort reportDeliveryNotificationPort(
            org.springframework.context.ApplicationEventPublisher events) {
        return new com.nexus.report.subscription.ReportDeliveryNotificationAdapter(events);
    }

    /**
     * 交付对账及领取复用正常导出鉴权。
     *
     * @param resources 当前契约的resources配置或状态
     * @param jdbc 当前契约的jdbc配置或状态
     * @param store 逻辑存储标识
     * @param definitions 当前契约的definitions配置或状态
     * @param runtime 当前契约的runtime配置或状态
     * @param compiler 当前契约的compiler配置或状态
     * @param properties 当前契约的properties配置或状态
     * @param notifications 当前契约的notifications配置或状态
     * @return 当前受控处理的固定结果
     */
    @Bean
    public com.nexus.report.subscription.ReportDeliveryService reportDeliveryService(
            ReportResourceStore resources,
            JdbcTemplate jdbc,
            JdbcReportStore store,
            ReportDefinitionService definitions,
            ReportRuntimeService runtime,
            ReportCompiler compiler,
            com.nexus.report.subscription.ReportSubscriptionProperties properties,
            ReportDeliveryNotificationPort notifications) {
        return new com.nexus.report.subscription.ReportDeliveryService(
                resources, jdbc, store, definitions, runtime, compiler, properties, notifications);
    }

    /**
     * 独立仪表盘发布及逐组件计算。
     *
     * @param resources 当前契约的resources配置或状态
     * @param jdbc 当前契约的jdbc配置或状态
     * @param store 逻辑存储标识
     * @param definitions 当前契约的definitions配置或状态
     * @param compiler 当前契约的compiler配置或状态
     * @return 当前受控处理的固定结果
     */
    @Bean
    public com.nexus.report.dashboard.ReportDashboardService reportDashboardService(
            ReportResourceStore resources,
            JdbcTemplate jdbc,
            JdbcReportStore store,
            ReportDefinitionService definitions,
            ReportCompiler compiler) {
        return new com.nexus.report.dashboard.ReportDashboardService(
                resources, jdbc, store, definitions, compiler);
    }

    /**
     * 批次复用固定版本运行和持久导出队列。
     *
     * @param resources 当前契约的resources配置或状态
     * @param jdbc 当前契约的jdbc配置或状态
     * @param store 逻辑存储标识
     * @param definitions 当前契约的definitions配置或状态
     * @param runtime 当前契约的runtime配置或状态
     * @param compiler 当前契约的compiler配置或状态
     * @return 当前受控处理的固定结果
     */
    @Bean
    public com.nexus.report.batch.ReportBatchService reportBatchService(
            ReportResourceStore resources,
            JdbcTemplate jdbc,
            JdbcReportStore store,
            ReportDefinitionService definitions,
            ReportRuntimeService runtime,
            ReportCompiler compiler) {
        return new com.nexus.report.batch.ReportBatchService(
                resources, jdbc, store, definitions, runtime, compiler);
    }

    /**
     * 私有图片服务与定义服务无循环依赖。
     *
     * @param resources 当前契约的resources配置或状态
     * @param artifacts 当前契约的artifacts配置或状态
     * @param authorization 当前契约的authorization配置或状态
     * @param compiler 当前契约的compiler配置或状态
     * @return 当前受控处理的固定结果
     */
    @Bean
    public com.nexus.report.asset.ReportAssetService reportAssetService(
            ReportResourceStore resources,
            ReportArtifactStore artifacts,
            ReportAuthorizationPort authorization,
            ReportCompiler compiler) {
        return new com.nexus.report.asset.ReportAssetService(
                resources, artifacts, authorization, compiler);
    }

    /**
     * 执行reportCatalogQueryExecutor对应的受控处理。
     *
     * @param properties 当前受控操作的properties输入
     * @param catalogs 当前受控操作的catalogs输入
     * @return 当前固定契约或处理结果
     */
    @Bean(destroyMethod = "close")
    public CatalogQueryExecutor reportCatalogQueryExecutor(
            ReportQueryProperties properties, ReportCatalogRegistry catalogs) {
        var executor = new CatalogQueryExecutor(properties);
        executor.approvedTables(
                catalogs.versions().stream()
                        .flatMap(v -> catalogs.require(v).entities().stream())
                        .map(e -> e.table())
                        .collect(java.util.stream.Collectors.toSet()));
        return executor;
    }

    /**
     * 注册代码实体目录，目录版本不会跟随远程更新。
     *
     * @param providers 当前契约的providers配置或状态
     * @param compiler 当前契约的compiler配置或状态
     * @return 当前受控处理的固定结果
     */
    @Bean
    public ReportCatalogRegistry reportCatalogRegistry(
            List<ReportEntityCatalogProvider> providers, ReportCompiler compiler) {
        return new ReportCatalogRegistry(providers, compiler);
    }

    /**
     * 主库扩展元数据复用报表ACL及同数据源事务。
     *
     * @param jdbc 当前契约的jdbc配置或状态
     * @param acl 当前契约的acl配置或状态
     * @return 当前受控处理的固定结果
     */
    @Bean
    public ReportResourceStore reportResourceStore(JdbcTemplate jdbc, JdbcReportStore acl) {
        return new ReportResourceStore(
                jdbc, new DataSourceTransactionManager(jdbc.getDataSource()), acl);
    }

    /**
     * 查询到数据集的固定关联存于系统主库。
     *
     * @param jdbc 当前契约的jdbc配置或状态
     * @return 当前受控处理的固定结果
     */
    @Bean
    public QueryDatasetBindingStore reportQueryDatasetBindingStore(JdbcTemplate jdbc) {
        return new QueryDatasetBindingStore(jdbc);
    }

    /**
     * 固定查询解析无定义服务依赖，避免装配循环。
     *
     * @param links 当前契约的links配置或状态
     * @param resources 当前契约的resources配置或状态
     * @param catalogs 当前契约的catalogs配置或状态
     * @param executor 当前契约的executor配置或状态
     * @param compiler 当前契约的compiler配置或状态
     * @return 当前受控处理的固定结果
     */
    @Bean
    public QueryDatasetResolver reportQueryDatasetResolver(
            QueryDatasetBindingStore links,
            ReportResourceStore resources,
            ReportCatalogRegistry catalogs,
            CatalogQueryExecutor executor,
            ReportCompiler compiler) {
        return new QueryDatasetResolver(links, resources, catalogs, executor, compiler);
    }

    /**
     * 统一代码和目录查询数据集版本解析。
     *
     * @param providers 当前契约的providers配置或状态
     * @param queries 当前契约的queries配置或状态
     * @return 当前受控处理的固定结果
     */
    @Bean
    public ReportDatasetResolver reportDatasetResolver(
            ReportProviderRegistry providers, QueryDatasetResolver queries) {
        return new ReportDatasetResolver(providers, queries);
    }

    /**
     * 原生查询生命周期与独立只读取数服务。
     *
     * @param resources 当前契约的resources配置或状态
     * @param catalogs 当前契约的catalogs配置或状态
     * @param executor 当前契约的executor配置或状态
     * @param links 当前契约的links配置或状态
     * @param store 逻辑存储标识
     * @param definitions 当前契约的definitions配置或状态
     * @param compiler 当前契约的compiler配置或状态
     * @return 当前受控处理的固定结果
     */
    @Bean
    public ReportQueryService reportQueryService(
            ReportResourceStore resources,
            ReportCatalogRegistry catalogs,
            CatalogQueryExecutor executor,
            QueryDatasetBindingStore links,
            JdbcReportStore store,
            ReportDefinitionService definitions,
            ReportCompiler compiler) {
        return new ReportQueryService(
                resources, catalogs, executor, links, store, definitions, compiler);
    }
}
