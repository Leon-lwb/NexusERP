package com.nexus.ai.flow.schedule;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.flow.engine.FlowEngine;
import com.nexus.common.ai.domain.entity.AiFlow;
import com.nexus.common.ai.service.IAiFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AI 流程定时触发任务（SnailJob）
 * <p>
 * SnailJob 后台 ruoyi_group 建任务: executor=aiFlowJob,
 * 任务参数 JSON: {"flowId": 123, "input": {"question": "xxx"}} 或 "flowId=123"
 * <p>
 * 任务线程无登录态：租户上下文由 FlowEngine.start 传入的 tenantIdStr 承载（流程创建人租户），
 * 实例 trigger_by 取流程创建人，SSE 通知发往该用户
 *
 * @author NexusERP
 */
@Component
@RequiredArgsConstructor
@JobExecutor(name = "aiFlowJob")
public class AiFlowScheduleJob {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** AI 流程业务服务 */
    private final IAiFlowService aiFlowService;
    /** 流程执行引擎 */
    private final FlowEngine flowEngine;

    /**
     * 执行定时流程任务
     *
     * @param jobArgs SnailJob 任务参数（含 flowId 与 input）
     * @return 执行结果，含实例ID
     */
    public ExecuteResult jobExecute(JobArgs jobArgs) {
        try {
            // 解析任务参数
            Long flowId = null;
            JsonNode input = null;
            if (jobArgs.getJobParams() != null) {
                String params = jobArgs.getJobParams().toString();
                if (params.matches("\\d+")) {
                    // 纯数字 = flowId
                    flowId = Long.parseLong(params);
                } else {
                    try {
                        JsonNode node = MAPPER.readTree(params);
                        if (node.has("flowId")) {
                            flowId = node.get("flowId").asLong();
                        }
                        if (node.has("input")) {
                            input = node.get("input");
                        }
                    } catch (Exception e) {
                        SnailJobLog.REMOTE.warn("aiFlowJob 任务参数解析失败，尝试按纯数字处理: {}", e.getMessage());
                        // 尝试去掉引号后按数字处理
                        try {
                            flowId = Long.parseLong(params.replace("\"", "").trim());
                        } catch (NumberFormatException ignored) {
                            // 无法解析
                        }
                    }
                }
            }
            if (flowId == null) {
                return ExecuteResult.failure("任务参数缺少 flowId（JSON: {\"flowId\":123,\"input\":{}}）");
            }

            // 查询流程（任务线程无登录态，查询走默认数据源需按租户；flowId 全局唯一雪花 ID 直接查）
            AiFlow flow = aiFlowService.selectById(flowId);
            if (flow == null) {
                return ExecuteResult.failure("流程不存在: flowId=" + flowId);
            }
            if (!"PUBLISHED".equals(flow.getStatus())) {
                return ExecuteResult.failure("流程未发布，无法定时触发: flowId=" + flowId);
            }

            // 触发执行（租户 = 流程创建人租户，用户 = 流程创建人用于 SSE 通知）
            String tenantIdStr = flow.getTenantId() == null ? "000000" : flow.getTenantId();
            Long userId = flow.getCreateBy() == null ? 1L : flow.getCreateBy();
            Long instanceId = flowEngine.start(flow, input, "SCHEDULE", tenantIdStr, userId, null);

            SnailJobLog.REMOTE.info("AI 流程定时触发成功: flowId={}, instanceId={}", flowId, instanceId);
            return ExecuteResult.success("流程已触发，实例ID=" + instanceId);
        } catch (Exception e) {
            SnailJobLog.REMOTE.error("AI 流程定时触发失败: {}", e.getMessage());
            return ExecuteResult.failure("流程触发失败: " + e.getMessage());
        }
    }
}
