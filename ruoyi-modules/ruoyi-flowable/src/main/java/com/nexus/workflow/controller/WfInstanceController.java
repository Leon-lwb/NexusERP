package com.nexus.workflow.controller;


import com.nexus.common.core.domain.R;
import com.nexus.workflow.domain.bo.WfTaskBo;
import com.nexus.workflow.service.IWfInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 工作流流程实例管理
 * <p>提供流程实例的激活/挂起、结束、删除及详情查询等 REST 接口，属于 Flowable 工作流模块。</p>
 *
 * @author wanghuan
 * @createTime 2022/3/10 00:12
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/instance")
public class WfInstanceController {

    /** 流程实例业务服务，提供状态变更、结束、删除及详情查询等能力。 */
    private final IWfInstanceService instanceService;

    /**
     * 激活或挂起流程实例。
     *
     * @param state      状态（1：激活，2：挂起）
     * @param instanceId 流程实例 ID
     * @return 操作结果，成功时返回成功状态
     */
    @PostMapping(value = "/updateState")
    public R updateState(@RequestParam Integer state, @RequestParam String instanceId) {
        instanceService.updateState(state, instanceId);
        return R.ok();
    }

    /**
     * 结束流程实例。
     *
     * @param bo 流程任务业务对象（含流程实例与结束意见）
     * @return 操作结果，成功时返回成功状态
     */
    @PostMapping(value = "/stopProcessInstance")
    public R stopProcessInstance(@RequestBody WfTaskBo bo) {
        instanceService.stopProcessInstance(bo);
        return R.ok();
    }

    /**
     * 删除流程实例。
     *
     * @param instanceId   流程实例 ID
     * @param deleteReason 删除原因
     * @return 操作结果，成功时返回成功状态
     */
    @Deprecated
    @DeleteMapping(value = "/delete")
    public R delete(@RequestParam String instanceId, String deleteReason) {
        instanceService.delete(instanceId, deleteReason);
        return R.ok();
    }

    /**
     * 查询流程实例详情信息。
     *
     * @param procInsId 流程实例 ID
     * @param deployId  流程部署 ID
     * @return 流程实例详情信息
     */
    @GetMapping("/detail")
    public R detail(String procInsId, String deployId) {
        return R.ok(instanceService.queryDetailProcess(procInsId, deployId));
    }
}
