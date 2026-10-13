package com.nexus.report.config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.engine.ReportCompiler;
import com.nexus.report.export.*;
import com.nexus.report.mapper.JdbcReportStore;
import com.nexus.report.runtime.*;
import com.nexus.report.service.ReportDefinitionService;
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
    @Bean public ReportProviderRegistry reportProviderRegistry(List<ReportDatasetProvider> providers){return new ReportProviderRegistry(providers);}
    @Bean public ReportCompiler reportCompiler(ObjectMapper json){return new ReportCompiler(json);}
    @Bean(destroyMethod="close") public ReportArtifactStore reportArtifactStore(ReportProperties properties){return new LocalReportArtifactStore(Path.of(properties.getArtifactRoot()),properties.getMaxArtifactBytes());}
    @Bean public ReportSnapshotStore reportSnapshotStore(ReportArtifactStore artifacts,ObjectMapper json){return new ReportSnapshotStore(artifacts,json);}
    @Bean public ReportDefinitionService reportDefinitionService(JdbcReportStore store,ReportProviderRegistry registry,ReportAuthorizationPort authorization,ReportCompiler compiler){return new ReportDefinitionService(store,registry,authorization,compiler);}
    @Bean public ReportRuntimeService reportRuntimeService(JdbcReportStore store,ReportDefinitionService definitions,ReportCompiler compiler,ReportSnapshotStore snapshots,ReportArtifactStore artifacts,ReportProperties properties,ObjectMapper json){return new ReportRuntimeService(store,definitions,compiler,snapshots,artifacts,properties,json);}
    @Bean(destroyMethod="close") public ReportExportWorker reportExportWorker(JdbcReportStore store,ReportRuntimeService runtime,ReportArtifactStore artifacts,ReportProperties properties){return new ReportExportWorker(store,runtime,artifacts,properties,new ReportExportRenderer(runtime,properties));}
}
