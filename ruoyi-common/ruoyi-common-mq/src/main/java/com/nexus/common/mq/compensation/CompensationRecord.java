package com.nexus.common.mq.compensation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/** 补偿记录，映射mq_compensation_record表
 *
 * @author Nexus
 */
@Data
@TableName("mq_compensation_record")
public class CompensationRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 关联消息ID */
    private String messageId;
    /** 业务类型 */
    private String businessType;
    /** 业务ID */
    private String businessId;
    /** 补偿状态: PENDING/SUCCESS/FAILED */
    private String compensationStatus;
    /** 补偿方法(全限定类名.方法名) */
    private String compensationMethod;
    /** 补偿参数JSON */
    private String compensationParams;
    /** 已重试次数 */
    private Integer retryCount;
    /** 最大重试次数 */
    private Integer maxRetry;
    /** 下次重试时间 */
    private Date nextRetryTime;
    /** 创建时间 */
    private Date createTime;
    /** 更新时间 */
    private Date updateTime;
}
