package com.nexus.workflow.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

import com.nexus.system.domain.SysRole;
import com.nexus.system.domain.SysUser;

/**
 * 动态人员、组
 * @author wanghuan
 * @createTime 2022/3/10 00:12
 */
@Data
public class WfNextDto implements Serializable {

    private String type;

    private String vars;

    private List<SysUser> userList;

    private List<SysRole> roleList;
}
