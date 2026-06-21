package com.nexus.mes.pro.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_mbom")
public class MesMbom extends TenantEntity {
    @TableId(value = "mbom_id")
    private Long mbomId;
    private Long bomId;
    private Long routingId;
    private String mbomVersion;
    private String mbomStatus;  // DRAFT/ACTIVE/FROZEN
    private String remark;
    @TableLogic
    private String delFlag;
}
