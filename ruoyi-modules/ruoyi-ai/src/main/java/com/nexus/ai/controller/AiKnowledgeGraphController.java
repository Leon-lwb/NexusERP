package com.nexus.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.ai.service.kg.GlobalSearchService;
import com.nexus.ai.service.kg.GraphStoreStrategy;
import com.nexus.ai.service.kg.KnowledgeGraphService;
import com.nexus.common.ai.domain.bo.AiKgCommunityBo;
import com.nexus.common.ai.domain.bo.AiKgEntityBo;
import com.nexus.common.ai.domain.bo.AiKgRelationBo;
import com.nexus.common.ai.domain.bo.AiKnowledgeGraphBo;
import com.nexus.common.ai.domain.entity.AiKgEntity;
import com.nexus.common.ai.domain.entity.AiKgRelation;
import com.nexus.common.ai.domain.vo.AiKgCommunityVo;
import com.nexus.common.ai.domain.vo.AiKgEntityVo;
import com.nexus.common.ai.domain.vo.AiKgRelationVo;
import com.nexus.common.ai.domain.vo.AiKnowledgeGraphVo;
import com.nexus.common.ai.service.IAiKgCommunityService;
import com.nexus.common.ai.service.IAiKgEntityService;
import com.nexus.common.ai.service.IAiKgRelationService;
import com.nexus.common.ai.service.IAiKnowledgeGraphService;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.web.core.BaseController;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 知识图谱管理 Controller
 * <p>提供图谱构建/查询/邻居/社区/全局检索/重建/删除等接口</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/knowledge/graph")
public class AiKnowledgeGraphController extends BaseController {

    /** 知识图谱构建服务，负责图谱抽取、构建、重建与删除。 */
    private final KnowledgeGraphService knowledgeGraphService;
    /** 图谱存储策略，提供实体/关系/邻居的底层读写。 */
    private final GraphStoreStrategy graphStoreStrategy;
    /** 全局检索服务，基于社区摘要合成全局检索结果。 */
    private final GlobalSearchService globalSearchService;
    /** 知识图谱实例业务服务，提供图谱元信息 CRUD。 */
    private final IAiKnowledgeGraphService aiKnowledgeGraphService;
    /** 图谱实体业务服务，提供实体分页查询。 */
    private final IAiKgEntityService aiKgEntityService;
    /** 图谱关系业务服务，提供关系分页查询。 */
    private final IAiKgRelationService aiKgRelationService;
    /** 图谱社区业务服务，提供社区分页查询。 */
    private final IAiKgCommunityService aiKgCommunityService;

    /**
     * 触发图谱构建（异步）
     */
    @SaCheckPermission("ai:knowledge:graph:build")
    @Log(title = "知识图谱构建", businessType = BusinessType.OTHER)
    @PostMapping("/build/{knowledgeId}")
    public R<Void> build(@PathVariable Long knowledgeId,
                         @RequestParam(required = false) Long modelId) {
        knowledgeGraphService.buildGraph(knowledgeId, modelId, currentUserId(), currentTenantIdStr(), currentUserName());
        return R.ok();
    }

    /**
     * 查询图谱实例
     */
    @SaCheckPermission("ai:knowledge:graph:query")
    @GetMapping("/{knowledgeId}")
    public R<AiKnowledgeGraphVo> getInfo(@PathVariable Long knowledgeId) {
        return R.ok(aiKnowledgeGraphService.selectByKnowledgeId(knowledgeId));
    }

    /**
     * 查询构建进度
     */
    @SaCheckPermission("ai:knowledge:graph:query")
    @GetMapping("/progress/{knowledgeId}")
    public R<Map<String, Object>> progress(@PathVariable Long knowledgeId) {
        return R.ok(knowledgeGraphService.getProgress(knowledgeId));
    }

    /**
     * 分页查询实体列表
     */
    @SaCheckPermission("ai:knowledge:graph:query")
    @GetMapping("/entity/list")
    public TableDataInfo<AiKgEntityVo> entityList(AiKgEntityBo bo, PageQuery pageQuery) {
        return aiKgEntityService.selectPageList(bo, pageQuery);
    }

    /**
     * 分页查询关系列表
     */
    @SaCheckPermission("ai:knowledge:graph:query")
    @GetMapping("/relation/list")
    public TableDataInfo<AiKgRelationVo> relationList(AiKgRelationBo bo, PageQuery pageQuery) {
        return aiKgRelationService.selectPageList(bo, pageQuery);
    }

