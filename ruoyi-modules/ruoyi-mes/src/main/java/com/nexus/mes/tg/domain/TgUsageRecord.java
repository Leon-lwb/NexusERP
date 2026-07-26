package com.nexus.mes.tg.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES工装使用/领用记录对象 tg_usage_record
 * <p>合并原 tg_checkout_record: 领用=建记录(status=OUT), 归还=更新(status=RETURNED + 写 usage_count/hours)</p>
 * <p>状态: OUT(领出) / RETURNED(已归还)</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tg_usage_record")
public class TgUsageRecord extends TenantEntity {

    /** 使用记录ID(主键) */
    @TableId(value = "usage_id")
    private Long usageId;

    /** 工装ID */
    private Long toolingId;

    /** 工单ID */
    private Long woId;

    /** 工单编号(冗余) */
    private String woNo;

    /** 工位ID */
    private Long workstationId;

    /** 工位名称(冗余) */
    private String workstationName;

    /** 操作员ID */
    private Long operatorId;

    /** 操作员姓名(冗余) */
    private String operatorName;

    /** 领用时间 */
    private Date checkoutTime;

    /** 归还时间 */
    private Date returnTime;

    /** 使用次数 */
    private Integer usageCount;

    /** 使用时长 */
    private BigDecimal usageHours;

    /** 状态: OUT/RETURNED */
    private String status;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
