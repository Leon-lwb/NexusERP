package com.nexus.workflow.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

import com.nexus.system.domain.SysRole;
import com.nexus.system.domain.SysUser;

/**
 * 下一节点动态人员/组信息
 * <p>描述流程下一处理节点的动态候选用户与角色，属于 Flowable 工作流模块。</p>
 *
 * @author wanghuan
 * @createTime 2022/3/10 00:12
 */
@Data
public class WfNextDto implements Serializable {

    /** 人员/组类型。 */
    private String type;

    /** 流程变量 JSON 字符串。 */
    private String vars;

    /** 候选用户列表。 */
    private List<SysUser> userList;

    /** 候选角色列表。 */
    private List<SysRole> roleList;
}
