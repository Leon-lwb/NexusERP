package com.nexus.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.ai.service.governance.AiAbRunService;
import com.nexus.common.ai.domain.entity.AiAbTest;
import com.nexus.common.ai.domain.entity.AiAbTestResult;
import com.nexus.common.ai.domain.vo.AiAbTestVo;
import com.nexus.common.ai.mapper.AiAbTestMapper;
import com.nexus.common.ai.mapper.AiAbTestResultMapper;
import com.nexus.common.ai.service.IAiModelService;
import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    @Autowired
    private AiAbRunService runs;

    /** A/B 测试主表 Mapper，负责测试配置读写。 */
    private final AiAbTestMapper abTestMapper;
    /** A/B 测试结果表 Mapper，负责单次问答结果读写。 */
    private final AiAbTestResultMapper abTestResultMapper;
    /** AI 模型业务服务，用于解析模型名称与配置。 */
    private final IAiModelService aiModelService;
    /** ChatModel 工厂，按模型ID获取具体厂商的对话模型实例。 */
    private final ChatModelFactory chatModelFactory;
    /** AI 专用线程池，用于异步执行 A/B 测试避免阻塞请求线程。 */
    private final Executor aiExecutor;

    private static AiAbTest editable(AiAbTest source) {
        AiAbTest clean = new AiAbTest();
        clean.setTestName(source.getTestName());
        clean.setDescription(source.getDescription());
        clean.setModelAId(source.getModelAId());
        clean.setModelBId(source.getModelBId());
        clean.setTestQuestions(source.getTestQuestions());
        return clean;
    }

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
        AiAbTest clean = editable(test);
        clean.setTenantId(LoginHelper.getTenantId());
        clean.setStatus("PENDING");
        abTestMapper.insert(clean);
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
        if ("RUNNING".equals(existing.getStatus())
                || runs.hasUnconfirmed(LoginHelper.getTenantId(), existing.getId())) {
            return R.fail("测试正在执行中，无法修改");
        }
        AiAbTest clean = editable(test);
        clean.setId(existing.getId());
        abTestMapper.updateById(clean);
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
            if (existing != null
                    && ("RUNNING".equals(existing.getStatus())
                            || runs.hasUnconfirmed(LoginHelper.getTenantId(), id))) {
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
        if (!runs.enabled()) return R.fail(503, "A/B批次执行未启用，请先完成第二阶段数据库迁移");
        runs.start(
                LoginHelper.getTenantId(),
                LoginHelper.getUserId(),
                id,
                UUID.randomUUID().toString());
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
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("testId", testId);
        report.put("testName", test.getTestName());
        report.put(
                "totalQuestions",
                results.stream().map(AiAbTestResult::getQuestion).distinct().count());

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
     * 构建单个模型的报告指标
     */
    private Map<String, Object> buildModelReport(List<AiAbTestResult> results) {
        Map<String, Object> report = new LinkedHashMap<>();
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
}
