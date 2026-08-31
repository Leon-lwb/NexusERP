package com.nexus.ai.service.kg;

import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;
import com.nexus.common.ai.domain.entity.AiKgCommunity;
import com.nexus.common.ai.domain.entity.AiKgEntity;
import com.nexus.common.ai.domain.entity.AiKgRelation;
import com.nexus.common.ai.domain.entity.AiKnowledgeGraph;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识图谱存储策略
 * <p>抽象图谱实例/实体/关系/社区的存储与查询，v1 实现 {@code MysqlGraphStoreStrategy}，
 * v2 可扩展 {@code Neo4jGraphStoreStrategy}（多跳遍历 + Leiden 社区）。</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
public interface GraphStoreStrategy {

    /**
     * @return 存储类型（mysql | neo4j）
     */
    String getStoreType();

    // -------------------- 图谱实例 --------------------

    /**
     * 按知识库ID查询图谱实例
     */
    AiKnowledgeGraph findGraphByKnowledge(Long knowledgeId);

    /**
     * 查找或创建图谱实例（构建入口，幂等：已存在则置为构建中）
     */
    AiKnowledgeGraph findOrCreateGraph(Long knowledgeId, Long modelId, String entityTypes, String relationTypes);

    /**
     * 更新图谱实例（状态/计数/错误信息等）
     */
    void updateGraph(AiKnowledgeGraph graph);

    /**
     * 删除图谱的全部实体/关系/社区（重建用，保留图谱实例行）
     */
    void deleteGraphData(Long graphId);

    /**
     * 删除图谱的全部社区并重置实体 community_id（社区重算用，保留实体/关系）
     */
    void deleteCommunitiesByGraphId(Long graphId);

    // -------------------- 实体 --------------------

    /**
     * 查询图谱全部实体（社区检测加载用）
     */
    List<AiKgEntity> listEntitiesByGraphId(Long graphId);

    /**
     * 按 name + type 查询实体（同名合并查重用，type 为 null 时按 name 查任一）
     */
    AiKgEntity findEntity(Long graphId, String name, String type);

    /**
     * 按主键查询实体
     */
    AiKgEntity getEntityById(Long id);

    /**
     * 新增实体
     */
    void insertEntity(AiKgEntity entity);

    /**
     * 更新实体（合并 mention_count/description 等）
     */
    void updateEntity(AiKgEntity entity);

    // -------------------- 关系 --------------------

    /**
     * 查询图谱全部关系（社区检测加载用）
     */
    List<AiKgRelation> listRelationsByGraphId(Long graphId);

    /**
     * 查询与给定实体集合相关的全部关系（@Tool 关系遍历用）
     */
    List<AiKgRelation> listRelationsByEntities(Long graphId, Collection<Long> entityIds);

    /**
     * 查询去重用关系（同 source/target/type）
     */
    AiKgRelation findRelation(Long graphId, Long sourceId, Long targetId, String type);

    /**
     * 新增关系
     */
    void insertRelation(AiKgRelation relation);

    /**
     * 更新关系（取 strength/confidence 较大者）
     */
    void updateRelation(AiKgRelation relation);

    /**
     * 查询 1 跳邻居实体ID集合
     */
    Set<Long> findNeighborEntityIds(Long graphId, Collection<Long> entityIds);

    /**
     * 查询 2 跳邻居实体ID集合
     */
    Set<Long> findTwoHopEntityIds(Long graphId, Collection<Long> entityIds);

    // -------------------- 社区 --------------------

    /**
     * 新增社区
     */
    void insertCommunity(AiKgCommunity community);

    /**
     * 更新社区（摘要/状态/token 等）
     */
    void updateCommunity(AiKgCommunity community);

    /**
     * 查询图谱全部社区（摘要生成阶段遍历用）
     */
    List<AiKgCommunity> listCommunitiesByGraphId(Long graphId);

    /**
     * 查询已生成摘要的社区（全局检索 map 阶段用）
     */
    List<AiKgCommunity> listSummarizedCommunities(Long graphId);

    /**
     * 统计图谱社区总数（构建进度查询用，避免加载摘要全文）
     */
    long countCommunities(Long graphId);

    /**
     * 按摘要状态统计社区数（"2"=已完成 "3"=失败，构建进度/部分失败提示用）
     */
    long countCommunitiesBySummaryStatus(Long graphId, String summaryStatus);

    /**
     * 批量回填实体所属社区ID
     *
     * @param entityIdToCommunityId 实体ID → 社区ID
     */
    void assignCommunityIds(Map<Long, Long> entityIdToCommunityId);

    // -------------------- 检索（局部召回 / @Tool） --------------------

    /**
     * 实体链接：查询名称与查询文本相关的实体（v1 子串匹配）
     */
    List<AiKgEntity> matchEntities(Long graphId, String query);

    /**
     * 按切片ID批量加载来源切片内容（局部召回回溯用）
     */
    List<AiKnowledgeChunk> findSourceChunks(Collection<Long> chunkIds);
}
