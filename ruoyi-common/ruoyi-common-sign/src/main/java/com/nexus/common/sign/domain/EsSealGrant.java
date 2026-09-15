package com.nexus.common.sign.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.util.Date;

/**
 * 印章使用授权。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("es_seal_grant")
public class EsSealGrant extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 授权ID */
    @TableId(value = "grant_id")
    private Long grantId;

    /** 印章ID */
    private Long sealId;

    /** 被授权用户ID(用户级授权) */
    private Long granteeUserId;

    /** 被授权部门ID(部门级授权) */
    private Long granteeDeptId;

    /** 限定业务场景(NULL=不限) */
    private String bizType;

    /** 可否单独用章(0否 1是) */
    private String canSignAlone;

    /** 授权生效时间 */
    private Date validFrom;

    /** 授权失效时间 */
    private Date validTo;

    /** 状态(0停用 1启用) */
    private String status;

    /** 授权人用户ID */
    private Long grantBy;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
