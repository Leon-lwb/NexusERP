package com.nexus.im.dto;

import lombok.Data;

/**
 * 处理好友申请DTO
 *
 * @author nexus
 */
@Data
public class HandleFriendRequestDTO
{
    private String messageId;
    private boolean accept;
    private String remark;
}
