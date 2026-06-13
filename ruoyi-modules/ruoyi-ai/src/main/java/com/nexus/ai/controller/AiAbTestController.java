package com.nexus.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.ai.domain.entity.AiAbTest;
import com.nexus.common.ai.domain.entity.AiAbTestResult;
import com.nexus.common.ai.domain.vo.AiAbTestVo;
import com.nexus.common.ai.mapper.AiAbTestMapper;
import com.nexus.common.ai.mapper.AiAbTestResultMapper;
import com.nexus.common.ai.service.IAiModelService;
import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.ai.service.chat.ChatModelFactory;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * AI A/B 测试控制器
 * <p>支持创建测试、异步执行、人工评分、结果对比</p>
 *
 * @author NexusERP
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/ab-test")
public class AiAbTestController {

    private final AiAbTestMapper abTestMapper;
    private final AiAbTestResultMapper abTestResultMapper;
    private final IAiModelService aiModelService;
    private final ChatModelFactory chatModelFactory;
    private final Executor aiExecutor;

    /**
     * 查询 A/B 测试列表
     *
     * @return 测试列表
     */
    @SaCheckPermission("ai:abtest:list")
    @GetMapping("/list")
    public TableDataInfo<AiAbTestVo> list(PageQuery pageQuery) {
        Page<AiAbTestVo> page = abTestMapper.selectVoPage(pageQuery.build(),
            new LambdaQueryWrapper<AiAbTest>().orderByDesc(AiAbTest::getCreateTime));
        // 填充模型名称
        page.getRecords().forEach(this::fillModelNames);
        return TableDataInfo.build(page);
    }

    /**
     * 查询 A/B 测试详情
     *
     * @param id 测试ID
     * @return 测试详情
     */
    @SaCheckPermission("ai:abtest:query")
    @GetMapping("/{id}")
    public R<AiAbTestVo> getInfo(@PathVariable Long id) {
        AiAbTestVo vo = abTestMapper.selectVoById(id);
        if (vo != null) {
            fillModelNames(vo);
        }
        return R.ok(vo);
    }

    /**
     * 新增 A/B 测试
     *
     * @param test 测试配置
     * @return 操作结果
     */
    @SaCheckPermission("ai:abtest:add")
    @PostMapping
    public R<Void> add(@RequestBody AiAbTest test) {
        test.setStatus("PENDING");
        abTestMapper.insert(test);
        return R.ok();
    }

    /**
     * 修改 A/B 测试
     *
     * @param test 测试配置
     * @return 操作结果
     */
    @SaCheckPermission("ai:abtest:edit")
    @PutMapping
    public R<Void> edit(@RequestBody AiAbTest test) {
        AiAbTest existing = abTestMapper.selectById(test.getId());
        if (existing == null) {
            return R.fail("测试不存在");
        }
        if ("RUNNING".equals(existing.getStatus())) {
            return R.fail("测试正在执行中，无法修改");
        }
        abTestMapper.updateById(test);
        return R.ok();
    }

    /**
     * 删除 A/B 测试
     *
     * @param ids 测试ID数组
     * @return 操作结果
     */
    @SaCheckPermission("ai:abtest:remove")
    @DeleteMapping("/{ids}")
    public R<Void> remove(@PathVariable Long[] ids) {
        for (Long id : ids) {
            AiAbTest existing = abTestMapper.selectById(id);
            if (existing != null && "RUNNING".equals(existing.getStatus())) {
                return R.fail("测试 " + id + " 正在执行中，无法删除");
            }
            // 删除关联的测试结果
            abTestResultMapper.delete(new LambdaQueryWrapper<AiAbTestResult>().eq(AiAbTestResult::getTestId, id));
        }
        abTestMapper.deleteByIds(List.of(ids));
        return R.ok();
    }

