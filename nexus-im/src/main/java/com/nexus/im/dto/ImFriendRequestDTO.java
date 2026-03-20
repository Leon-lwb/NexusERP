package com.nexus.im.dto;

import lombok.Data;

/**
 * 发送好友申请DTO
 *
 * @author nexus
 */
@Data
public class ImFriendRequestDTO
{
    private Long toUserId;
    private String requestMessage;
    private String remark;
}
