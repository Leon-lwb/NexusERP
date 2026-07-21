package com.nexus.ai.service.kg;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;
import com.nexus.common.ai.domain.entity.AiKgCommunity;
import com.nexus.common.ai.domain.entity.AiKgEntity;
import com.nexus.common.ai.domain.entity.AiKgRelation;
import com.nexus.common.ai.domain.entity.AiKnowledgeGraph;
import com.nexus.common.ai.mapper.AiKgCommunityMapper;
import com.nexus.common.ai.mapper.AiKgEntityMapper;
import com.nexus.common.ai.mapper.AiKgRelationMapper;
import com.nexus.common.ai.mapper.AiKnowledgeChunkMapper;
import com.nexus.common.ai.mapper.AiKnowledgeGraphMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识图谱 MySQL 存储策略实现
 * <p>用 MyBatis-Plus LambdaQueryWrapper 查询，租户隔离由多租户拦截器自动注入 tenant_id 条件。</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nexus.ai.knowledge-graph", name = "storage", havingValue = "mysql", matchIfMissing = true)
public class MysqlGraphStoreStrategy implements GraphStoreStrategy {

    /** 知识图谱实例表 Mapper。 */
    private final AiKnowledgeGraphMapper graphMapper;
    /** 图谱实体表 Mapper。 */
    private final AiKgEntityMapper entityMapper;
    /** 图谱关系表 Mapper。 */
    private final AiKgRelationMapper relationMapper;
    /** 图谱社区表 Mapper。 */
    private final AiKgCommunityMapper communityMapper;
    /** 知识切片表 Mapper（局部召回回溯来源切片用）。 */
    private final AiKnowledgeChunkMapper chunkMapper;

    /** 局部召回实体链接最大命中数 */
    private static final int MAX_ENTITY_LINK_HITS = 50;

    @Override
    public String getStoreType() {
        return "mysql";
    }

    // -------------------- 图谱实例 --------------------

    @Override
    public AiKnowledgeGraph findGraphByKnowledge(Long knowledgeId) {
        // ORDER BY id DESC LIMIT 1：确定性地返回最新图谱记录。
        // 原仅 LIMIT 1 无 ORDER BY，非确定性——防重检查/findOrCreateGraph/getInfo 各调一次可能返回不同记录，
        // 导致"构建写入图A、前端查询图B（空）"错位，且并发构建时 findOrCreateGraph 找不到一致记录而创建重复 graph 行。
        return graphMapper.selectOne(Wrappers.<AiKnowledgeGraph>lambdaQuery()
            .eq(AiKnowledgeGraph::getKnowledgeId, knowledgeId)
            .orderByDesc(AiKnowledgeGraph::getId)
            .last("LIMIT 1"));
    }

    @Override
    public AiKnowledgeGraph findOrCreateGraph(Long knowledgeId, Long modelId, String entityTypes, String relationTypes) {
        AiKnowledgeGraph graph = findGraphByKnowledge(knowledgeId);
        if (graph != null) {
            graph.setGraphStatus("1");
            graph.setErrorMessage(null);
            if (modelId != null) {
                graph.setModelId(modelId);
            }
            if (entityTypes != null) {
                graph.setEntityTypes(entityTypes);
            }
            if (relationTypes != null) {
                graph.setRelationTypes(relationTypes);
            }
            graphMapper.updateById(graph);
            return graph;
        }
        AiKnowledgeGraph g = new AiKnowledgeGraph();
        g.setKnowledgeId(knowledgeId);
        g.setGraphStatus("1");
        g.setModelId(modelId);
        g.setEntityTypes(entityTypes);
        g.setRelationTypes(relationTypes);
        g.setNodeCount(0);
        g.setRelationshipCount(0);
        g.setCommunityCount(0);
        graphMapper.insert(g);
        return g;
    }

    @Override
    public void updateGraph(AiKnowledgeGraph graph) {
        graphMapper.updateById(graph);
    }

    @Override
    public void deleteGraphData(Long graphId) {
        relationMapper.delete(Wrappers.<AiKgRelation>lambdaQuery().eq(AiKgRelation::getGraphId, graphId));
        entityMapper.delete(Wrappers.<AiKgEntity>lambdaQuery().eq(AiKgEntity::getGraphId, graphId));
        communityMapper.delete(Wrappers.<AiKgCommunity>lambdaQuery().eq(AiKgCommunity::getGraphId, graphId));
    }

    @Override
    public void deleteCommunitiesByGraphId(Long graphId) {
        communityMapper.delete(Wrappers.<AiKgCommunity>lambdaQuery().eq(AiKgCommunity::getGraphId, graphId));
        entityMapper.update(null, Wrappers.<AiKgEntity>lambdaUpdate()
            .set(AiKgEntity::getCommunityId, null)
            .eq(AiKgEntity::getGraphId, graphId));
    }

    // -------------------- 实体 --------------------

    @Override
    public List<AiKgEntity> listEntitiesByGraphId(Long graphId) {
        return entityMapper.selectList(Wrappers.<AiKgEntity>lambdaQuery()
            .eq(AiKgEntity::getGraphId, graphId)
            .orderByDesc(AiKgEntity::getMentionCount));
    }

    @Override
    public AiKgEntity findEntity(Long graphId, String name, String type) {
        return entityMapper.selectOne(Wrappers.<AiKgEntity>lambdaQuery()
            .eq(AiKgEntity::getGraphId, graphId)
            .eq(AiKgEntity::getEntityName, name)
            .eq(type != null, AiKgEntity::getEntityType, type)
            .last("LIMIT 1"));
    }

    @Override
    public AiKgEntity getEntityById(Long id) {
        return entityMapper.selectById(id);
    }

    @Override
    public void insertEntity(AiKgEntity entity) {
        entityMapper.insert(entity);
    }

