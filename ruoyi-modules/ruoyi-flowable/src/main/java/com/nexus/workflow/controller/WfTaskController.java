package com.nexus.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.ObjectUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import javax.imageio.ImageIO;

import org.springframework.web.bind.annotation.*;

import com.nexus.common.core.domain.R;
import com.nexus.workflow.domain.bo.WfTaskBo;
import com.nexus.workflow.service.IWfTaskService;

/**
 * 工作流任务管理
 * <p>提供任务维度的审批、驳回、退回、加签、委派、转办、签收及流程图生成等 REST 接口，属于 Flowable 工作流模块。</p>
 *
 * @author wanghuan
 * @createTime 2022/3/10 00:12
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/workflow/task")
public class WfTaskController {

    /** 流程任务业务服务，提供审批、退回、加签、委派、流程图等任务操作能力。 */
    private final IWfTaskService flowTaskService;

    /**
     * 取消申请（终止流程）。
     *
     * @param bo 流程任务业务对象（含流程实例与取消意见）
     * @return 操作结果，成功时返回成功状态
     */
    @PostMapping(value = "/stopProcess")
    @SaCheckPermission("workflow:process:cancel")
    public R stopProcess(@RequestBody WfTaskBo bo) {
        flowTaskService.stopProcess(bo);
        return R.ok();
    }

    /**
     * 收回流程（在下一节点未处理前撤回）。
     *
     * @param bo 流程任务业务对象
     * @return 操作结果，成功时返回成功状态
     */
    @PostMapping(value = "/recallProcess")
    @SaCheckPermission("workflow:process:recall")
    public R recallProcess(@RequestBody WfTaskBo bo) {
    	return flowTaskService.recallProcess(bo);
    }

    /**
     * 撤回流程。
     *
     * @param bo 流程任务业务对象
     * @return 操作结果，成功时返回成功状态
     */
    @PostMapping(value = "/revokeProcess")
    @SaCheckPermission("workflow:process:revoke")
    public R revokeProcess(@RequestBody WfTaskBo bo) {
        flowTaskService.revokeProcess(bo);
        return R.ok();
    }

    /**
     * 获取流程变量。
     *
     * @param taskId 流程任务 ID
     * @return 流程变量集合
     */
    @GetMapping(value = "/processVariables/{taskId}")
    @SaCheckPermission("workflow:process:query")
    public R processVariables(@PathVariable(value = "taskId") String taskId) {
        return R.ok(flowTaskService.getProcessVariables(taskId));
    }

    /**
     * 审批通过任务。
     *
     * @param bo 流程任务业务对象（含任务 ID 与审批意见）
     * @return 操作结果，成功时返回成功状态
     */
    @PostMapping(value = "/complete")
    @SaCheckPermission("workflow:process:approval")
    public R complete(@RequestBody WfTaskBo bo) {
        flowTaskService.complete(bo);
        return R.ok();
    }

    /**
     * 驳回任务（回到上一节点或发起人）。
     *
     * @param taskBo 流程任务业务对象
     * @return 操作结果，成功时返回成功状态
     */
    @PostMapping(value = "/reject")
    @SaCheckPermission("workflow:process:approval")
    public R taskReject(@RequestBody WfTaskBo taskBo) {
        flowTaskService.taskReject(taskBo);
        return R.ok();
    }

    /**
     * 拒绝任务（终止流程）。
     *
     * @param taskBo 流程任务业务对象
     * @return 操作结果，成功时返回成功状态
     */
    @PostMapping(value = "/refuse")
    @SaCheckPermission("workflow:process:approval")
    public R taskRefuse(@RequestBody WfTaskBo taskBo) {
        flowTaskService.taskRefuse(taskBo);
        return R.ok();
    }

    /**
     * 退回任务（回退到指定节点）。
     *
     * @param bo 流程任务业务对象
     * @return 操作结果，成功时返回成功状态
     */
    @PostMapping(value = "/return")
    @SaCheckPermission("workflow:process:approval")
    public R taskReturn(@RequestBody WfTaskBo bo) {
        flowTaskService.taskReturn(bo);
        return R.ok();
    }


    /**
     * 加签任务（在当前节点增加处理人）。
     *
     * @param bo 流程任务业务对象
     * @return 操作结果，成功时返回成功状态
     */
    @PostMapping(value = "/addSign")
    @SaCheckPermission("workflow:process:approval")
    public R addSignTask(@RequestBody WfTaskBo bo) {
        flowTaskService.addSignTask(bo);
        return R.ok("加签任务成功");
    }

    /**
     * 多实例加签任务。
     *
     * @param bo 流程任务业务对象
     * @return 操作结果，成功时返回成功状态
     */
    @PostMapping(value = "/multiInstanceAddSign")
    @SaCheckPermission("workflow:process:approval")
    public R multiInstanceAddSign(@RequestBody WfTaskBo bo) {
        flowTaskService.multiInstanceAddSign(bo);
        return R.ok("多实例加签任务成功");
    }

    /**
     * 任务跳转（跳转到指定节点）。
     *
     * @param bo 流程任务业务对象
     * @return 操作结果，成功时返回成功状态
     */
    @PostMapping(value = "/jump")
    @SaCheckPermission("workflow:process:approval")
    public R taskJump(@RequestBody WfTaskBo bo) {
        flowTaskService.taskJump(bo);
        return R.ok();
    }

    /**
     * 获取用户任务节点列表（用于任务跳转选择）。
     *
     * @param bo 流程任务业务对象
     * @return 用户任务节点列表
     */
    @PostMapping(value = "/userTask")
    @SaCheckPermission("workflow:process:approval")
    public R userTaskList(@RequestBody WfTaskBo bo) {
    	return flowTaskService.userTaskList(bo);
    }

    /**
     * 获取所有可回退的节点列表。
     *
     * @param bo 流程任务业务对象
     * @return 可回退节点列表
     */
    @PostMapping(value = "/returnList")
    @SaCheckPermission("workflow:process:query")
    public R findReturnTaskList(@RequestBody WfTaskBo bo) {
        return R.ok(flowTaskService.findReturnTaskList(bo));
    }

    /**
     * 删除任务。
     *
     * @param bo 流程任务业务对象
     * @return 操作结果，成功时返回成功状态
     */
    @DeleteMapping(value = "/delete")
    @SaCheckPermission("workflow:process:approval")
    public R delete(@RequestBody WfTaskBo bo) {
        flowTaskService.deleteTask(bo);
        return R.ok();
    }

    /**
     * 认领/签收任务。
     *
     * @param bo 流程任务业务对象
     * @return 操作结果，成功时返回成功状态
     */
    @PostMapping(value = "/claim")
    @SaCheckPermission("workflow:process:claim")
    public R claim(@RequestBody WfTaskBo bo) {
        flowTaskService.claim(bo);
        return R.ok();
    }

    /**
     * 取消认领/签收任务。
     *
     * @param bo 流程任务业务对象
     * @return 操作结果，成功时返回成功状态
     */
    @PostMapping(value = "/unClaim")
    @SaCheckPermission("workflow:process:claim")
    public R unClaim(@RequestBody WfTaskBo bo) {
        flowTaskService.unClaim(bo);
        return R.ok();
    }

    /**
     * 委派任务（委托他人代为处理）。
     *
     * @param bo 流程任务业务对象（需包含任务 ID 与目标用户 ID）
     * @return 操作结果，成功时返回成功状态
     */
    @PostMapping(value = "/delegate")
    @SaCheckPermission("workflow:process:approval")
    public R delegate(@RequestBody WfTaskBo bo) {
        if (ObjectUtil.hasNull(bo.getTaskId(), bo.getUserId())) {
            return R.fail("参数错误！");
        }
        flowTaskService.delegateTask(bo);
        return R.ok();
    }

    /**
     * 转办任务（将任务转交给他人处理）。
     *
     * @param bo 流程任务业务对象（需包含任务 ID 与目标用户 ID）
     * @return 操作结果，成功时返回成功状态
     */
    @PostMapping(value = "/transfer")
    @SaCheckPermission("workflow:process:approval")
    public R transfer(@RequestBody WfTaskBo bo) {
        if (ObjectUtil.hasNull(bo.getTaskId(), bo.getUserId())) {
            return R.fail("参数错误！");
        }
        flowTaskService.transferTask(bo);
        return R.ok();
    }

    /**
     * 判断是否为第一个发起人节点（用于退回/驳回场景）。
     *
     * @param dataId    流程业务数据 ID
     * @param variables 变量集合（JSON 对象，含 actStatus、processInstanceId）
     * @return 是否为首个发起人节点的布尔结果
     */
    @PostMapping(value = "/isFirstInitiator/{dataId}")
    public R isFirstInitiator(@PathVariable(value = "dataId") String dataId,
            @RequestBody Map<String, Object> variables) {

    	String sRevokeOrReject = (String) variables.get("actStatus");
    	String processInstanceId = (String) variables.get("processInstanceId");
    	return R.ok(flowTaskService.isFirstInitiator(processInstanceId, sRevokeOrReject));
    }

    /**
     * 删除自定义业务任务关联表与流程历史表（以便重新发起流程）。
     *
     * @param dataId    流程业务数据 ID
     * @param variables 变量集合（JSON 对象，含 actStatus、processInstanceId）
     * @return 删除结果
     */
    @SaCheckPermission("workflow:process:remove")
    @PostMapping(value = "/deleteActivityAndJoin/{dataId}")
    public R del(@PathVariable(value = "dataId") String dataId,
            @RequestBody Map<String, Object> variables) {
    	String sRevokeOrReject = (String) variables.get("actStatus");
    	String processInstanceId = (String) variables.get("processInstanceId");
    	return R.ok(flowTaskService.deleteActivityAndJoin(dataId, processInstanceId, sRevokeOrReject));
    }

    /**
     * 生成流程流程图（PNG）。
     *
     * @param processId 流程实例 ID
     */
    @RequestMapping("/diagram/{processId}")
    public void genProcessDiagram(HttpServletResponse response,
                                  @PathVariable("processId") String processId) {
        InputStream inputStream = flowTaskService.diagram(processId);
        OutputStream os = null;
        BufferedImage image = null;
        try {
            image = ImageIO.read(inputStream);
            response.setContentType("image/png");
            os = response.getOutputStream();
            if (image != null) {
                ImageIO.write(image, "png", os);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (os != null) {
                    os.flush();
                    os.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
