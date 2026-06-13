package com.nexus.common.ai.service;

import com.nexus.common.ai.domain.vo.AiTokenUsageVo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * AI Token 用量日统计服务接口
 * <p>提供按用户/模型/日期维度的 Token 用量聚合、成本计算和历史回填能力</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
public interface IAiTokenUsageService {

    /**
     * 增量更新日统计（每次 LLM 调用后触发）
     * <p>按 (userId, modelId, date) 聚合，存在则累加，不存在则插入</p>
     *
     * @param tenantId     租户ID
     * @param userId       用户ID
     * @param userName     用户名称
     * @param modelId      模型ID
     * @param modelName    模型名称
     * @param inputTokens  输入 Token 数
     * @param outputTokens 输出 Token 数
     * @param cost         本次成本
     */
    void incrementDailyUsage(Long tenantId, Long userId, String userName, Long modelId,
                             String modelName, int inputTokens, int outputTokens, BigDecimal cost);

    /**
     * 手动触发历史数据回填
     * <p>从 ai_message 表聚合历史数据，填充 ai_token_usage 表</p>
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     */
    void backfillUsage(LocalDate startDate, LocalDate endDate);

    /**
     * 查询 Token 用量汇总
     *
     * @param userId    用户ID（null=全部用户）
     * @param modelId   模型ID（null=全部模型）
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 用量汇总列表
     */
    List<AiTokenUsageVo> queryUsage(Long userId, Long modelId, LocalDate startDate, LocalDate endDate);

    /**
     * 查询 Token 用量趋势（按日聚合）
     *
     * @param userId    用户ID（null=全部用户）
     * @param modelId   模型ID（null=全部模型）
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 按日聚合的趋势数据
     */
    List<Map<String, Object>> queryUsageTrend(Long userId, Long modelId, LocalDate startDate, LocalDate endDate);

    /**
     * 查询模型用量排行
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 按模型聚合的用量排行
     */
    List<Map<String, Object>> queryModelRanking(LocalDate startDate, LocalDate endDate);

    /**
     * 查询用户用量排行
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 按用户聚合的用量排行
     */
    List<Map<String, Object>> queryUserRanking(LocalDate startDate, LocalDate endDate);

    /**
     * 计算单次调用的成本
     * <p>成本 = (inputTokens / 1,000,000) * inputPrice + (outputTokens / 1,000,000) * outputPrice</p>
     * <p>若模型未配置价格，返回 BigDecimal.ZERO</p>
     *
     * @param modelId      模型ID
     * @param inputTokens  输入 Token 数
     * @param outputTokens 输出 Token 数
     * @return 成本金额
     */
    BigDecimal calculateCost(Long modelId, int inputTokens, int outputTokens);
}
