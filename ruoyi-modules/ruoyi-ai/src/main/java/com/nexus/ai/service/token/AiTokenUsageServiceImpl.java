package com.nexus.ai.service.token;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nexus.common.ai.domain.entity.AiMessage;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.domain.entity.AiTokenUsage;
import com.nexus.common.ai.domain.vo.AiTokenUsageVo;
import com.nexus.common.ai.mapper.AiMessageMapper;
import com.nexus.common.ai.mapper.AiModelMapper;
import com.nexus.common.ai.mapper.AiTokenUsageMapper;
import com.nexus.common.ai.service.IAiTokenUsageService;
import com.nexus.common.satoken.utils.LoginHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI Token 用量日统计服务实现
 * <p>提供按用户/模型/日期维度的 Token 用量聚合、成本计算和历史回填能力</p>
 * <p>日统计表通过唯一索引 (tenant_id, user_id, model_id, usage_date) 保证幂等，支持增量更新</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTokenUsageServiceImpl implements IAiTokenUsageService {

    /** Token 用量日统计 Mapper */
    private final AiTokenUsageMapper aiTokenUsageMapper;
    /** 模型 Mapper，用于读取模型单价计算成本 */
    private final AiModelMapper aiModelMapper;
    /** 消息 Mapper，用于历史数据回填 */
    private final AiMessageMapper aiMessageMapper;

    /**
     * 增量更新日统计（原子 upsert）
     * <p>用 INSERT ... ON DUPLICATE KEY UPDATE 原子写入，依赖唯一索引 uk_user_model_date 保证幂等，
     * 避免并发场景下 select-then-insert 导致唯一索引冲突丢统计（S2-D R1 修复）</p>
     */
    @Override
    public void incrementDailyUsage(Long tenantId, Long userId, String userName, Long modelId,
                                    String modelName, int inputTokens, int outputTokens, BigDecimal cost) {
        long totalTokens = inputTokens + outputTokens;
        aiTokenUsageMapper.upsertDailyUsage(
            com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(),
            tenantId != null ? tenantId : 0,
            userId,
            userName,
            modelId,
            modelName,
            LocalDate.now(),
            inputTokens,
            outputTokens,
            totalTokens,
            cost != null ? cost : BigDecimal.ZERO
        );
    }

    /**
     * 手动触发历史数据回填
     * <p>从 ai_message 表聚合历史数据，按 (userId, modelId, date) 分组填充 ai_token_usage</p>
     * <p>历史数据无 input/output 拆分，回填时 inputTokens = tokenUsage, outputTokens = 0</p>
     */
    @Override
    public void backfillUsage(LocalDate startDate, LocalDate endDate) {
        log.info("[TokenUsage] 开始回填历史数据: {} ~ {}", startDate, endDate);

        // 查询时间范围内的消息（有 token_usage 且有 modelId 的 ASSISTANT 消息）
        LambdaQueryWrapper<AiMessage> query = new LambdaQueryWrapper<>();
        query.eq(AiMessage::getRole, "ASSISTANT")
            .isNotNull(AiMessage::getTokenUsage)
            .isNotNull(AiMessage::getModelId)
            .ge(AiMessage::getCreateTime, java.sql.Date.valueOf(startDate))
            .le(AiMessage::getCreateTime, java.sql.Date.valueOf(endDate.plusDays(1)));
        List<AiMessage> messages = aiMessageMapper.selectList(query);

        if (messages.isEmpty()) {
            log.info("[TokenUsage] 无需回填的数据");
            return;
        }

        // 按 (sessionId) 分组获取 userId（通过 ai_session 关联）
        // 简化实现：按日期+模型聚合，userId 从 session 获取
        // 由于回填是批量操作，按 (modelId, date) 聚合，userId 设为消息的 createBy
        Map<String, List<AiMessage>> grouped = messages.stream()
            .collect(Collectors.groupingBy(m -> {
                LocalDate date = m.getCreateTime().toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                Long modelId = m.getModelId();
                Long userId = m.getCreateBy() != null ? m.getCreateBy() : 0L;
                return userId + "_" + modelId + "_" + date;
            }));

        for (Map.Entry<String, List<AiMessage>> entry : grouped.entrySet()) {
            String[] parts = entry.getKey().split("_");
            Long userId = Long.parseLong(parts[0]);
            Long modelId = Long.parseLong(parts[1]);
            LocalDate date = LocalDate.parse(parts[2]);

            List<AiMessage> group = entry.getValue();
            long totalTokens = group.stream()
                .mapToLong(m -> m.getTokenUsage() != null ? m.getTokenUsage() : 0L)
                .sum();

            // 检查是否已存在
            LambdaQueryWrapper<AiTokenUsage> existQuery = new LambdaQueryWrapper<>();
            existQuery.eq(AiTokenUsage::getUserId, userId)
                .eq(AiTokenUsage::getModelId, modelId)
                .eq(AiTokenUsage::getUsageDate, date);
            AiTokenUsage existing = aiTokenUsageMapper.selectOne(existQuery);

            if (existing == null) {
                AiModel model = aiModelMapper.selectById(modelId);
                String modelName = model != null ? model.getModelName() : "unknown";
                BigDecimal cost = calculateCost(modelId, (int) totalTokens, 0);

                AiTokenUsage usage = new AiTokenUsage();
                usage.setTenantId(0L);
                usage.setUserId(userId);
                usage.setUserName(String.valueOf(userId));
                usage.setModelId(modelId);
                usage.setModelName(modelName);
                usage.setUsageDate(date);
                usage.setTotalInputTokens(totalTokens);
                usage.setTotalOutputTokens(0L);
                usage.setTotalTokens(totalTokens);
                usage.setRequestCount(group.size());
                usage.setTotalCost(cost);
                usage.setCreateTime(LocalDateTime.now());
                usage.setUpdateTime(LocalDateTime.now());
                aiTokenUsageMapper.insert(usage);
            }
        }

        log.info("[TokenUsage] 回填完成，处理 {} 条消息，{} 个分组", messages.size(), grouped.size());
    }

    /**
     * 查询 Token 用量汇总
     */
    @Override
    public List<AiTokenUsageVo> queryUsage(Long userId, Long modelId, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<AiTokenUsage> query = buildQueryWrapper(userId, modelId, startDate, endDate);
        query.orderByDesc(AiTokenUsage::getUsageDate);
        List<AiTokenUsage> list = aiTokenUsageMapper.selectList(query);

        return list.stream().map(this::toVo).collect(Collectors.toList());
    }

    /**
     * 查询 Token 用量趋势（按日聚合）
     */
    @Override
    public List<Map<String, Object>> queryUsageTrend(Long userId, Long modelId, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<AiTokenUsage> query = buildQueryWrapper(userId, modelId, startDate, endDate);
        query.orderByAsc(AiTokenUsage::getUsageDate);
        List<AiTokenUsage> list = aiTokenUsageMapper.selectList(query);

        // 按日期聚合
        Map<LocalDate, List<AiTokenUsage>> byDate = list.stream()
            .collect(Collectors.groupingBy(AiTokenUsage::getUsageDate, TreeMap::new, Collectors.toList()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<LocalDate, List<AiTokenUsage>> entry : byDate.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", entry.getKey().toString());
            row.put("totalTokens", entry.getValue().stream().mapToLong(AiTokenUsage::getTotalTokens).sum());
            row.put("totalInputTokens", entry.getValue().stream().mapToLong(AiTokenUsage::getTotalInputTokens).sum());
            row.put("totalOutputTokens", entry.getValue().stream().mapToLong(AiTokenUsage::getTotalOutputTokens).sum());
            row.put("totalCost", entry.getValue().stream().map(AiTokenUsage::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
            row.put("requestCount", entry.getValue().stream().mapToInt(AiTokenUsage::getRequestCount).sum());
            result.add(row);
        }
        return result;
    }

    /**
     * 查询模型用量排行
     */
    @Override
    public List<Map<String, Object>> queryModelRanking(LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<AiTokenUsage> query = buildQueryWrapper(null, null, startDate, endDate);
        List<AiTokenUsage> list = aiTokenUsageMapper.selectList(query);

        // 按模型聚合
        Map<Long, List<AiTokenUsage>> byModel = list.stream()
            .collect(Collectors.groupingBy(AiTokenUsage::getModelId));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, List<AiTokenUsage>> entry : byModel.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("modelId", entry.getKey());
            row.put("modelName", entry.getValue().get(0).getModelName());
            row.put("totalTokens", entry.getValue().stream().mapToLong(AiTokenUsage::getTotalTokens).sum());
            row.put("totalCost", entry.getValue().stream().map(AiTokenUsage::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
            row.put("requestCount", entry.getValue().stream().mapToInt(AiTokenUsage::getRequestCount).sum());
            result.add(row);
        }

        // 按 totalTokens 降序
        result.sort((a, b) -> Long.compare((long) b.get("totalTokens"), (long) a.get("totalTokens")));
        return result;
    }

    /**
     * 查询用户用量排行
     */
    @Override
    public List<Map<String, Object>> queryUserRanking(LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<AiTokenUsage> query = buildQueryWrapper(null, null, startDate, endDate);
        List<AiTokenUsage> list = aiTokenUsageMapper.selectList(query);

        // 按用户聚合
        Map<Long, List<AiTokenUsage>> byUser = list.stream()
            .collect(Collectors.groupingBy(AiTokenUsage::getUserId));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, List<AiTokenUsage>> entry : byUser.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", entry.getKey());
            row.put("userName", entry.getValue().get(0).getUserName());
            row.put("totalTokens", entry.getValue().stream().mapToLong(AiTokenUsage::getTotalTokens).sum());
            row.put("totalCost", entry.getValue().stream().map(AiTokenUsage::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
            row.put("requestCount", entry.getValue().stream().mapToInt(AiTokenUsage::getRequestCount).sum());
            result.add(row);
        }

        result.sort((a, b) -> Long.compare((long) b.get("totalTokens"), (long) a.get("totalTokens")));
        return result;
    }

    /**
     * 计算单次调用成本
     * <p>成本 = (inputTokens / 1,000,000) * inputPrice + (outputTokens / 1,000,000) * outputPrice</p>
     */
    @Override
    public BigDecimal calculateCost(Long modelId, int inputTokens, int outputTokens) {
        if (modelId == null) {
            return BigDecimal.ZERO;
        }
        AiModel model = aiModelMapper.selectById(modelId);
        if (model == null || model.getInputPricePerMillion() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal inputCost = model.getInputPricePerMillion()
            .multiply(BigDecimal.valueOf(inputTokens))
            .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
        BigDecimal outputCost = BigDecimal.ZERO;
        if (model.getOutputPricePerMillion() != null) {
            outputCost = model.getOutputPricePerMillion()
                .multiply(BigDecimal.valueOf(outputTokens))
                .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
        }
        return inputCost.add(outputCost);
    }

    /**
     * 构建查询条件
     */
    private LambdaQueryWrapper<AiTokenUsage> buildQueryWrapper(Long userId, Long modelId,
                                                                LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<AiTokenUsage> query = new LambdaQueryWrapper<>();
        if (userId != null) {
            query.eq(AiTokenUsage::getUserId, userId);
        }
        if (modelId != null) {
            query.eq(AiTokenUsage::getModelId, modelId);
        }
        if (startDate != null) {
            query.ge(AiTokenUsage::getUsageDate, startDate);
        }
        if (endDate != null) {
            query.le(AiTokenUsage::getUsageDate, endDate);
        }
        return query;
    }

    /**
     * 实体转 Vo（含计算字段）
     */
    private AiTokenUsageVo toVo(AiTokenUsage entity) {
        AiTokenUsageVo vo = new AiTokenUsageVo();
        vo.setId(entity.getId());
        vo.setTenantId(entity.getTenantId());
        vo.setUserId(entity.getUserId());
        vo.setUserName(entity.getUserName());
        vo.setModelId(entity.getModelId());
        vo.setModelName(entity.getModelName());
        vo.setUsageDate(entity.getUsageDate());
        vo.setTotalInputTokens(entity.getTotalInputTokens());
        vo.setTotalOutputTokens(entity.getTotalOutputTokens());
        vo.setTotalTokens(entity.getTotalTokens());
        vo.setRequestCount(entity.getRequestCount());
        vo.setTotalCost(entity.getTotalCost());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());

        // 计算平均每次请求 Token 数
        if (entity.getRequestCount() != null && entity.getRequestCount() > 0) {
            vo.setAvgTokensPerRequest(BigDecimal.valueOf(entity.getTotalTokens())
                .divide(BigDecimal.valueOf(entity.getRequestCount()), 1, RoundingMode.HALF_UP));
        }
        return vo;
    }
}