    @Override
    public void updateEntity(AiKgEntity entity) {
        entityMapper.updateById(entity);
    }

    // -------------------- 关系 --------------------

    @Override
    public List<AiKgRelation> listRelationsByGraphId(Long graphId) {
        return relationMapper.selectList(Wrappers.<AiKgRelation>lambdaQuery()
            .eq(AiKgRelation::getGraphId, graphId));
    }

    @Override
    public List<AiKgRelation> listRelationsByEntities(Long graphId, Collection<Long> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) {
            return List.of();
        }
        return relationMapper.selectList(Wrappers.<AiKgRelation>lambdaQuery()
            .eq(AiKgRelation::getGraphId, graphId)
            .and(w -> w.in(AiKgRelation::getSourceEntityId, entityIds)
                .or().in(AiKgRelation::getTargetEntityId, entityIds)));
    }

    @Override
    public AiKgRelation findRelation(Long graphId, Long sourceId, Long targetId, String type) {
        return relationMapper.selectOne(Wrappers.<AiKgRelation>lambdaQuery()
            .eq(AiKgRelation::getGraphId, graphId)
            .eq(AiKgRelation::getSourceEntityId, sourceId)
            .eq(AiKgRelation::getTargetEntityId, targetId)
            .eq(AiKgRelation::getRelationType, type)
            .last("LIMIT 1"));
    }

    @Override
    public void insertRelation(AiKgRelation relation) {
        relationMapper.insert(relation);
    }

    @Override
    public void updateRelation(AiKgRelation relation) {
        relationMapper.updateById(relation);
    }

    @Override
    public Set<Long> findNeighborEntityIds(Long graphId, Collection<Long> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> seed = new HashSet<>(entityIds);
        List<AiKgRelation> rels = relationMapper.selectList(Wrappers.<AiKgRelation>lambdaQuery()
            .eq(AiKgRelation::getGraphId, graphId)
            .and(w -> w.in(AiKgRelation::getSourceEntityId, seed)
                .or().in(AiKgRelation::getTargetEntityId, seed)));
        Set<Long> neighbors = new HashSet<>();
        for (AiKgRelation r : rels) {
            if (seed.contains(r.getSourceEntityId()) && !seed.contains(r.getTargetEntityId())) {
                neighbors.add(r.getTargetEntityId());
            }
            if (seed.contains(r.getTargetEntityId()) && !seed.contains(r.getSourceEntityId())) {
                neighbors.add(r.getSourceEntityId());
            }
        }
        return neighbors;
    }

    @Override
    public Set<Long> findTwoHopEntityIds(Long graphId, Collection<Long> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> seed = new HashSet<>(entityIds);
        Set<Long> oneHop = findNeighborEntityIds(graphId, seed);
        if (oneHop.isEmpty()) {
            return Set.of();
        }
        Set<Long> twoHop = findNeighborEntityIds(graphId, oneHop);
        twoHop.removeAll(seed);
        twoHop.removeAll(oneHop);
        return twoHop;
    }

    // -------------------- 社区 --------------------

    @Override
    public void insertCommunity(AiKgCommunity community) {
        communityMapper.insert(community);
    }

    @Override
    public void updateCommunity(AiKgCommunity community) {
        communityMapper.updateById(community);
    }

    @Override
    public List<AiKgCommunity> listCommunitiesByGraphId(Long graphId) {
        return communityMapper.selectList(Wrappers.<AiKgCommunity>lambdaQuery()
            .eq(AiKgCommunity::getGraphId, graphId)
            .orderByAsc(AiKgCommunity::getCommunityIndex));
    }

    @Override
    public List<AiKgCommunity> listSummarizedCommunities(Long graphId) {
        return communityMapper.selectList(Wrappers.<AiKgCommunity>lambdaQuery()
            .eq(AiKgCommunity::getGraphId, graphId)
            .eq(AiKgCommunity::getSummaryStatus, "2")
            .orderByDesc(AiKgCommunity::getEntityCount));
    }

    @Override
    public long countCommunities(Long graphId) {
        return communityMapper.selectCount(Wrappers.<AiKgCommunity>lambdaQuery()
            .eq(AiKgCommunity::getGraphId, graphId));
    }

    @Override
    public long countCommunitiesBySummaryStatus(Long graphId, String summaryStatus) {
        return communityMapper.selectCount(Wrappers.<AiKgCommunity>lambdaQuery()
            .eq(AiKgCommunity::getGraphId, graphId)
            .eq(AiKgCommunity::getSummaryStatus, summaryStatus));
    }

    @Override
    public void assignCommunityIds(Map<Long, Long> entityIdToCommunityId) {
        if (entityIdToCommunityId == null || entityIdToCommunityId.isEmpty()) {
            return;
        }
        entityIdToCommunityId.forEach((entityId, communityId) -> {
            AiKgEntity update = new AiKgEntity();
            update.setId(entityId);
            update.setCommunityId(communityId);
            entityMapper.updateById(update);
        });
    }

    // -------------------- 检索 --------------------

    @Override
    public List<AiKgEntity> matchEntities(Long graphId, String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<AiKgEntity> all = listEntitiesByGraphId(graphId);
        List<AiKgEntity> hits = new ArrayList<>();
        for (AiKgEntity e : all) {
            String name = e.getEntityName();
            if (name != null && !name.isBlank() && query.contains(name)) {
                hits.add(e);
                if (hits.size() >= MAX_ENTITY_LINK_HITS) {
                    break;
                }
            }
        }
        return hits;
    }

    @Override
    public List<AiKnowledgeChunk> findSourceChunks(Collection<Long> chunkIds) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return List.of();
        }
        return chunkMapper.selectBatchIds(chunkIds);
    }
}
