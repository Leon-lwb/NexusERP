package com.nexus.ai.service.kg;

import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;
import com.nexus.common.ai.domain.entity.AiKgCommunity;
import com.nexus.common.ai.domain.entity.AiKgEntity;
import com.nexus.common.ai.domain.entity.AiKgRelation;
import com.nexus.common.ai.domain.entity.AiKnowledgeGraph;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识图谱 Neo4j 存储策略实现（v2 预留）
 * <p>v1 不实现，方法调用即抛出 {@link UnsupportedOperationException}。
 * v2 引入 {@code neo4j-java-driver} + {@code langchain4j-neo4j}，支持多跳遍历与 Leiden 社区（Cypher GDS）。</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Component
@ConditionalOnProperty(prefix = "nexus.ai.knowledge-graph", name = "storage", havingValue = "neo4j")
public class Neo4jGraphStoreStrategy implements GraphStoreStrategy {

    private static final String NOT_IMPLEMENTED = "Neo4j 存储在 S2-A v2 实现";

    @Override
    public String getStoreType() {
        return "neo4j";
    }

    @Override
    public AiKnowledgeGraph findGraphByKnowledge(Long knowledgeId) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public AiKnowledgeGraph findOrCreateGraph(Long knowledgeId, Long modelId, String entityTypes, String relationTypes) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void updateGraph(AiKnowledgeGraph graph) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void deleteGraphData(Long graphId) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void deleteCommunitiesByGraphId(Long graphId) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public List<AiKgEntity> listEntitiesByGraphId(Long graphId) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public AiKgEntity findEntity(Long graphId, String name, String type) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public AiKgEntity getEntityById(Long id) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void insertEntity(AiKgEntity entity) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void updateEntity(AiKgEntity entity) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public List<AiKgRelation> listRelationsByGraphId(Long graphId) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public List<AiKgRelation> listRelationsByEntities(Long graphId, Collection<Long> entityIds) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public AiKgRelation findRelation(Long graphId, Long sourceId, Long targetId, String type) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void insertRelation(AiKgRelation relation) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void updateRelation(AiKgRelation relation) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Set<Long> findNeighborEntityIds(Long graphId, Collection<Long> entityIds) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Set<Long> findTwoHopEntityIds(Long graphId, Collection<Long> entityIds) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void insertCommunity(AiKgCommunity community) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void updateCommunity(AiKgCommunity community) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public List<AiKgCommunity> listCommunitiesByGraphId(Long graphId) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public List<AiKgCommunity> listSummarizedCommunities(Long graphId) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public long countCommunities(Long graphId) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public long countCommunitiesBySummaryStatus(Long graphId, String summaryStatus) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void assignCommunityIds(Map<Long, Long> entityIdToCommunityId) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public List<AiKgEntity> matchEntities(Long graphId, String query) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public List<AiKnowledgeChunk> findSourceChunks(Collection<Long> chunkIds) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }
}
