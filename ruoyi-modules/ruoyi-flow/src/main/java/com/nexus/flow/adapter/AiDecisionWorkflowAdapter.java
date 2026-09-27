package com.nexus.flow.adapter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.config.AiProperties;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionWorkflowStartCommand;
import com.nexus.common.ai.decision.port.DecisionWorkflowPort;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.service.IAiModelService;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flow.domain.entity.Flow;
import com.nexus.common.flow.domain.entity.FlowInstance;
import com.nexus.common.flow.mapper.FlowInstanceMapper;
import com.nexus.common.flow.mapper.FlowMapper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.flow.engine.FlowEngine;
import com.nexus.flow.model.FlowGraph;
import com.nexus.flow.node.FlowNodeProvider;
import com.nexus.flow.node.FlowNodeRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

/**
 * 唯一允许调度版本化工业系统图的适配器。
 */
@Service
@RequiredArgsConstructor
public class AiDecisionWorkflowAdapter implements DecisionWorkflowPort {
    private final DecisionFlowDefinitionFactory factory;
    private final FlowMapper flows;
    private final FlowInstanceMapper instances;
    private final FlowNodeRegistry providers;
    private final FlowEngine engine;
    private final IAiModelService models;
    private final ObjectMapper mapper;
    private final AiProperties properties;

    @Override
    public void validateAnalysis(DecisionWorkflowStartCommand command) {
        valid(command, "system_delivery_risk_analysis");
        TenantHelper.dynamic(command.tenantId(), () -> published(factory.analysisFlow(modelId(), properties.getDecision().getPolicyKnowledgeId()), command));
    }

    @Override
    public Long startAnalysis(DecisionWorkflowStartCommand command) {
        valid(command, "system_delivery_risk_analysis");
        return TenantHelper.dynamic(command.tenantId(), () -> {
            Flow flow = published(factory.analysisFlow(modelId(), properties.getDecision().getPolicyKnowledgeId()), command);
            return engine.start(flow, mapper.valueToTree(Map.of("runId", String.valueOf(command.runId()))),
                "DECISION", command.tenantId(), command.userId(), command.userName());
        });
    }

    @Override
    public Long startExecution(DecisionWorkflowStartCommand command) {
        valid(command, "system_delivery_risk_execution");
        return TenantHelper.dynamic(command.tenantId(), () -> {
            Flow flow = published(factory.executionFlow(), command);
            return engine.start(flow, mapper.valueToTree(Map.of("runId", String.valueOf(command.runId()))),
                "DECISION", command.tenantId(), command.userId(), command.userName());
        });
    }

    @Override
    public boolean stop(Long flowInstanceId, DecisionActor actor) {
        if (flowInstanceId == null || flowInstanceId <= 0 || actor == null || actor.tenantId() == null) return false;
        return TenantHelper.dynamic(actor.tenantId(), () -> {
            FlowInstance instance = instances.selectOne(Wrappers.<FlowInstance>lambdaQuery()
                .eq(FlowInstance::getId, flowInstanceId).eq(FlowInstance::getTenantId, actor.tenantId()));
            if (instance == null) return false;
            Flow flow = flows.selectOne(Wrappers.<Flow>lambdaQuery().eq(Flow::getId, instance.getFlowId())
                .eq(Flow::getTenantId, actor.tenantId()));
            if (flow == null || !flow.getFlowKey().startsWith("system_delivery_risk_")) return false;
            return engine.stop(flowInstanceId);
        });
    }

    private Long modelId() {
        AiModel model = models.selectDefaultModel();
        return model != null && model.getId() != null && model.getId() > 0
            && "0".equals(model.getStatus()) ? model.getId() : null;
    }

