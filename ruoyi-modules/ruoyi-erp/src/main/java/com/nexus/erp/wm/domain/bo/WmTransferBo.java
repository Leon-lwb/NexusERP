package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmTransfer;
import com.nexus.erp.wm.domain.WmTransferLine;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmTransfer.class, reverseConvertGenerate = false)
public class WmTransferBo extends BaseEntity {

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
    private Long selectFlowId;
    private String remark;
    private String delFlag;

    private List<WmTransferLine> wmTransferLineList;
}
