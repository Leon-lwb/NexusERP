package com.nexus.common.mq.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 消息记录
 *
 * @author Nexus
 */
@Data
@TableName("mq_message_record")
public class MessageRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String messageId;
    private String exchange;
    private String routingKey;
    private String messageBody;
    /** 消息类型全限定名(补偿重投时类型化反序列化用; 历史数据为空则无法补偿) */
    private String messageType;
    private String status;
    private Integer retryCount;
    private Integer maxRetry;
    private Date nextRetryTime;
    private Date createTime;
    private Date updateTime;
}
