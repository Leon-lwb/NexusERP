package com.nexus.report.batch;

import com.nexus.report.domain.ReportDsl;
import com.nexus.report.extension.ReportExtensionSchema;
import com.nexus.report.extension.ReportResourceStore;
import com.nexus.report.runtime.RuntimeReviewFixture;

import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** 批次回归使用隔离内存库和私有目录，不连接真实业务库。 */
final class ReportBatchFixture implements AutoCloseable {
    /** 复用真实版本、权限和运行服务。 */
    final RuntimeReviewFixture runtime;

    /** 扩展资源的事务入口。 */
    final ReportResourceStore resources;

    /** 当前测试的批次服务。 */
    final ReportBatchService service;

    /** 已发布的固定单据版本。 */
    final String version;

    /**
     * 安装隔离表结构并发布可用于批次的单据。
     *
     * @param directory 当前测试拥有的私有目录
     */
    ReportBatchFixture(Path directory) {
        runtime = new RuntimeReviewFixture(directory);
        ReportExtensionSchema.apply(runtime.jdbc);
        runtime.store.admissionLimits(50, 100, 1000);
        resources = new ReportResourceStore(runtime.jdbc,
                new DataSourceTransactionManager(runtime.jdbc.getDataSource()), runtime.store);
        var original = runtime.dsl("BATCH_DOCUMENT");
        var document = new ReportDsl(1, original.code(), "批次单据", ReportDsl.Kind.DOCUMENT,
                original.datasetCode(), original.datasetVersionId(), original.columns(),
                List.of(new ReportDsl.Parameter("customerId", "客户", "ID", false, null)),
                original.page(), original.headerRows(), original.footerRows(), original.summaries(), true);
        var draft = runtime.definitions.create(runtime.owner, document, null);
        version = runtime.definitions.publish(runtime.owner, draft.id(), draft.revision()).id();
        service = new ReportBatchService(resources, runtime.jdbc, runtime.store,
                runtime.definitions, runtime.runtime, runtime.compiler);
    }

    /**
     * 返回固定文档请求，避免测试依赖雪花标识的具体数值。
     *
     * @return 单据请求集合
     */
    List<ReportBatchService.Document> documents() {
        return List.of(new ReportBatchService.Document(version, Map.of("customerId", "7")));
    }

    /** 释放本次测试拥有的内存库。 */
    @Override
    public void close() {
        runtime.close();
    }
}
