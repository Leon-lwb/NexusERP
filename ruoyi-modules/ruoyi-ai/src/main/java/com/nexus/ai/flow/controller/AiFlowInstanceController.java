package com.nexus.ai.flow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.ai.domain.bo.AiFlowInstanceBo;
import com.nexus.common.ai.domain.vo.AiFlowInstanceVo;
import com.nexus.common.ai.domain.vo.AiFlowNodeRunVo;
import com.nexus.common.ai.service.IAiFlowInstanceService;
import com.nexus.common.core.domain.R;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.web.core.BaseController;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
public class AiFlowInstanceController extends BaseController {

    /** AI 流程运行实例业务服务 */
    private final IAiFlowInstanceService aiFlowInstanceService;

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
    public TableDataInfo<AiFlowInstanceVo> list(AiFlowInstanceBo bo, PageQuery pageQuery) {
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
    public R<AiFlowInstanceVo> getInfo(@PathVariable Long instanceId) {
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
    public R<List<AiFlowNodeRunVo>> nodeRuns(@PathVariable Long instanceId) {
        return R.ok(aiFlowInstanceService.selectNodeRuns(instanceId));
    }
}
