package com.nexus.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * App用户协议同意记录对象 app_user_agreement
 *
 * @author nexus
 * @date 2026-08-05
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("app_user_agreement")
public class AppUserAgreement extends TenantEntity {

    /**
     * 主键(雪花ID)
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 协议标识(privacy_policy/user_service_agreement)
     */
    private String agreementKey;

    /**
     * 协议版本
     */
    private String agreementVersion;

    /**
     * 同意时间
     */
    private Date agreeTime;

}