    private Flow published(Flow generated, DecisionWorkflowStartCommand command) {
        validateGraph(generated);
        String key = generated.getFlowKey() + "_" + command.tenantId();
        generated.setFlowKey(key);
        generated.setTenantId(command.tenantId());
        generated.setCreateBy(command.userId());
        Flow stored = flows.selectOne(Wrappers.<Flow>lambdaQuery()
            .eq(Flow::getFlowKey, key).eq(Flow::getTenantId, command.tenantId()));
        if (stored == null) {
            try {
                if (flows.insert(generated) != 1 || generated.getId() == null || generated.getId() <= 0)
                    throw new ServiceException("工业系统流程配置持久化失败");
                return generated;
            } catch (DuplicateKeyException raced) {
                // 并发首个请求可能已发布相同租户键，不能因异常就信任结果；
                // 必须精确重读该行，并执行下方全部身份、发布状态及规范图护栏。
                stored = flows.selectOne(Wrappers.<Flow>lambdaQuery()
                    .eq(Flow::getFlowKey, key).eq(Flow::getTenantId, command.tenantId()));
                if (stored == null) throw new ServiceException("工业系统流程并发发布未取得可核验配置");
            }
        }
        if (!command.tenantId().equals(stored.getTenantId()) || !key.equals(stored.getFlowKey())
            || !"PUBLISHED".equals(stored.getStatus()) || stored.getVersion() == null
            || stored.getVersion() <= 0 || stored.getVersion() == Integer.MAX_VALUE)
            throw new ServiceException("工业系统流程配置被修改或未发布");
        validateGraph(stored);
        if (Objects.equals(stored.getConfigJson(), generated.getConfigJson())) return stored;
        // 旧图只有与本工厂生成结果逐字节一致时，才能安全迁移。
        // 任意编辑（含通用代码或 SQL 节点）必须拒绝，不能悄然覆盖。
        FlowGraph graph = FlowGraph.parse(stored.getConfigJson());
        Long oldModel = graph.getNode("llm") == null ? null
            : graph.getNode("llm").getConfig().path("modelId").asLong();
        Long oldKnowledge = graph.getNode("rag") == null ? null
            : graph.getNode("rag").getConfig().path("knowledgeId").asLong();
        boolean analysis = generated.getFlowKey().startsWith(DecisionFlowDefinitionFactory.ANALYSIS_KEY);
        Flow previous = analysis ? factory.analysisFlow(oldModel, oldKnowledge) : factory.executionFlow();
        Flow legacy = analysis ? factory.legacyAnalysisFlow(oldModel, oldKnowledge) : previous;
        Flow releasedDefault = analysis ? factory.releasedDefaultAnalysisFlow(oldModel, oldKnowledge) : previous;
        Flow priorContract = analysis ? factory.priorSummaryContractAnalysisFlow(oldModel, oldKnowledge) : previous;
        Flow releasedContract = analysis ? factory.summaryContractReleasedDefaultAnalysisFlow(oldModel, oldKnowledge) : previous;
        if (!Objects.equals(previous.getConfigJson(), stored.getConfigJson())
            && !Objects.equals(legacy.getConfigJson(), stored.getConfigJson())
            && !Objects.equals(releasedDefault.getConfigJson(), stored.getConfigJson())
            && !Objects.equals(priorContract.getConfigJson(), stored.getConfigJson())
            && !Objects.equals(releasedContract.getConfigJson(), stored.getConfigJson()))
            throw new ServiceException("工业系统流程模板存在未授权修改");
        var update = Wrappers.<Flow>lambdaUpdate().eq(Flow::getId, stored.getId())
            .eq(Flow::getTenantId, command.tenantId()).eq(Flow::getFlowKey, key)
            .eq(Flow::getVersion, stored.getVersion()).eq(Flow::getStatus, "PUBLISHED")
            .eq(Flow::getConfigJson, stored.getConfigJson())
            .set(Flow::getConfigJson, generated.getConfigJson())
            .set(Flow::getVersion, stored.getVersion() + 1);
        if (flows.update(null, update) != 1) throw new ServiceException("工业系统流程版本更新冲突");
        generated.setId(stored.getId()); generated.setVersion(stored.getVersion() + 1);
        return generated;
    }

    private void validateGraph(Flow flow) {
        try {
            FlowGraph graph = FlowGraph.parse(flow.getConfigJson());
            graph.validate(providers::contains);
            for (var node : graph.getNodes().values()) {
                FlowNodeProvider provider = providers.getProvider(node.getType());
                if (provider == null) throw new ServiceException("工业节点未注册");
                provider.validateConfig(node.getConfig());
            }
        } catch (RuntimeException invalid) {
            throw new ServiceException("工业系统流程图未通过校验");
        }
    }

    private void valid(DecisionWorkflowStartCommand command, String key) {
        if (command == null || command.runId() == null || command.runId() <= 0
            || !key.equals(command.flowKey()) || command.tenantId() == null
            || !command.tenantId().matches("[A-Za-z0-9_-]{1,20}")
            || command.userId() == null || command.userId() <= 0 || command.userName() == null
            || command.userName().isBlank() || command.input() == null
            || !command.input().equals(Map.of("runId", String.valueOf(command.runId()))))
            throw new ServiceException("工业系统流程启动身份或输入无效");
    }
}
