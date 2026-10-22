package com.nexus.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.common.ai.domain.vo.AiTokenUsageVo;
import com.nexus.common.ai.service.IAiTokenUsageService;
import com.nexus.common.core.domain.R;
import com.nexus.common.satoken.utils.LoginHelper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Token 用量统计控制器
 * <p>提供按用户/模型/时间维度的 Token 用量查询、趋势分析、排行和回填能力</p>
 * <p>管理员可查看全局统计，普通用户可查看自己的用量</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/token-usage")
public class AiTokenUsageController {

    /** Token 用量业务服务，提供用量查询、趋势、排行与回填。 */
    private final IAiTokenUsageService aiTokenUsageService;

    /**
     * 查询 Token 用量汇总
     * <p>支持按用户、模型、时间范围筛选</p>
     *
     * @param userId    用户ID（可选）
     * @param modelId   模型ID（可选）
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 用量汇总列表
     */
    @SaCheckPermission("ai:tokenUsage:list")
    @GetMapping("/summary")
    public R<List<AiTokenUsageVo>> summary(
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) Long modelId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(aiTokenUsageService.queryUsage(userId, modelId, startDate, endDate));
    }

    /**
     * 查询 Token 用量趋势
     * <p>按日聚合，返回时间序列数据，供前端图表展示</p>
     *
     * @param userId    用户ID（可选）
     * @param modelId   模型ID（可选）
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 按日聚合的趋势数据
     */
    @SaCheckPermission("ai:tokenUsage:list")
    @GetMapping("/trend")
    public R<List<Map<String, Object>>> trend(
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) Long modelId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(aiTokenUsageService.queryUsageTrend(userId, modelId, startDate, endDate));
    }

    /**
     * 查询当前用户的 Token 用量概览
     * <p>普通用户查看自己的用量，无需管理员权限</p>
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 当前用户的用量概览
     */
    @GetMapping("/my-usage")
    public R<Map<String, Object>> myUsage(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long userId = LoginHelper.getUserId();
        List<AiTokenUsageVo> usageList = aiTokenUsageService.queryUsage(userId, null, startDate, endDate);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("startDate", startDate.toString());
        result.put("endDate", endDate.toString());

        long totalTokens = usageList.stream().mapToLong(v -> v.getTotalTokens() != null ? v.getTotalTokens() : 0L).sum();
        long totalInputTokens = usageList.stream().mapToLong(v -> v.getTotalInputTokens() != null ? v.getTotalInputTokens() : 0L).sum();
        long totalOutputTokens = usageList.stream().mapToLong(v -> v.getTotalOutputTokens() != null ? v.getTotalOutputTokens() : 0L).sum();
        int totalRequests = usageList.stream().mapToInt(v -> v.getRequestCount() != null ? v.getRequestCount() : 0).sum();
        BigDecimal totalCost =
                usageList.stream()
                        .map(v -> v.getTotalCost() != null ? v.getTotalCost() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        result.put("totalTokens", totalTokens);
        result.put("totalInputTokens", totalInputTokens);
        result.put("totalOutputTokens", totalOutputTokens);
        result.put("totalRequests", totalRequests);
        result.put("totalCost", totalCost);
        result.put("details", usageList);
        return R.ok(result);
    }

    /**
     * 查询模型用量排行
     * <p>按模型聚合，返回各模型的 Token 用量和成本排行</p>
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 模型用量排行
     */
    @SaCheckPermission("ai:tokenUsage:list")
    @GetMapping("/model-ranking")
    public R<List<Map<String, Object>>> modelRanking(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(aiTokenUsageService.queryModelRanking(startDate, endDate));
    }

    /**
     * 查询用户用量排行
     * <p>按用户聚合，返回各用户的 Token 用量和成本排行</p>
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 用户用量排行
     */
    @SaCheckPermission("ai:tokenUsage:list")
    @GetMapping("/user-ranking")
    public R<List<Map<String, Object>>> userRanking(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return R.ok(aiTokenUsageService.queryUserRanking(startDate, endDate));
    }

    /**
     * 手动触发历史数据回填
     * <p>从 ai_message 表聚合历史数据填充 ai_token_usage</p>
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 操作结果
     */
    @SaCheckPermission("ai:tokenUsage:backfill")
    @PostMapping("/backfill")
    public R<Void> backfill(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        aiTokenUsageService.backfillUsage(startDate, endDate);
        return R.ok();
    }
}