    /**
     * 异步执行 A/B 测试
     * <p>对每个测试问题，分别用模型A和模型B生成回答，记录结果</p>
     *
     * @param id 测试ID
     * @return 操作结果
     */
    @SaCheckPermission("ai:abtest:edit")
    @PostMapping("/execute/{id}")
    public R<Void> execute(@PathVariable Long id) {
        AiAbTest test = abTestMapper.selectById(id);
        if (test == null) {
            return R.fail("测试不存在");
        }
        if ("RUNNING".equals(test.getStatus())) {
            return R.fail("测试正在执行中，请勿重复执行");
        }

        // 更新状态为执行中
        test.setStatus("RUNNING");
        abTestMapper.updateById(test);

        // 异步执行
        CompletableFuture.runAsync(() -> {
            try {
                executeTestInternal(test);
            } catch (Exception e) {
                log.error("[A/B测试] 执行失败: testId={}", id, e);
                test.setStatus("COMPLETED");
                test.setResultSummary("{\"error\":\"" + e.getMessage() + "\"}");
                abTestMapper.updateById(test);
            }
        }, aiExecutor);

        return R.ok();
    }

    /**
     * 查询测试结果列表
     *
     * @param testId 测试ID
     * @return 结果列表
     */
    @SaCheckPermission("ai:abtest:query")
    @GetMapping("/result/{testId}")
    public R<List<AiAbTestResult>> getResults(@PathVariable Long testId) {
        List<AiAbTestResult> results = abTestResultMapper.selectList(
            new LambdaQueryWrapper<AiAbTestResult>()
                .eq(AiAbTestResult::getTestId, testId)
                .orderByAsc(AiAbTestResult::getQuestion)
                .orderByAsc(AiAbTestResult::getModelId)
        );
        return R.ok(results);
    }

    /**
     * 提交人工评分
     *
     * @param resultId 结果ID
     * @param score    评分（1-5）
     * @return 操作结果
     */
    @SaCheckPermission("ai:abtest:edit")
    @PutMapping("/result/{resultId}/score")
    public R<Void> scoreResult(@PathVariable Long resultId, @RequestParam Integer score) {
        if (score < 1 || score > 5) {
            return R.fail("评分必须在 1-5 之间");
        }
        AiAbTestResult result = abTestResultMapper.selectById(resultId);
        if (result == null) {
            return R.fail("结果不存在");
        }
        result.setScore(score);
        abTestResultMapper.updateById(result);
        return R.ok();
    }

    /**
     * 生成测试报告
     * <p>汇总评分、Token 用量、延迟等指标</p>
     *
     * @param testId 测试ID
     * @return 报告数据
     */
    @SaCheckPermission("ai:abtest:query")
    @GetMapping("/report/{testId}")
    public R<Map<String, Object>> report(@PathVariable Long testId) {
        List<AiAbTestResult> results = abTestResultMapper.selectList(
            new LambdaQueryWrapper<AiAbTestResult>().eq(AiAbTestResult::getTestId, testId)
        );

        AiAbTest test = abTestMapper.selectById(testId);
        if (test == null) {
            return R.fail("测试不存在");
        }

        // 计算模型A和模型B的指标
        Map<String, Object> report = new java.util.LinkedHashMap<>();
        report.put("testId", testId);
        report.put("testName", test.getTestName());
        report.put("totalQuestions", results.stream().map(AiAbTestResult::getQuestion).distinct().count());

        // 模型A指标
        List<AiAbTestResult> modelAResults = results.stream()
            .filter(r -> r.getModelId().equals(test.getModelAId())).toList();
        report.put("modelA", buildModelReport(modelAResults));

        // 模型B指标
        List<AiAbTestResult> modelBResults = results.stream()
            .filter(r -> r.getModelId().equals(test.getModelBId())).toList();
        report.put("modelB", buildModelReport(modelBResults));

        return R.ok(report);
    }

    // ==================== 私有方法 ====================

