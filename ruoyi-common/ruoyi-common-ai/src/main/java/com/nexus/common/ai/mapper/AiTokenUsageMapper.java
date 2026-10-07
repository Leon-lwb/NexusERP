package com.nexus.common.ai.mapper;

import com.nexus.common.ai.domain.entity.AiTokenUsage;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.common.ai.domain.vo.AiTokenUsageVo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * AI Token 用量日统计 Mapper 接口
 *
 * @author NexusERP
 * @since 1.0.0
 */
public interface AiTokenUsageMapper extends BaseMapperPlus<AiTokenUsage, AiTokenUsageVo> {

    /**
     * 原子增量更新日统计（INSERT ... ON DUPLICATE KEY UPDATE）
     * <p>依赖唯一索引 uk_user_model_date (tenant_id, user_id, model_id, usage_date) 保证幂等，
     * 避免并发场景下 select-then-insert 导致唯一索引冲突被吞而丢统计（S2-D R1 修复）</p>
     *
     * @return 影响行数（1=新增，2=更新）
     */
    @Insert("""
        INSERT INTO ai_token_usage (id, tenant_id, user_id, user_name, model_id, model_name, usage_date,
            total_input_tokens, total_output_tokens, total_tokens, request_count, total_cost, create_time, update_time)
        VALUES (#{id}, #{tenantId}, #{userId}, #{userName}, #{modelId}, #{modelName}, #{usageDate},
            #{inputTokens}, #{outputTokens}, #{totalTokens}, 1, #{cost}, NOW(), NOW())
        ON DUPLICATE KEY UPDATE
            total_input_tokens = IFNULL(total_input_tokens, 0) + VALUES(total_input_tokens),
            total_output_tokens = IFNULL(total_output_tokens, 0) + VALUES(total_output_tokens),
            total_tokens = IFNULL(total_tokens, 0) + VALUES(total_tokens),
            request_count = IFNULL(request_count, 0) + 1,
            total_cost = IFNULL(total_cost, 0) + VALUES(total_cost),
            update_time = NOW()
        """)
    int upsertDailyUsage(@Param("id") Long id, @Param("tenantId") String tenantId, @Param("userId") Long userId,
                         @Param("userName") String userName, @Param("modelId") Long modelId,
                         @Param("modelName") String modelName, @Param("usageDate") LocalDate usageDate,
                         @Param("inputTokens") long inputTokens, @Param("outputTokens") long outputTokens,
                         @Param("totalTokens") long totalTokens, @Param("cost") BigDecimal cost);
}
