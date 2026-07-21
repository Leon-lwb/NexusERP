package com.nexus.im.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 群成员视图（含用户名称/头像/是否群主）
 *
 * @author wanghuan
 */
@Data
public class ImGroupMemberVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 群成员用户 ID。 */
    private Long userId;

    /** 群成员用户名。 */
    private String userName;

    /** 已解析的可访问头像 URL。 */
    private String avatar;

    /** 加入群组的时间。 */
    private Date joinTime;

    /** 是否为群主。 */
    private Boolean owner;
}
