package com.nexus.erp.prd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.prd.domain.PrdSubcontract;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 委外订单业务对象
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PrdSubcontract.class, reverseConvertGenerate = false)
public class PrdSubcontractBo extends BaseEntity {

    private Long subcontractId;
    private String subcontractNo;
    private Long vendorId;
    private String vendorCode;
    private String vendorName;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String materialSpec;
    private String baseUomName;
    private BigDecimal plannedQty;
    private BigDecimal actualQty;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private Date planStartDate;
    private Date planEndDate;
    private Date actualStartDate;
    private Date actualEndDate;
    private Long woId;
    private String woNo;
    private String status;
    private String remark;
    private Long[] ids;
}
