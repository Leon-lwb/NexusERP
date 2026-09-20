package com.nexus.iot.forward.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * IoT数据转发投递留痕对象 iot_forward_log。
 * <p>
 * 回答"这条数据有没有转发出去、发到哪、为什么失败、重试了几次"。
 * <p>
 * 不继承 {@code TenantEntity}：本表无 create_by/create_dept/update_by 审计列
 * （投递发生在消费线程，无登录上下文），继承基类会触发不存在的列自动填充导致 INSERT 报错。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@TableName("iot_forward_log")
public class IotForwardLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日志ID
     */
    @TableId(value = "log_id")
    private Long logId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 转发规则ID
     */
    private Long ruleId;

    /**
     * 设备ID
     */
    private Long deviceId;

    /**
     * 设备编码(冗余)
     */
    private String deviceCode;

    /**
     * 属性编码
     */
    private String propertyCode;

    /**
     * 实际投递URL
     */
    private String targetUrl;

    /**
     * 投递载荷(JSON)
     */
    private String payload;

    /**
     * 状态: PENDING/SUCCESS/FAILED
     */
    private String status;

    /**
     * 已重试次数
     */
    private Integer retryCount;

    /**
     * HTTP响应码
     */
    private Integer httpStatus;

    /**
     * 失败原因
     */
    private String errorMsg;

    /**
     * 下次重试时间(空=不再重试)
     */
    private Date nextRetryTime;

    /**
     * 最近一次投递时间
     */
    private Date lastAttemptTime;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}
