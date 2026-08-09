package com.nexus.ai.flow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.flow.engine.FlowEngine;
import com.nexus.ai.flow.model.FlowEdgeDef;
import com.nexus.ai.flow.model.FlowGraph;
import com.nexus.ai.flow.node.FlowNodeProvider;
import com.nexus.ai.flow.node.FlowNodeRegistry;
import com.nexus.ai.flow.node.NodeParamSchema;
import com.nexus.common.ai.domain.bo.AiFlowBo;
import com.nexus.common.ai.domain.entity.AiFlow;
import com.nexus.common.ai.domain.vo.AiFlowVo;
import com.nexus.common.ai.service.IAiFlowService;
import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.web.core.BaseController;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 流程定义 Controller
 *
 * @author NexusERP
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/flow")
public class AiFlowController extends BaseController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** AI 流程业务服务 */
    private final IAiFlowService aiFlowService;
    /** 流程执行引擎 */
    private final FlowEngine flowEngine;
    /** 流程节点注册中心（node-types 元数据） */
    private final FlowNodeRegistry flowNodeRegistry;

    /**
     * 分页查询流程列表
     * <p>权限标识：{@code ai:flow:list}</p>
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 流程分页列表
     */
    @SaCheckPermission("ai:flow:list")
    @GetMapping("/list")
    public TableDataInfo<AiFlowVo> list(AiFlowBo bo, PageQuery pageQuery) {
        return aiFlowService.selectPageList(bo, pageQuery);
    }

    /**
     * 查询流程详情（含 configJson）
     * <p>权限标识：{@code ai:flow:query}</p>
     *
     * @param flowId 流程ID
     * @return 流程详情
     */
    @SaCheckPermission("ai:flow:query")
    @GetMapping("/{flowId}")
    public R<AiFlowVo> getInfo(@PathVariable Long flowId) {
        return R.ok(aiFlowService.selectVoById(flowId));
    }

    /**
     * 查询节点类型元数据（前端面板 + 参数表单驱动）
     * <p>仅要求登录，无需权限</p>
     *
     * @return 节点类型列表（含参数 schema）
     */
    @GetMapping("/node-types")
    public R<List<Map<String, Object>>> nodeTypes() {
        Collection<FlowNodeProvider> providers = flowNodeRegistry.getAll();
        List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (FlowNodeProvider provider : providers) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("type", provider.getNodeType());
            node.put("displayName", provider.getDisplayName());
            List<NodeParamSchema> schemas = provider.getParamSchemas();
            if (schemas == null) {
                schemas = List.of();
            }
            node.put("params", schemas);
            list.add(node);
        }
        return R.ok(list);
    }

    /**
     * 新增流程（存草稿）
     * <p>权限标识：{@code ai:flow:add}</p>
     *
     * @param bo 流程业务对象
     * @return 操作结果
     */
    @SaCheckPermission("ai:flow:add")
    @PostMapping
    public R<Void> add(@Validated @RequestBody AiFlowBo bo) {
        return toAjax(aiFlowService.insertByBo(bo));
    }

    /**
     * 修改流程（仅 DRAFT/OFFLINE 可编辑）
     * <p>权限标识：{@code ai:flow:edit}</p>
     *
     * @param bo 流程业务对象
     * @return 操作结果
     */
    @SaCheckPermission("ai:flow:edit")
    @PutMapping
    public R<Void> edit(@Validated @RequestBody AiFlowBo bo) {
        return toAjax(aiFlowService.updateByBo(bo));
    }

    /**
     * 发布流程（DRAFT→PUBLISHED，校验图合法性后版本+1）
     * <p>权限标识：{@code ai:flow:publish}</p>
     *
     * @param flowId 流程ID
     * @return 操作结果
     */
    @SaCheckPermission("ai:flow:publish")
    @PostMapping("/publish")
    public R<Void> publish(@RequestParam Long flowId) {
        // 发布前校验图合法性（节点类型注册 + 拓扑无环 + 起点存在）
        AiFlow flow = aiFlowService.selectById(flowId);
        if (flow == null) {
            return R.fail("流程不存在");
        }
        try {
            FlowGraph graph = FlowGraph.parse(flow.getConfigJson());
            graph.validate(flowNodeRegistry::contains);
            // 各节点 config 校验 + ERROR_PORT 必须有 error 出边
            for (var entry : graph.getNodes().entrySet()) {
                FlowNodeProvider provider = flowNodeRegistry.getProvider(entry.getValue().getType());
                if (provider != null) {
                    provider.validateConfig(entry.getValue().getConfig());
                }
                if ("ERROR_PORT".equals(entry.getValue().getOnError())
                    && graph.getOutEdges(entry.getValue().getId()).stream().noneMatch(FlowEdgeDef::isErrorBranch)) {
                    return R.fail("发布校验失败: 节点 " + entry.getValue().getName()
                        + " 配置了 ERROR_PORT 但缺少错误输出边（outputs/error/0）");
                }
            }
        } catch (IllegalArgumentException e) {
            return R.fail("发布校验失败: " + e.getMessage());
        }
        return toAjax(aiFlowService.publish(flowId));
    }

    /**
     * 下线流程（PUBLISHED→OFFLINE）
     * <p>权限标识：{@code ai:flow:edit}</p>
     *
     * @param flowId 流程ID
     * @return 操作结果
     */
    @SaCheckPermission("ai:flow:edit")
    @PostMapping("/offline")
    public R<Void> offline(@RequestParam Long flowId) {
        return toAjax(aiFlowService.offline(flowId));
    }

    /**
     * 删除流程（逻辑删除，有运行中实例拒绝）
     * <p>权限标识：{@code ai:flow:remove}</p>
     *
     * @param flowIds 流程ID数组
     * @return 操作结果
     */
    @SaCheckPermission("ai:flow:remove")
    @DeleteMapping("/{flowIds}")
    public R<Void> remove(@PathVariable Long[] flowIds) {
        return toAjax(aiFlowService.deleteByIds(flowIds));
    }

    /**
     * 手动运行流程
     * <p>权限标识：{@code ai:flow:run}</p>
     *
     * @param request 运行请求（flowId + input）
     * @return 实例ID
     */
    @SaCheckPermission("ai:flow:run")
    @PostMapping("/run")
    public R<Long> run(@RequestBody FlowRunRequest request) {
        if (request.getFlowId() == null) {
            return R.fail("flowId 不能为空");
        }
        AiFlow flow = aiFlowService.selectById(request.getFlowId());
        if (flow == null) {
            return R.fail("流程不存在");
        }
        if (!"PUBLISHED".equals(flow.getStatus())) {
            return R.fail("仅 PUBLISHED 状态的流程可运行");
        }
        // 解析输入 JSON
        JsonNode input = null;
        if (request.getInput() != null && !request.getInput().isBlank()) {
            try {
                input = MAPPER.readTree(request.getInput());
            } catch (Exception e) {
                return R.fail("input 不是合法的 JSON: " + e.getMessage());
            }
        }
        // 捕获登录上下文（异步线程用）
        String tenantIdStr = LoginHelper.getTenantId();
        Long userId = LoginHelper.getUserId();
        String userName = LoginHelper.getUsername();
        try {
            Long instanceId = flowEngine.start(flow, input, "MANUAL", tenantIdStr, userId, userName);
            return R.ok(instanceId);
        } catch (IllegalArgumentException e) {
            return R.fail("启动失败: " + e.getMessage());
        }
    }

    /**
     * 停止运行中的实例（协作式，节点边界生效）
     * <p>权限标识：{@code ai:flow:run}</p>
     *
     * @param request 停止请求（instanceId）
     * @return 操作结果
     */
    @SaCheckPermission("ai:flow:run")
    @PostMapping("/stop")
    public R<Void> stop(@RequestBody FlowStopRequest request) {
        if (request.getInstanceId() == null) {
            return R.fail("instanceId 不能为空");
        }
        boolean first = flowEngine.stop(request.getInstanceId());
        return first ? R.ok() : R.fail("实例不存在或已停止");
    }

    /**
     * 运行请求
     *
     * @param flowId 流程ID
     * @param input  运行入参（JSON 字符串）
     */
    public static class FlowRunRequest {
        private Long flowId;
        private String input;

        public Long getFlowId() {
            return flowId;
        }

        public void setFlowId(Long flowId) {
            this.flowId = flowId;
        }

        public String getInput() {
            return input;
        }

        public void setInput(String input) {
            this.input = input;
        }
    }

    /**
     * 停止请求
     *
     * @param instanceId 实例ID
     */
    public static class FlowStopRequest {
        private Long instanceId;

        public Long getInstanceId() {
            return instanceId;
        }

        public void setInstanceId(Long instanceId) {
            this.instanceId = instanceId;
        }
    }
}
