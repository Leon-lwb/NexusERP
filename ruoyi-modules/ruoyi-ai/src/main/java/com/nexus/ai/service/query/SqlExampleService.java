package com.nexus.ai.service.query;

import com.nexus.common.ai.domain.bo.AiSqlExampleBo;
import com.nexus.common.ai.domain.vo.AiSqlExampleVo;
import com.nexus.common.ai.service.IAiSqlExampleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
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

    /** 示例缓存：key="all" 或 category */
    private final ConcurrentHashMap<String, List<AiSqlExampleVo>> exampleCache = new ConcurrentHashMap<>();

    /**
     * 获取所有已启用的 SQL 示例
     * <p>从缓存加载，缓存未命中时查询数据库</p>
     *
     * @return SQL 示例列表
     */
    public List<AiSqlExampleVo> getAllEnabledExamples() {
        return exampleCache.computeIfAbsent("all", k -> {
            AiSqlExampleBo bo = new AiSqlExampleBo();
            bo.setStatus("1");
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
        return exampleCache.computeIfAbsent(category, k -> {
            AiSqlExampleBo bo = new AiSqlExampleBo();
            bo.setCategory(k);
            bo.setStatus("1");
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
     * 清除全部缓存
     * <p>在 SQL 示例增删改后调用，确保 Agent 使用最新数据</p>
     */
    public void evictCache() {
        exampleCache.clear();
        log.info("[AI-SqlExample] 缓存已清除");
    }
}