    /**
     * 分页查询社区列表
     */
    @SaCheckPermission("ai:knowledge:graph:query")
    @GetMapping("/community/list")
    public TableDataInfo<AiKgCommunityVo> communityList(AiKgCommunityBo bo, PageQuery pageQuery) {
        return aiKgCommunityService.selectPageList(bo, pageQuery);
    }

    /**
     * 查询实体 1 跳邻居（实体 + 邻居 + 关系，含名称）
     */
    @SaCheckPermission("ai:knowledge:graph:query")
    @GetMapping("/neighbors/{entityId}")
    public R<Map<String, Object>> neighbors(@PathVariable Long entityId) {
        AiKgEntity entity = graphStoreStrategy.getEntityById(entityId);
        if (entity == null) {
            return R.fail("实体不存在");
        }
        Set<Long> seed = Set.of(entityId);
        Set<Long> neighborIds = graphStoreStrategy.findNeighborEntityIds(entity.getGraphId(), seed);

        // 加载本图全部实体构建 id→实体 / id→名 映射
        Map<Long, AiKgEntity> entityMap = graphStoreStrategy.listEntitiesByGraphId(entity.getGraphId()).stream()
            .collect(Collectors.toMap(AiKgEntity::getId, e -> e, (a, b) -> a));
        Map<Long, String> nameMap = entityMap.values().stream()
            .collect(Collectors.toMap(AiKgEntity::getId, AiKgEntity::getEntityName, (a, b) -> a));

        List<AiKgEntity> neighborEntities = new ArrayList<>();
        for (Long id : neighborIds) {
            AiKgEntity nb = entityMap.get(id);
            if (nb != null) {
                neighborEntities.add(nb);
            }
        }

        Set<Long> relatedIds = new java.util.HashSet<>(seed);
        relatedIds.addAll(neighborIds);
        List<AiKgRelation> relations = graphStoreStrategy.listRelationsByEntities(entity.getGraphId(), relatedIds);
        List<Map<String, Object>> relationList = new ArrayList<>();
        for (AiKgRelation r : relations) {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("sourceEntityId", r.getSourceEntityId());
            rm.put("sourceEntityName", nameMap.get(r.getSourceEntityId()));
            rm.put("relationType", r.getRelationType());
            rm.put("targetEntityId", r.getTargetEntityId());
            rm.put("targetEntityName", nameMap.get(r.getTargetEntityId()));
            rm.put("description", r.getDescription());
            rm.put("strength", r.getStrength());
            relationList.add(rm);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entity", entity);
        result.put("neighbors", neighborEntities);
        result.put("relations", relationList);
        return R.ok(result);
    }

    /**
     * 全局检索（基于社区摘要合成）
     */
    @SaCheckPermission("ai:knowledge:graph:query")
    @PostMapping("/globalSearch/{knowledgeId}")
    public R<Map<String, Object>> globalSearch(@PathVariable Long knowledgeId,
                                                @RequestBody Map<String, String> body) {
        String query = body != null ? body.get("query") : null;
        return R.ok(globalSearchService.globalSearch(knowledgeId, query));
    }

    /**
     * 全量重建（清实体/关系/社区 + 重置 chunk 状态 + 重新构建）
     */
    @SaCheckPermission("ai:knowledge:graph:edit")
    @Log(title = "知识图谱重建", businessType = BusinessType.OTHER)
    @PostMapping("/rebuild/{knowledgeId}")
    public R<Void> rebuild(@PathVariable Long knowledgeId,
                           @RequestParam(required = false) Long modelId) {
        knowledgeGraphService.rebuild(knowledgeId, modelId, currentUserId(), currentTenantIdStr(), currentUserName());
        return R.ok();
    }

    /**
     * 删除图谱（实体/关系/社区 + 实例行），保留 chunk 内容
     */
    @SaCheckPermission("ai:knowledge:graph:remove")
    @Log(title = "知识图谱删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{knowledgeId}")
    public R<Void> remove(@PathVariable Long knowledgeId) {
        knowledgeGraphService.deleteGraph(knowledgeId);
        return R.ok();
    }

    // -------------------- 辅助 --------------------

    private Long currentUserId() {
        return LoginHelper.getUserId();
    }

    private Long currentTenantId() {
        try {
            String s = LoginHelper.getTenantId();
            return (s != null && !s.isEmpty()) ? Long.parseLong(s) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 当前租户ID（字符串，保留前导零如 "000000"，供 TenantHelper.dynamic 在异步线程恢复租户上下文）
     */
    private String currentTenantIdStr() {
        try {
            String s = LoginHelper.getTenantId();
            return (s != null && !s.isEmpty()) ? s : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String currentUserName() {
        try {
            return LoginHelper.getUsername();
        } catch (Exception e) {
            return null;
        }
    }
}
