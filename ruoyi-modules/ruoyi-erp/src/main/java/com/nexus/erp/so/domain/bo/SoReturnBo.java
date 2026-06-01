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

/**
 * 销售退货业务对象 so_return
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SoReturn.class)
public class SoReturnBo extends BaseEntity {

    /** 退货ID */
    @NotNull(message = "退货ID不能为空", groups = {EditGroup.class})
    private Long returnId;

    /** 退货单号 */
    @NotBlank(message = "退货单号不能为空", groups = {AddGroup.class, EditGroup.class})
    private String returnNo;

    /** 销售订单ID */
    private Long soId;
    /** 销售订单号 */
    private String soNo;
    /** 客户ID */
    private Long clientId;
    /** 客户名称 */
    private String clientName;
    /** 销售代表ID */
    private Long salesRepId;
    /** 销售代表名称 */
    private String salesRepName;
    /** 退货日期 */
    private Date returnDate;
    /** 退货原因 */
    private String returnReason;
    /** 退货类型 */
    private String returnType;
    /** 总金额 */
    private BigDecimal totalAmount;
    /** 税额 */
    private BigDecimal taxAmount;
    /** 退款金额 */
    private BigDecimal refundAmount;
    /** 仓库ID */
    private Long warehouseId;
    /** 仓库编码 */
    private String warehouseCode;
    /** 仓库名称 */
    private String warehouseName;
    /** 状态 */
    private String status;
    /** 审批流程ID */
    private Long selectFlowId;
    /** 备注 */
    private String remark;
    /** 扩展字段1 */
    private String attr1;
    /** 扩展字段2 */
    private String attr2;
    /** 扩展字段3 */
    private Long attr3;
    /** 扩展字段4 */
    private Long attr4;

    /** 退货明细行列表 */
    private List<SoReturnLine> soReturnLineList;
}
