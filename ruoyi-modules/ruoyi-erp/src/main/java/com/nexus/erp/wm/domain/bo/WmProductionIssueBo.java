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

/**
 * 生产领料单业务对象 wm_production_issue
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmProductionIssue.class)
public class WmProductionIssueBo extends BaseEntity {

    /**
     * 生产领料单ID
     */
    @NotNull(message = "生产领料ID不能为空", groups = {EditGroup.class})
    private Long issueId;

    /**
     * 领料单号
     */
    @NotBlank(message = "领料单号不能为空", groups = {AddGroup.class, EditGroup.class})
    private String issueCode;

    /**
     * 关联工单ID
     */
    private Long woId;

    /**
     * 工单编号
     */
    private String woNo;

    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 仓库编码
     */
    private String warehouseCode;

    /**
     * 仓库名称
     */
    private String warehouseName;

    /**
     * 领料日期
     */
    private Date issueDate;

    /**
     * 状态
     */
    private String status;

    /**
     * 领料类型(NORMAL普通/BACKFLUSH倒冲)
     */
    private String issueType;

    /**
     * 领料人ID
     */
    private Long issuerId;

    /**
     * 领料人名称
     */
    private String issuerName;

    /**
     * 总数量
     */
    private BigDecimal totalQty;

    /**
     * 总金额
     */
    private BigDecimal totalAmount;

    /**
     * 备注
     */
    private String remark;

    /**
     * 预留字段1
     */
    private String attr1;

    /**
     * 预留字段2
     */
    private String attr2;

    /**
     * 预留字段3
     */
    private Long attr3;

    /**
     * 预留字段4
     */
    private Long attr4;

    /**
     * 生产领料单行项目列表
     */
    private List<WmProductionIssueLine> wmProductionIssueLineList;
}
