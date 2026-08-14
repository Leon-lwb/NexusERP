package com.nexus.flow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.flow.engine.FlowEngine;
import com.nexus.common.flow.domain.bo.FlowInstanceBo;
import com.nexus.common.flow.domain.vo.FlowInstanceVo;
import com.nexus.common.flow.domain.vo.FlowNodeRunVo;
import com.nexus.common.flow.service.IFlowInstanceService;
import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.web.core.BaseController;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 流程运行实例 Controller
 *
 * @author NexusERP
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/flow/instance")
public class FlowInstanceController extends BaseController {

    /** AI 流程运行实例业务服务 */
    private final IFlowInstanceService aiFlowInstanceService;
    /** 流程执行引擎（续跑） */
    private final FlowEngine flowEngine;

    /**
     * 分页查询运行实例列表
     * <p>权限标识：{@code ai:flow:list}</p>
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 实例分页列表
     */
    @SaCheckPermission("ai:flow:list")
    @GetMapping("/list")
    public TableDataInfo<FlowInstanceVo> list(FlowInstanceBo bo, PageQuery pageQuery) {
        return aiFlowInstanceService.selectPageList(bo, pageQuery);
    }

    /**
     * 查询实例详情（含 input/output/status/耗时/错误）
     * <p>权限标识：{@code ai:flow:list}</p>
     *
     * @param instanceId 实例ID
     * @return 实例详情
     */
    @SaCheckPermission("ai:flow:list")
    @GetMapping("/{instanceId}")
    public R<FlowInstanceVo> getInfo(@PathVariable Long instanceId) {
        return R.ok(aiFlowInstanceService.selectVoById(instanceId));
    }

    /**
     * 查询实例的节点运行记录（按开始时间升序，含 input/output JSON 快照）
     * <p>权限标识：{@code ai:flow:list}</p>
     *
     * @param instanceId 实例ID
     * @return 节点运行记录列表
     */
    @SaCheckPermission("ai:flow:list")
    @GetMapping("/{instanceId}/nodes")
    public R<List<FlowNodeRunVo>> nodeRuns(@PathVariable Long instanceId) {
        return R.ok(aiFlowInstanceService.selectNodeRuns(instanceId));
    }

    /**
     * 执行级续跑（从失败节点重跑，对齐 n8n retry）
     * <p>权限标识：{@code ai:flow:run}</p>
     *
     * @param instanceId 实例ID
     * @return 续跑后的实例ID
     */
    @SaCheckPermission("ai:flow:run")
    @PostMapping("/{instanceId}/retry")
    public R<Long> retry(@PathVariable Long instanceId) {
        try {
            String tenantIdStr = LoginHelper.getTenantId();
            Long userId = LoginHelper.getUserId();
            Long retried = flowEngine.retry(instanceId, tenantIdStr, userId);
            return R.ok(retried);
        } catch (IllegalArgumentException e) {
            return R.fail("续跑失败: " + e.getMessage());
        }
    }
}
