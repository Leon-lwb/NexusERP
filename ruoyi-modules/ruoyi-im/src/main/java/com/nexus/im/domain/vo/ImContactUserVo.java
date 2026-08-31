package com.nexus.im.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通讯录成员视图（精简字段，仅登录即可获取）
 *
 * @author nexus
 */
@Data
public class ImContactUserVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID。 */
    private Long userId;

    /** 登录用户名。 */
    private String userName;

    /** 用户昵称。 */
    private String nickName;

    /** 已解析的头像 URL。 */
    private String avatar;

    /** 所属部门 ID。 */
    private Long deptId;

    /** 所属部门名称。 */
    private String deptName;

    /** 岗位名称（多个以顿号连接）。 */
    private String postName;

    /** 在线状态：online / offline。 */
    private String onlineStatus;

    /** 最后活跃时间（Redis 在线心跳写入时间，离线时可能为空）。 */
    private String lastActiveTime;
}
