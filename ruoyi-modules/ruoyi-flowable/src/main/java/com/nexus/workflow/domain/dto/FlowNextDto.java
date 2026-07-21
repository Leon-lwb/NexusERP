package com.nexus.workflow.domain.dto;

import lombok.Data;
import org.flowable.bpmn.model.UserTask;

import com.nexus.system.domain.SysRole;
import com.nexus.system.domain.vo.SysUserVo;

import java.io.Serializable;
import java.util.List;

/**
 * 下一流程节点人员/组信息
 * <p>描述流程任务审批时下一处理节点及其候选用户、角色，属于 Flowable 工作流模块。</p>
 */
@Data
public class FlowNextDto implements Serializable {

    /** 序列化版本号。 */
    private static final long serialVersionUID = 1L;

    /** 人员类型（如用户、角色、部门等）。 */
    private String type;

    /** 流程变量 JSON 字符串。 */
    private String vars;

    /** 下一节点对应的用户任务对象。 */
    private UserTask userTask;
    /** 待办人员列表。 */
    private List<SysUserVo> userList;

    /** 候选角色列表。 */
    private List<SysRole> roleList;

    /** 会签是否结束标志。 */
    private boolean bmutiInstanceFinish=false;

    /** 是否串行会签。 */
    private boolean bisSequential;

}
