package com.nexus.ai.service.query;

import com.nexus.ai.mcp.AiToolStatusContract;
import com.nexus.common.ai.domain.bo.AiSqlExampleBo;
import com.nexus.common.ai.domain.vo.AiSqlExampleVo;
import com.nexus.common.ai.service.IAiSqlExampleService;
import com.nexus.common.tenant.helper.TenantHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SQL Few-Shot 示例匹配服务
 * <p>
 * 根据用户问题语义匹配最相关的 SQL 示例，注入到 Agent 上下文中，
 * 帮助 LLM 生成更准确的 SQL 查询语句。
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlExampleService {

    /** SQL 示例持久化服务 */
    private final IAiSqlExampleService sqlExampleService;

    /** 示例缓存按原始租户 String 与分类隔离，不能复用其他租户的热缓存。 */
    private final ConcurrentHashMap<ExampleCacheKey, List<AiSqlExampleVo>> exampleCache = new ConcurrentHashMap<>();

    /** category=null 专用于全部示例，避免与实际分类文本 "all" 碰撞。 */
    private record ExampleCacheKey(String tenantId, String category) {
    }

    /**
     * 获取所有已启用的 SQL 示例
     * <p>从缓存加载，缓存未命中时查询数据库</p>
     *
     * @return SQL 示例列表
     */
    public List<AiSqlExampleVo> getAllEnabledExamples() {
        ExampleCacheKey cacheKey = new ExampleCacheKey(requireTenantId(), null);
        return exampleCache.computeIfAbsent(cacheKey, k -> {
            AiSqlExampleBo bo = new AiSqlExampleBo();
            // 已启用 = status 0（字典 sys_normal_disable，2026-08-28 迁移后语义）
            bo.setStatus(AiToolStatusContract.ACTIVE);
            return sqlExampleService.selectList(bo);
        });
    }

    /**
     * 获取指定分类的已启用 SQL 示例
     *
     * @param category 业务分类（INVENTORY/SALES/PURCHASE/PRODUCTION/GENERAL）
     * @return SQL 示例列表
     */
    public List<AiSqlExampleVo> getExamplesByCategory(String category) {
        ExampleCacheKey cacheKey = new ExampleCacheKey(requireTenantId(), Objects.requireNonNull(category));
        return exampleCache.computeIfAbsent(cacheKey, k -> {
            AiSqlExampleBo bo = new AiSqlExampleBo();
            bo.setCategory(k.category());
            bo.setStatus(AiToolStatusContract.ACTIVE);
            return sqlExampleService.selectList(bo);
        });
    }

    /**
     * 格式化 SQL 示例为 Prompt 注入文本
     * <p>将示例格式化为 LLM 可理解的参考格式，供 Agent 生成 SQL 时参考</p>
     *
     * @param examples SQL 示例列表
     * @return 格式化后的文本
     */
    public String formatExamplesForPrompt(List<AiSqlExampleVo> examples) {
        if (examples == null || examples.isEmpty()) {
            return "暂无参考示例。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("以下是常见的查询示例，供参考：\n\n");
        for (AiSqlExampleVo example : examples) {
            sb.append("问题：").append(example.getQuestion()).append("\n");
            sb.append("SQL：").append(example.getSqlTemplate()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 清除当前租户的全部分类缓存
     * <p>在 SQL 示例增删改后调用，不影响其他租户的热缓存</p>
     */
    public void evictCache() {
        String tenantId = requireTenantId();
        exampleCache.keySet().removeIf(key -> tenantId.equals(key.tenantId()));
        log.info("[AI-SqlExample] 已清除租户 {} 的示例缓存", tenantId);
    }

    /**
     * 显式全局清理入口，不要求调用线程携带租户，可用于全局缓存失效通知。
     */
    public void evictAllCache() {
        exampleCache.clear();
        log.info("[AI-SqlExample] 已清除全部示例缓存");
    }

    /**
     * 租户缺失时先拒绝，不能查库或命中某个共享默认租户的缓存。
     */
    private String requireTenantId() {
        String tenantId = TenantHelper.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("租户上下文缺失，拒绝访问 SQL 示例缓存。");
        }
        return tenantId;
    }
}
