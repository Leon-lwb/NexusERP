package com.nexus.im.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 通讯录部门树节点视图（含该部门直属成员与人数统计）
 *
 * @author nexus
 */
@Data
public class ImContactDeptVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 部门 ID。 */
    private Long deptId;

    /** 父部门 ID。 */
    private Long parentId;

    /** 部门名称。 */
    private String deptName;

    /** 显示顺序。 */
    private Integer orderNum;

    /** 部门状态（0正常 1停用）。 */
    private String status;

    /** 直属成员列表。 */
    private List<ImContactUserVo> users = new ArrayList<>();

    /** 直属成员数。 */
    private Integer userCount = 0;

    /** 含子部门的成员总数。 */
    private Integer totalUserCount = 0;

    /** 子部门。 */
    private List<ImContactDeptVo> children = new ArrayList<>();
}
