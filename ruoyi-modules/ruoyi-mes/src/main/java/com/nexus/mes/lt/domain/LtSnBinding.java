package com.nexus.mes.lt.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * MES SN批次绑定关系对象 lt_sn_binding
 * <p>属于 MES 批次管理模块，表达 SN 与批次 的绑定关系。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lt_sn_binding")
public class LtSnBinding extends TenantEntity {

    /** 绑定ID(主键) */
    @TableId(value = "binding_id")
    private Long bindingId;

    /** SN ID */
    private Long snId;

    /** SN编码(冗余) */
    private String snCode;

    /** 批次ID */
    private Long lotId;

    /** 批次编码(冗余) */
    private String lotCode;

    /** 物料ID */
    private Long materialId;

    /** 绑定时间 */
    private Date boundTime;

    /** 绑定人ID */
    private Long boundBy;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