    /**
     * 执行 A/B 测试内部逻辑
     * <p>对每个问题分别用两个模型生成回答</p>
     */
    private void executeTestInternal(AiAbTest test) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        List<String> questions;
        try {
            questions = mapper.readValue(test.getTestQuestions(),
                mapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            throw new RuntimeException("解析测试问题失败: " + e.getMessage(), e);
        }

        ChatModel modelA = chatModelFactory.getChatModel(test.getModelAId());
        ChatModel modelB = chatModelFactory.getChatModel(test.getModelBId());

        String modelAName = aiModelService.selectById(test.getModelAId()).getDisplayName();
        String modelBName = aiModelService.selectById(test.getModelBId()).getDisplayName();

        for (String question : questions) {
            // 模型A
            executeSingleQuestion(test.getId(), question, test.getModelAId(), modelAName, modelA);
            // 模型B
            executeSingleQuestion(test.getId(), question, test.getModelBId(), modelBName, modelB);
        }

        // 更新测试状态为完成
        test.setStatus("COMPLETED");
        abTestMapper.updateById(test);
    }

    /**
     * 用指定模型执行单个问题
     */
    private void executeSingleQuestion(Long testId, String question, Long modelId,
                                        String modelName, ChatModel chatModel) {
        AiAbTestResult result = new AiAbTestResult();
        result.setTestId(testId);
        result.setQuestion(question);
        result.setModelId(modelId);
        result.setModelName(modelName);

        try {
            long startTime = System.currentTimeMillis();
            ChatResponse response = chatModel.chat(UserMessage.from(question));
            long latency = System.currentTimeMillis() - startTime;

            result.setResponse(response.aiMessage().text());
            result.setLatencyMs((int) latency);
            if (response.tokenUsage() != null) {
                result.setTokenUsage(response.tokenUsage().totalTokenCount());
            }
        } catch (Exception e) {
            log.error("[A/B测试] 模型 {} 回答失败: {}", modelName, e.getMessage());
            result.setResponse("执行失败: " + e.getMessage());
        }

        abTestResultMapper.insert(result);
    }

    /**
     * 填充模型名称
     */
    private void fillModelNames(AiAbTestVo vo) {
        if (vo.getModelAId() != null) {
            var model = aiModelService.selectById(vo.getModelAId());
            if (model != null) {
                vo.setModelAName(model.getDisplayName());
            }
        }
        if (vo.getModelBId() != null) {
            var model = aiModelService.selectById(vo.getModelBId());
            if (model != null) {
                vo.setModelBName(model.getDisplayName());
            }
        }
    }

    /**
     * 构建单个模型的报告指标
     */
    private Map<String, Object> buildModelReport(List<AiAbTestResult> results) {
        Map<String, Object> report = new java.util.LinkedHashMap<>();
        if (results.isEmpty()) {
            report.put("count", 0);
            return report;
        }

        report.put("count", results.size());

        // 平均评分
        List<AiAbTestResult> scored = results.stream().filter(r -> r.getScore() != null).toList();
        if (!scored.isEmpty()) {
            double avgScore = scored.stream().mapToInt(AiAbTestResult::getScore).average().orElse(0);
            report.put("avgScore", Math.round(avgScore * 100.0) / 100.0);
        }

        // 平均 Token 用量
        List<AiAbTestResult> withTokens = results.stream().filter(r -> r.getTokenUsage() != null).toList();
        if (!withTokens.isEmpty()) {
            double avgTokens = withTokens.stream().mapToInt(AiAbTestResult::getTokenUsage).average().orElse(0);
            report.put("avgTokenUsage", Math.round(avgTokens));
        }

        // 平均延迟
        List<AiAbTestResult> withLatency = results.stream().filter(r -> r.getLatencyMs() != null).toList();
        if (!withLatency.isEmpty()) {
            double avgLatency = withLatency.stream().mapToInt(AiAbTestResult::getLatencyMs).average().orElse(0);
            report.put("avgLatencyMs", Math.round(avgLatency));
        }

        return report;
    }
}
