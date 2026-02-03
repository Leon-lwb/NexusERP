package com.nexus.system.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 用户公告阅读标记对象 sys_notice_send
 *
 * @author nbmade
 * @date 2023-09-21
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notice_send")
public class SysNoticeSend extends BaseEntity {

    private static final long serialVersionUID=1L;

    /**
     * 公告发送ID
     */
    @TableId(value = "send_id")
    private Long sendId;
    /**
     * 公告ID
     */
    private Long noticeId;
    /**
     * 用户id
     */
    private Long userId;
    /**
     * 阅读状态（0未读，1已读）
     */
    private String readFlag;
    /**
     * 阅读时间
     */
    private Date readTime;

}
