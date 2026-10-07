package com.nexus.common.ai.tenant;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.nexus.common.ai.domain.entity.*;
import com.nexus.common.ai.domain.vo.*;
import com.nexus.common.ai.mapper.AiKnowledgeChunkMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** AI 租户身份是文本，不得因数值映射丢失前导零或拒绝非数字租户。 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class AiTenantIdentityContractTest {

    static Stream<Class<?>> tenantModels() {
        return Stream.of(AiKnowledge.class, AiKnowledgeDocument.class, AiKnowledgeChunk.class,
            AiModel.class, AiProvider.class, AiSession.class, AiMessage.class, AiTool.class,
            AiMcpMarket.class, AiMcpMarketTool.class, AiPromptTemplate.class, AiSqlExample.class,
            AiTokenUsage.class, AiAbTest.class, AiAbTestResult.class, AiImageRecord.class,
            AiKnowledgeGraph.class, AiKnowledgeVo.class, AiKnowledgeDocumentVo.class,
            AiKnowledgeChunkVo.class, AiModelVo.class, AiProviderVo.class,
            AiPromptTemplateVo.class, AiSqlExampleVo.class, AiTokenUsageVo.class,
            AiKnowledgeGraphVo.class);
    }

    @ParameterizedTest(name = "{0} 保留租户文本类型")
    @MethodSource("tenantModels")
    void tenantMustNotBeANumber(Class<?> model) throws Exception {
        assertEquals(String.class, model.getDeclaredField("tenantId").getType());
    }

    @ParameterizedTest(name = "租户 {0} 经真实映射器读写不失真")
    @ValueSource(strings = {"000000", "000021", "TENANT-A"})
    void chunkMapperRoundTripPreservesExactTenant(String tenant) throws Exception {
        var datasource = new DriverManagerDataSource(
            "jdbc:h2:mem:ai_tenant_" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        try (var connection = datasource.getConnection()) {
            var jdbc = new JdbcTemplate(datasource);
            jdbc.execute("CREATE TABLE ai_knowledge_chunk (id BIGINT PRIMARY KEY, "
                + "tenant_id VARCHAR(20), document_id BIGINT, knowledge_id BIGINT, "
                + "content VARCHAR(4000), chunk_index INT, vector_id VARCHAR(128), "
                + "extraction_status VARCHAR(8), entity_count INT, relation_count INT, "
                + "token_used BIGINT, kg_error_message VARCHAR(500), create_dept BIGINT, "
                + "create_by BIGINT, create_time TIMESTAMP, update_by BIGINT, update_time TIMESTAMP)");
            jdbc.update("INSERT INTO ai_knowledge_chunk(id,tenant_id,content) VALUES (?,?,?)",
                1L, tenant, "采购库存业务规范");
            var factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(datasource);
            factoryBean.afterPropertiesSet();
            var factory = factoryBean.getObject();
            assertNotNull(factory);
            factory.getConfiguration().addMapper(AiKnowledgeChunkMapper.class);
            try (var session = factory.openSession(true)) {
                var mapper = session.getMapper(AiKnowledgeChunkMapper.class);
                var chunk = mapper.selectById(1L);
                assertEquals(tenant, chunk.getTenantId());
                chunk.setId(2L);
                assertEquals(1, mapper.insert(chunk));
                assertEquals(tenant, jdbc.queryForObject(
                    "SELECT tenant_id FROM ai_knowledge_chunk WHERE id=2", String.class));
            }
            jdbc.execute("DROP ALL OBJECTS");
        }
    }
}
