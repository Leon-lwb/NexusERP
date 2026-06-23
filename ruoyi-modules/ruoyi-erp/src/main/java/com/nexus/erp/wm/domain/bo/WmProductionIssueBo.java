package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.domain.WmProductionIssueLine;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmProductionIssue.class)
public class WmProductionIssueBo extends BaseEntity {

    @NotNull(message = "生产领料ID不能为空", groups = {EditGroup.class})
    private Long issueId;

    @NotBlank(message = "领料单号不能为空", groups = {AddGroup.class, EditGroup.class})
    private String issueCode;

    private Long woId;
    private String woNo;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private Date issueDate;
    private String status;
    /** 领料类型(NORMAL普通/BACKFLUSH倒冲) */
    private String issueType;
    /** 领料人ID */
    private Long issuerId;
    /** 领料人名称 */
    private String issuerName;
    /** 总数量 */
    private BigDecimal totalQty;
    /** 总金额 */
    private BigDecimal totalAmount;
    private String remark;
    private String attr1;
    private String attr2;
    private Long attr3;
    private Long attr4;

    private List<WmProductionIssueLine> wmProductionIssueLineList;
}
