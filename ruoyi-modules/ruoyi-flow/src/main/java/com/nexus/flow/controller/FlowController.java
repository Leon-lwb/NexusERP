package com.nexus.flow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flow.domain.bo.FlowBo;
import com.nexus.common.flow.domain.entity.Flow;
import com.nexus.common.flow.domain.entity.FlowInstance;
import com.nexus.common.flow.domain.vo.FlowVersionVo;
import com.nexus.common.flow.domain.vo.FlowVo;
import com.nexus.common.flow.service.IFlowInstanceService;
import com.nexus.common.flow.service.IFlowService;
import com.nexus.common.flow.service.IFlowVersionService;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.web.core.BaseController;
import com.nexus.flow.adapter.DecisionFlowAccessGuard;
import com.nexus.flow.engine.FlowEngine;
import com.nexus.flow.model.FlowEdgeDef;
import com.nexus.flow.model.FlowGraph;
import com.nexus.flow.model.FlowNodeDef;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.node.FlowNodeProvider;
import com.nexus.flow.node.FlowNodeRegistry;
import com.nexus.flow.node.NodeParamSchema;
import com.nexus.flow.state.FlowState;
import com.nexus.flow.template.FlowTemplateCatalog;

import lombok.RequiredArgsConstructor;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
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
public class FlowController extends BaseController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** AI 流程业务服务 */
    private final IFlowService aiFlowService;
    /** AI 流程版本快照服务（版本历史/恢复） */
    private final IFlowVersionService aiFlowVersionService;
    /** 流程运行实例服务（webhook x-wait 同步等待用） */
    private final IFlowInstanceService instanceService;
    /** 流程执行引擎 */
    private final FlowEngine flowEngine;
    /** 流程节点注册中心（node-types 元数据） */
    private final FlowNodeRegistry flowNodeRegistry;

    /** 内置流程模板目录（templates 接口） */
    private final FlowTemplateCatalog flowTemplateCatalog;

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
    public TableDataInfo<FlowVo> list(FlowBo bo, PageQuery pageQuery) {
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
    public R<FlowVo> getInfo(@PathVariable Long flowId) {
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
        List<Map<String, Object>> list = new ArrayList<>();
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
     * @return 新流程ID（前端创建后跳转设计器）
     */
    @SaCheckPermission("ai:flow:add")
    @PostMapping
    public R<Long> add(@Validated @RequestBody FlowBo bo) {
        if (protectedBo(bo) || bo.getId() != null && DecisionFlowAccessGuard.protectedFlow(aiFlowService.selectById(bo.getId())))
            return R.fail("工业系统流程不允许通用创建");
        return R.ok(aiFlowService.insertByBo(bo));
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
    public R<Void> edit(@Validated @RequestBody FlowBo bo) {
        if (protectedBo(bo) || bo.getId() != null && DecisionFlowAccessGuard.protectedFlow(aiFlowService.selectById(bo.getId())))
            return R.fail("工业系统流程不允许通用修改");
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
        if (DecisionFlowAccessGuard.protectedFlow(aiFlowService.selectById(flowId)))
            return R.fail("工业系统流程不可通用发布");
        // 发布前校验图合法性（节点类型注册 + 拓扑无环 + 起点存在）
        Flow flow = aiFlowService.selectById(flowId);
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
        if (DecisionFlowAccessGuard.protectedFlow(aiFlowService.selectById(flowId)))
            return R.fail("工业系统流程不可通用下线");
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
        if (flowIds == null
                || Arrays.stream(flowIds)
                        .anyMatch(
                                id ->
                                        DecisionFlowAccessGuard.protectedFlow(
                                                aiFlowService.selectById(id))))
            return R.fail("工业系统流程不可通用删除");
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
        Flow flow = aiFlowService.selectById(request.getFlowId());
        if (flow == null) {
            return R.fail("流程不存在");
        }
        if (DecisionFlowAccessGuard.protectedFlow(flow)) return R.fail("工业系统流程只能通过决策 API 启动");
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
     * 设计器测试运行（对齐 n8n 执行流程）
     * <p>与正式 run 的区别：不要求 PUBLISHED（DRAFT 即可），且接受画布当前配置
     * configJson 直接执行（未保存也能测）；仍做图校验 + 逐节点 config 校验。
     * 实例 triggerType=TEST，历史页可过滤。权限标识：{@code ai:flow:run}</p>
     *
     * @param request 测试请求（flowId + configJson + input）
     * @return 实例ID
     */
    @SaCheckPermission("ai:flow:run")
    @PostMapping("/run-test")
    public R<Long> runTest(@RequestBody FlowRunTestRequest request) {
        if (request.getFlowId() == null) {
            return R.fail("flowId 不能为空");
        }
        Flow flow = aiFlowService.selectById(request.getFlowId());
        if (flow == null) {
            return R.fail("流程不存在");
        }
        if (DecisionFlowAccessGuard.protectedFlow(flow)) return R.fail("工业系统流程只能通过决策 API 启动");
        // 用画布当前配置覆盖（未保存也能测）；null 时回退 DB 配置
        if (DecisionFlowAccessGuard.protectedConfig(request.getConfigJson()))
            return R.fail("通用测试禁止工业节点");
        if (request.getConfigJson() != null && !request.getConfigJson().isBlank()) {
            try {
                MAPPER.readTree(request.getConfigJson());
                flow.setConfigJson(request.getConfigJson());
            } catch (Exception e) {
                return R.fail("configJson 不是合法的 JSON: " + e.getMessage());
            }
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
        // 测试运行同样做图 + 节点配置校验（仅豁免 PUBLISHED 要求）
        try {
            FlowGraph graph = FlowGraph.parse(flow.getConfigJson());
            graph.validate(flowNodeRegistry::contains);
            for (var entry : graph.getNodes().entrySet()) {
                FlowNodeProvider provider = flowNodeRegistry.getProvider(entry.getValue().getType());
                if (provider != null) {
                    provider.validateConfig(entry.getValue().getConfig());
                }
            }
        } catch (IllegalArgumentException e) {
            return R.fail("测试校验失败: " + e.getMessage());
        }
        // 捕获登录上下文（异步线程用）
        String tenantIdStr = LoginHelper.getTenantId();
        Long userId = LoginHelper.getUserId();
        String userName = LoginHelper.getUsername();
        try {
            Long instanceId = flowEngine.start(flow, input, "TEST", tenantIdStr, userId, userName);
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
        FlowInstance instance = instanceService.selectById(request.getInstanceId());
        if (instance == null || DecisionFlowAccessGuard.protectedConfig(instance.getConfigSnapshot()))
            return R.fail("工业系统实例禁止通过通用入口停止");
        boolean first = flowEngine.stop(request.getInstanceId());
        return first ? R.ok() : R.fail("实例不存在或已停止");
    }

    /**
     * 部分执行：运行到指定节点（含全部上游），对齐 n8n "Execute up to node"
     * <p>复用 run-test 的 DRAFT 豁免 + 画布当前配置覆盖语义；只执行目标节点闭包，
     * 目标节点执行完即终态。用于设计器单独验证某节点前序链路。</p>
     *
     * @param request 测试请求（flowId + configJson + input + destinationNodeId）
     * @return 实例ID
     */
    @SaCheckPermission("ai:flow:run")
    @PostMapping("/run-test-node")
    public R<Long> runTestNode(@RequestBody FlowRunToNodeRequest request) {
        if (request.getFlowId() == null) {
            return R.fail("flowId 不能为空");
        }
        if (request.getDestinationNodeId() == null || request.getDestinationNodeId().isBlank()) {
            return R.fail("destinationNodeId 不能为空");
        }
        Flow flow = aiFlowService.selectById(request.getFlowId());
        if (flow == null) {
            return R.fail("流程不存在");
        }
        if (DecisionFlowAccessGuard.protectedFlow(flow)) return R.fail("工业系统流程只能通过决策 API 启动");
        if (DecisionFlowAccessGuard.protectedConfig(request.getConfigJson()))
            return R.fail("通用测试禁止工业节点");
        if (request.getConfigJson() != null && !request.getConfigJson().isBlank()) {
            try {
                MAPPER.readTree(request.getConfigJson());
                flow.setConfigJson(request.getConfigJson());
            } catch (Exception e) {
                return R.fail("configJson 不是合法的 JSON: " + e.getMessage());
            }
        }
        JsonNode input = null;
        if (request.getInput() != null && !request.getInput().isBlank()) {
            try {
                input = MAPPER.readTree(request.getInput());
            } catch (Exception e) {
                return R.fail("input 不是合法的 JSON: " + e.getMessage());
            }
        }
        try {
            FlowGraph graph = FlowGraph.parse(flow.getConfigJson());
            graph.validate(flowNodeRegistry::contains);
            for (var entry : graph.getNodes().entrySet()) {
                FlowNodeProvider provider = flowNodeRegistry.getProvider(entry.getValue().getType());
                if (provider != null) {
                    provider.validateConfig(entry.getValue().getConfig());
                }
            }
        } catch (IllegalArgumentException e) {
            return R.fail("测试校验失败: " + e.getMessage());
        }
        String tenantIdStr = LoginHelper.getTenantId();
        Long userId = LoginHelper.getUserId();
        String userName = LoginHelper.getUsername();
        try {
            Long instanceId = flowEngine.startToNode(flow, input, "TEST", tenantIdStr, userId, userName,
                request.getDestinationNodeId());
            return R.ok(instanceId);
        } catch (IllegalArgumentException e) {
            return R.fail("启动失败: " + e.getMessage());
        }
    }

    /**
     * 单节点测试（设计器调试用）
     * <p>用 mockState 构建流程状态，单独执行指定节点 Provider 并返回输出。
     * 权限标识：{@code ai:flow:run}</p>
     *
     * @param request 测试请求（flowId + nodeId + mockState）
     * @return 节点输出 JSON
     */
    @SaCheckPermission("ai:flow:run")
    @PostMapping("/node-test")
    public R<JsonNode> nodeTest(@RequestBody FlowNodeTestRequest request) {
        if (request.getFlowId() == null || request.getNodeId() == null) {
            return R.fail("flowId 和 nodeId 不能为空");
        }
        Flow flow = aiFlowService.selectById(request.getFlowId());
        if (flow == null) {
            return R.fail("流程不存在");
        }
        if (DecisionFlowAccessGuard.protectedFlow(flow)) return R.fail("工业系统流程只能通过决策 API 启动");
        try {
            // 解析图 + 校验
            FlowGraph graph = FlowGraph.parse(flow.getConfigJson());
            graph.validate(flowNodeRegistry::contains);
            FlowNodeDef nodeDef = graph.getNode(request.getNodeId());
            if (nodeDef == null) {
                return R.fail("节点不存在: " + request.getNodeId());
            }
            FlowNodeProvider provider = flowNodeRegistry.getProvider(nodeDef.getType());
            if (provider == null) {
                return R.fail("未注册的节点类型: " + nodeDef.getType());
            }
            provider.validateConfig(nodeDef.getConfig());

            // 构建 mock 状态（mockState 为 {input: {...}, nodes: {...}}，缺失部分自动补全）
            ObjectNode mockRoot = MAPPER.createObjectNode();
            if (request.getMockState() != null && !request.getMockState().isBlank()) {
                JsonNode parsed = MAPPER.readTree(request.getMockState());
                if (parsed.isObject()) {
                    mockRoot = (ObjectNode) parsed;
                }
            }
            if (mockRoot.get("input") == null) {
                mockRoot.set("input", MAPPER.createObjectNode());
            }
            if (mockRoot.get("nodes") == null) {
                mockRoot.set("nodes", MAPPER.createObjectNode());
            }
            FlowState state = new FlowState(mockRoot.get("input"));
            // 把 mock 的 nodes 输出写入状态
            JsonNode mockNodes = mockRoot.get("nodes");
            if (mockNodes != null && mockNodes.isObject()) {
                var it = mockNodes.fields();
                while (it.hasNext()) {
                    var entry = it.next();
                    JsonNode output = entry.getValue() != null && entry.getValue().isObject()
                        ? entry.getValue().get("output") : entry.getValue();
                    if (output != null && !output.isMissingNode()) {
                        state.putNodeOutput(entry.getKey(), output);
                    }
                }
            }

            // 执行节点
            FlowExecutionContext ctx = new FlowExecutionContext();
            ctx.setNodeId(nodeDef.getId());
            ctx.setNodeName(nodeDef.getName());
            ctx.setNodeType(nodeDef.getType());
            ctx.setConfig(nodeDef.getConfig());
            ctx.setState(state);
            ctx.setGraph(graph);
            // 租户经 ctx 传递（节点内不依赖 AiRequestContext 线程上下文）
            ctx.setTenantId(LoginHelper.getTenantId());
            JsonNode output = provider.execute(ctx);
            return R.ok(output);
        } catch (IllegalArgumentException e) {
            return R.fail("测试失败: " + e.getMessage());
        } catch (Exception e) {
            return R.fail("测试失败: " + e.getMessage());
        }
    }

    /**
     * 单节点测试请求
     *
     * @param flowId    流程ID
     * @param nodeId    节点ID
     * @param mockState mock 状态 JSON（{input: {...}, nodes: {nodeId: {output: {...}}}}）
     */
    public static class FlowNodeTestRequest {
        private Long flowId;
        private String nodeId;
        private String mockState;

        public Long getFlowId() {
            return flowId;
        }

        public void setFlowId(Long flowId) {
            this.flowId = flowId;
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getMockState() {
            return mockState;
        }

        public void setMockState(String mockState) {
            this.mockState = mockState;
        }
    }

    /**
     * 查询内置流程模板列表（对齐 n8n 模板库精简版，ERP 场景）
     * <p>仅要求登录，无需权限。模板为纯本地节点（SQL/IF/CODE/NOTIFY），
     * 从模板创建后可直接测试运行。</p>
     *
     * @return 模板列表（key/name/category/description/inputExample/configJson）
     */
    @GetMapping("/templates")
    public R<List<Map<String, Object>>> templates() {
        return R.ok(flowTemplateCatalog.getTemplates());
    }

    /**
     * 查询流程的版本历史列表（按版本号降序，不含 configJson）
     * <p>权限标识：{@code ai:flow:list}</p>
     *
     * @param flowId 流程ID
     * @return 版本快照列表
     */
    @SaCheckPermission("ai:flow:list")
    @GetMapping("/{flowId}/versions")
    public R<List<FlowVersionVo>> versions(@PathVariable Long flowId) {
        return R.ok(aiFlowVersionService.selectListByFlowId(flowId));
    }

    /**
     * 查询版本快照详情（含 configJson，供只读预览）
     * <p>权限标识：{@code ai:flow:list}</p>
     *
     * @param versionId 版本快照ID
     * @return 版本快照详情
     */
    @SaCheckPermission("ai:flow:list")
    @GetMapping("/version/{versionId}")
    public R<FlowVersionVo> versionInfo(@PathVariable Long versionId) {
        return R.ok(aiFlowVersionService.selectVoById(versionId));
    }

    /**
     * 恢复版本：快照 config_json 写回流程定义，状态置 DRAFT（需重新发布生效）
     * <p>权限标识：{@code ai:flow:edit}</p>
     *
     * @param request 恢复请求（versionId）
     * @return 恢复后的流程ID
     */
    @SaCheckPermission("ai:flow:edit")
    @PostMapping("/version/restore")
    public R<Long> restoreVersion(@RequestBody FlowVersionRestoreRequest request) {
        if (request.getVersionId() == null) {
            return R.fail("versionId 不能为空");
        }
        FlowVersionVo version = aiFlowVersionService.selectVoById(request.getVersionId());
        if (version == null || DecisionFlowAccessGuard.protectedFlow(aiFlowService.selectById(version.getFlowId()))
            || DecisionFlowAccessGuard.protectedConfig(version.getConfigJson()))
            return R.fail("工业系统流程不允许通用版本恢复");
        try {
            return R.ok(aiFlowVersionService.restore(request.getVersionId()));
        } catch (ServiceException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 恢复版本请求
     *
     * @param versionId 版本快照ID
     */
    public static class FlowVersionRestoreRequest {
        private Long versionId;

        public Long getVersionId() {
            return versionId;
        }

        public void setVersionId(Long versionId) {
            this.versionId = versionId;
        }
    }

    /**
     * Webhook 入站触发（无需登录，flowKey 路由 + 可选 token 校验）
     * <p>POST /ai/flow/webhook/{flowKey}，body 为运行入参 JSON。
     * 流程配置了 webhookToken 时需带请求头 x-flow-token 匹配；未配置则 flowKey 本身即令牌。
     * 请求头 x-wait: true 时同步等待实例终态（最多 60s）并返回完整结果（外部系统一次调用拿结果）。</p>
     *
     * @param flowKey 流程标识（唯一）
     * @param body    运行入参 JSON
     * @return 实例ID（x-wait 时含 status/outputJson/errorMsg）
     */
    @PostMapping("/webhook/{flowKey}")
    public R<Map<String, Object>> webhook(@PathVariable String flowKey,
                                          @RequestBody(required = false) String body,
                                          @RequestHeader(value = "x-flow-token", required = false) String token,
                                          @RequestHeader(value = "x-wait", required = false) String waitHeader) {
        Flow flow = aiFlowService.selectByFlowKey(flowKey);
        if (flow == null) {
            return R.fail("流程不存在: " + flowKey);
        }
        if (DecisionFlowAccessGuard.protectedFlow(flow)) return R.fail("工业决策禁止 Webhook 调用");
        if (!"PUBLISHED".equals(flow.getStatus())) {
            return R.fail("流程未发布，无法 Webhook 触发");
        }
        // token 校验（配置了 webhookToken 时必须匹配；未配置则放行——flowKey 本身即令牌）
        if (flow.getWebhookToken() != null && !flow.getWebhookToken().isBlank()
            && !flow.getWebhookToken().equals(token)) {
            return R.fail("Webhook token 校验失败");
        }
        // 解析入参 JSON
        JsonNode input = null;
        if (body != null && !body.isBlank()) {
            try {
                input = MAPPER.readTree(body);
            } catch (Exception e) {
                return R.fail("入参不是合法的 JSON: " + e.getMessage());
            }
        }
        // 无登录态：用流程创建人租户/用户（对齐 FlowScheduleJob）
        String tenantIdStr = flow.getTenantId() == null ? "000000" : flow.getTenantId();
        Long userId = flow.getCreateBy() == null ? 1L : flow.getCreateBy();
        try {
            Long instanceId = flowEngine.start(flow, input, "WEBHOOK", tenantIdStr, userId, null);
            // x-wait: true → 同步等待终态（最多 60s）并返回完整结果
            if ("true".equalsIgnoreCase(waitHeader) || "1".equals(waitHeader)) {
                Map<String, Object> result = new HashMap<>();
                result.put("instanceId", instanceId);
                long deadline = System.currentTimeMillis() + 60000;
                FlowInstance inst = null;
                while (System.currentTimeMillis() < deadline) {
                    try {
                        inst =
                                TenantHelper.dynamic(
                                        tenantIdStr, () -> instanceService.selectById(instanceId));
                    } catch (Exception ignore) {
                        // 忽略瞬时查询异常
                    }
                    if (inst != null && !"RUNNING".equals(inst.getStatus())) {
                        break;
                    }
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                if (inst == null || "RUNNING".equals(inst.getStatus())) {
                    result.put("status", "TIMEOUT");
                    result.put("message", "60s 内未完成，请用 instanceId 查询或轮询");
                } else {
                    result.put("status", inst.getStatus());
                    result.put("outputJson", inst.getOutputJson());
                    result.put("errorMsg", inst.getErrorMsg());
                    result.put("durationMs", inst.getDurationMs());
                }
                return R.ok(result);
            }
            return R.ok(Map.of("instanceId", instanceId));
        } catch (IllegalArgumentException e) {
            return R.fail("启动失败: " + e.getMessage());
        }
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
     * 测试运行请求（设计器 执行流程）
     *
     * @param flowId     流程ID
     * @param configJson 画布当前配置（可选；null 时用 DB 配置）
     * @param input      运行入参（JSON 字符串）
     */
    public static class FlowRunTestRequest {
        private Long flowId;
        private String configJson;
        private String input;

        public Long getFlowId() {
            return flowId;
        }

        public void setFlowId(Long flowId) {
            this.flowId = flowId;
        }

        public String getConfigJson() {
            return configJson;
        }

        public void setConfigJson(String configJson) {
            this.configJson = configJson;
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

    /**
     * 部分执行（运行到指定节点）请求
     *
     * @param flowId           流程ID
     * @param configJson       画布当前配置（可选；null 时用 DB 配置）
     * @param input            运行入参（JSON 字符串）
     * @param destinationNodeId 目标节点ID（执行到它为止）
     */
    public static class FlowRunToNodeRequest {
        private Long flowId;
        private String configJson;
        private String input;
        private String destinationNodeId;

        public Long getFlowId() {
            return flowId;
        }

        public void setFlowId(Long flowId) {
            this.flowId = flowId;
        }

        public String getConfigJson() {
            return configJson;
        }

        public void setConfigJson(String configJson) {
            this.configJson = configJson;
        }

        public String getInput() {
            return input;
        }

        public void setInput(String input) {
            this.input = input;
        }

        public String getDestinationNodeId() {
            return destinationNodeId;
        }

        public void setDestinationNodeId(String destinationNodeId) {
            this.destinationNodeId = destinationNodeId;
        }
    }
    private boolean protectedBo(FlowBo bo) {
        return bo == null || bo.getFlowKey() != null && bo.getFlowKey().startsWith("system_delivery_risk_")
            || DecisionFlowAccessGuard.protectedConfig(bo.getConfigJson());
    }
}
