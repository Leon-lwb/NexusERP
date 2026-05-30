package com.nexus.erp.bd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.nexus.common.tenant.core.TenantEntity;

/**
 * 客户信用对象 bd_client_credit
 *
 * @author nexus
 */
@Data
@TableName("bd_client_credit")
public class BdClientCredit extends TenantEntity {

    /**
     * 信用ID
     */
    @TableId(value = "credit_id")
    private Long creditId;

    /**
     * 客户ID
     */
    private Long clientId;

    /**
     * 客户编码
     */
    private String clientCode;

    /**
     * 客户名称
     */
    private String clientName;

    /**
     * 信用额度
     */
    private Integer clientCredit;

    /**
     * 信用期限(天)
     */
    private Integer clientTime;

    /**
     * 客户等级
     */
    private String clientLevel;

    /**
     * 信用评分
     */
    private Integer clientScore;

    /**
     * 预留字段1
     */
    private String attr1;

    /**
     * 预留字段2
     */
    private String attr2;

    /**
     * 预留字段3
     */
    private Long attr3;

    /**
     * 预留字段4
     */
    private Long attr4;
}
