package com.nexus.im.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 好友视图（含好友资料、备注、在线状态）
 *
 * @author nexus
 */
@Data
public class ImFriendVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 好友用户 ID。 */
    private Long friendUserId;

    /** 好友登录用户名。 */
    private String userName;

    /** 好友昵称。 */
    private String nickName;

    /** 已解析的头像 URL。 */
    private String avatar;

    /** 我给好友设置的备注。 */
    private String remark;

    /** 在线状态：online / offline。 */
    private String onlineStatus;

    /** 最后活跃时间（Redis 在线心跳写入时间，离线时可能为空）。 */
    private String lastActiveTime;

    /** 成为好友的时间。 */
    private Date friendTime;
}
