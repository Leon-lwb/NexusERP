package org.dromara.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 库存调拨单对象 wm_transfer
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_transfer")
public class WmTransfer extends TenantEntity {

    @TableId(value = "transfer_id")
    private Long transferId;

    private String transferNo;
    private Long fromWarehouseId;
    private String fromWarehouseCode;
    private String fromWarehouseName;
    private Long fromAreaId;
    private String fromAreaCode;
    private String fromAreaName;
    private Long toWarehouseId;
    private String toWarehouseCode;
    private String toWarehouseName;
    private Long toAreaId;
    private String toAreaCode;
    private String toAreaName;
    private String transferType;
    private BigDecimal totalQuantity;
    private String status;
    private Long operatorId;
    private String operatorName;
    private Date transferDate;
    private Date executeDate;
    private String selectFlowId;
    private String remark;

    @TableLogic
    private String delFlag;

    @TableField(exist = false)
    private List<WmTransferLine> wmTransferLineList;
}
