package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_production_recpt")
public class WmProductionRecpt extends TenantEntity {

    @TableId(value = "recpt_id")
    private Long recptId;

    private String recptCode;
    private Long woId;
    private String woNo;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String materialSpec;
    private BigDecimal quantity;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private Long locationId;
    private String locationCode;
    private String locationName;
    private Date recptDate;
    private String status;
    private String remark;

    @TableLogic
    private String delFlag;
}
