package com.nexus.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM消息实体
 *
 * @author nexus
 */
@Data
@TableName("im_message")
public class ImMessage {
    
    @TableId(value = "message_id", type = IdType.INPUT)
    private String messageId;
    
    @TableField("from_user_id")
    private Long fromUserId;
    
    @TableField("to_user_id")
    private Long toUserId;
    
    @TableField("group_id")
    private String groupId;
    
    @TableField("content")
    private String content;
    
    @TableField("message_type")
    private String messageType;
    
    @TableField("status")
    private String status;
    
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    @TableField("is_read")
    private Integer isRead;
    
    @TableField("extra")
    private String extra;
    
    @TableField("extra_data")
    private String extraData;
    
    @TableField("source")
    private String source;
    
    @TableField("update_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    
    @TableField("is_revoked")
    private Integer isRevoked;
    
    @TableField("revoke_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime revokeTime;
}
