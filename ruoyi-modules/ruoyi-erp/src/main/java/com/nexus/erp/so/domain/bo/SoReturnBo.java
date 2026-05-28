package com.nexus.erp.so.domain.bo;

import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.so.domain.SoReturn;
import com.nexus.erp.so.domain.SoReturnLine;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SoReturn.class)
public class SoReturnBo extends BaseEntity {

    @NotNull(message = "退货ID不能为空", groups = {EditGroup.class})
    private Long returnId;

    @NotBlank(message = "退货单号不能为空", groups = {AddGroup.class, EditGroup.class})
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

    private List<SoReturnLine> soReturnLineList;
}
