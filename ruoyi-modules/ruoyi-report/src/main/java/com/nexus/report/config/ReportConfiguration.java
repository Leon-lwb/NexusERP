package com.nexus.report.config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.engine.ReportCompiler;
import com.nexus.report.export.*;
import com.nexus.report.mapper.JdbcReportStore;
import com.nexus.report.runtime.*;
import com.nexus.report.service.ReportDefinitionService;
import com.nexus.report.service.ReportDatasetVersionService;
import com.nexus.report.service.ReportTemplatePackageService;
import com.nexus.report.service.ReportLookupService;
import com.nexus.report.version.*;
import com.nexus.report.storage.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.nio.file.Path;
import java.util.List;
@Configuration
@EnableScheduling
@EnableConfigurationProperties(ReportProperties.class)
public class ReportConfiguration {
    @Bean public ReportRuntimeMetrics reportRuntimeMetrics(org.springframework.beans.factory.ObjectProvider<io.micrometer.core.instrument.MeterRegistry> registries){return new ReportRuntimeMetrics(registries.getIfAvailable(io.micrometer.core.instrument.simple.SimpleMeterRegistry::new));}
    @Bean public ReportProviderRegistry reportProviderRegistry(List<ReportDatasetProvider> providers){return new ReportProviderRegistry(providers);}
    @Bean public ReportCompiler reportCompiler(ObjectMapper json){return new ReportCompiler(json);}
    @Bean public ReportEngineExecutor reportV1Executor(ReportCompiler compiler){return new V1ReportEngineExecutor(compiler);}
    @Bean public ReportEngineRegistry reportEngineRegistry(List<ReportEngineExecutor> executors,ReportProperties properties){return new ReportEngineRegistry(executors,java.util.Map.of(1,ReportCompiler.ENGINE_VERSION),properties.getDisabledEngines());}
    @Bean public ReportLookupService reportLookupService(ReportDatasetVersionService versions,ReportDefinitionService definitions,List<ReportLookupProvider> providers){return new ReportLookupService(versions,definitions,providers);}
    @Bean public ReportTemplatePackageService reportTemplatePackageService(ReportDefinitionService definitions,ReportCompiler compiler){return new ReportTemplatePackageService(definitions,compiler);}
    @Bean public ReportDatasetVersionService reportDatasetVersionService(JdbcReportStore store,ReportProviderRegistry registry,ReportDefinitionService definitions,ReportCompiler compiler){return new ReportDatasetVersionService(store,registry,definitions,compiler);}
    @Bean(destroyMethod="close") public ReportArtifactStore reportArtifactStore(ReportProperties properties){return new LocalReportArtifactStore(Path.of(properties.getArtifactRoot()),properties.getMaxArtifactBytes());}
    @Bean public ReportSnapshotStore reportSnapshotStore(ReportArtifactStore artifacts,ObjectMapper json){return new ReportSnapshotStore(artifacts,json);}
    @Bean public ReportDefinitionService reportDefinitionService(JdbcReportStore store,ReportProviderRegistry registry,ReportAuthorizationPort authorization,ReportCompiler compiler,ReportEngineRegistry engines,ReportRuntimeMetrics metrics){return new ReportDefinitionService(store,registry,authorization,compiler,engines,metrics);}
    @Bean public ReportRuntimeService reportRuntimeService(JdbcReportStore store,ReportDefinitionService definitions,ReportCompiler compiler,ReportSnapshotStore snapshots,ReportArtifactStore artifacts,ReportProperties properties,ObjectMapper json,ReportRuntimeMetrics metrics){var runtime=new ReportRuntimeService(store,definitions,compiler,snapshots,artifacts,properties,json);runtime.metrics(metrics);return runtime;}
    @Bean(destroyMethod="close") public ReportExportWorker reportExportWorker(JdbcReportStore store,ReportRuntimeService runtime,ReportArtifactStore artifacts,ReportProperties properties){return new ReportExportWorker(store,runtime,artifacts,properties,new ReportExportRenderer(runtime,properties));}
}
