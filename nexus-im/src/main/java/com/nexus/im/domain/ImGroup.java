package com.nexus.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IM群组实体
 *
 * @author nexus
 */
@Data
@TableName("im_group")
public class ImGroup {
    
    @TableId(value = "group_id", type = IdType.INPUT)
    private String groupId;
    
    @TableField("group_name")
    private String groupName;
    
    @TableField("avatar")
    private String avatar;
    
    @TableField("description")
    private String description;
    
    @TableField("owner_id")
    private Long ownerId;
    
    @TableField("group_type")
    private String groupType;
    
    @TableField("max_members")
    private Integer maxMembers;
    
    @TableField("member_count")
    private Integer memberCount;
    
    @TableField("status")
    private String status;
    
    @TableField("allow_invite")
    private Integer allowInvite;
    
    @TableField("allow_member_modify")
    private Integer allowMemberModify;
    
    @TableField("allow_member_invite")
    private Integer allowMemberInvite;
    
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    
    @TableField("update_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
    
    @TableField(exist = false)
    private List<ImGroupMember> members;
}
