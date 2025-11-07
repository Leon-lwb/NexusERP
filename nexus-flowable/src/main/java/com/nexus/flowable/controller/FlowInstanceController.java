package com.nexus.flowable.controller;

import com.nexus.common.annotation.Log;
import com.nexus.common.core.controller.BaseController;
import com.nexus.common.core.domain.AjaxResult;
import com.nexus.common.enums.BusinessType;
import com.nexus.flowable.domain.vo.FlowTaskVo;
import com.nexus.flowable.service.IFlowInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * <p>
 * 工作流流程实例管理
 * <p>
 *
 * @author Tony
 * @date 2021-04-03
 */
@Slf4j
@Tag(name = "工作流流程实例管理")
@RestController
@RequestMapping("/flowable/instance")
public class FlowInstanceController extends BaseController {

    @Autowired
    private IFlowInstanceService flowInstanceService;

    @Operation(summary = "根据流程定义id启动流程实例")
    @PostMapping("/startBy/{procDefId}")
    public AjaxResult startById(@PathVariable String procDefId, @RequestBody Map<String, Object> variables) {
        return flowInstanceService.startProcessInstanceById(procDefId, variables);

    }

    @Operation(summary = "激活或挂起流程实例")
    @PostMapping(value = "/updateState")
    public AjaxResult updateState(@RequestParam Integer state, @RequestParam String instanceId) {
        flowInstanceService.updateState(state, instanceId);
        return AjaxResult.success();
    }

    @Operation(summary = "结束流程实例")
    @PostMapping(value = "/stopProcessInstance")
    public AjaxResult stopProcessInstance(@RequestBody FlowTaskVo flowTaskVo) {
        flowInstanceService.stopProcessInstance(flowTaskVo);
        return AjaxResult.success();
    }

    @Operation(summary = "删除流程实例")
    @Log(title = "删除任务", businessType = BusinessType.DELETE)
    @DeleteMapping(value = "/delete/{instanceIds}")
    public AjaxResult delete(@PathVariable String[] instanceIds, @RequestParam(required = false) String deleteReason) {
        for (String instanceId : instanceIds) {
            flowInstanceService.delete(instanceId, deleteReason);
        }
        return AjaxResult.success();
    }
}
