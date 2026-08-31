package com.nexus.common.mq.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 死信记录
 *
 * @author Nexus
 */
@Data
@TableName("mq_dead_letter_record")
public class DeadLetterRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String messageId;
    private String exchange;
    private String routingKey;
    private String messageBody;
    private String errorMessage;
    private Integer retryCount;
    private String status;
    private Date createTime;
}
