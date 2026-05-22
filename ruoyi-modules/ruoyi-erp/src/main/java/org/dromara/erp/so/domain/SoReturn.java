package org.dromara.erp.so.domain;

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

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("so_return")
public class SoReturn extends TenantEntity {

    @TableId(value = "return_id")
    private Long returnId;

    private String returnNo;
    private Long soId;
    private String soNo;
    private Long clientId;
    private String clientName;
    private Long salesRepId;
    private String salesRepName;
    private Date returnDate;
    private String returnReason;
    private String returnType;
    private BigDecimal totalAmount;
    private BigDecimal taxAmount;
    private BigDecimal refundAmount;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private String status;
    private Long selectFlowId;
    private String remark;
    private String attr1;
    private String attr2;
    private Long attr3;
    private Long attr4;

    @TableField(exist = false)
    private List<SoReturnLine> soReturnLineList;

    @TableLogic
    private String delFlag;
}
