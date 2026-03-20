package com.nexus.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * IM好友实体
 *
 * @author nexus
 */
@Data
@TableName("im_friend")
public class ImFriend implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    @TableField("user_id")
    private Long userId;
    
    @TableField("friend_user_id")
    private Long friendUserId;
    
    @TableField("remark")
    private String remark;
    
    @TableField("group_name")
    private String groupName;
    
    @TableField("add_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime addTime;
    
    @TableField("status")
    private String status;
    
    @TableField("is_top")
    private Integer isTop;
    
    @TableField("is_muted")
    private Integer isMuted;
    
    @TableField("last_chat_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastChatTime;
}
