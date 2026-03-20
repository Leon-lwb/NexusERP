package com.nexus.im.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * IM群组成员实体
 *
 * @author nexus
 */
@Data
@TableName("im_group_member")
public class ImGroupMember implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId("id")
    private Long id;
    
    @TableField("group_id")
    private String groupId;
    
    @TableField("user_id")
    private Long userId;
    
    @TableField("group_nickname")
    private String groupNickname;
    
    @TableField("role")
    private String role;
    
    @TableField("join_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime joinTime;
    
    @TableField("last_speak_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastSpeakTime;
    
    @TableField("is_muted")
    private Integer isMuted;
    
    @TableField("mute_end_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime muteEndTime;
    
    @TableField("enabled")
    private Integer enabled;
}
