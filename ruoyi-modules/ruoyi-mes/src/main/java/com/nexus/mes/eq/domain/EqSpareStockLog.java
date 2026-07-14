package com.nexus.mes.eq.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES备件库存流水对象 eq_spare_stock_log
 * <p>
 * 记录每次出入库变动, 维护 eq_spare_part.current_stock。
 * transaction_type: IN(入库) / OUT(出库) / REPAIR_USE(维修领用)。
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eq_spare_stock_log")
public class EqSpareStockLog extends TenantEntity {

    /** 流水ID(主键) */
    @TableId(value = "log_id")
    private Long logId;

    /** 备件ID */
    private Long spareId;

    /** 备件编码(冗余) */
    private String spareCode;

    /** 备件名称(冗余) */
    private String spareName;

    /** 流水类型: IN/OUT/REPAIR_USE */
    private String transactionType;

    /** 变动数量(正数) */
    private BigDecimal quantity;

    /** 变动前库存 */
    private BigDecimal beforeQty;

    /** 变动后库存 */
    private BigDecimal afterQty;

    /** 关联单据类型 MANUAL/REPAIR */
    private String refType;

    /** 关联单据ID */
    private Long refId;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 操作时间 */
    private Date operateTime;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
