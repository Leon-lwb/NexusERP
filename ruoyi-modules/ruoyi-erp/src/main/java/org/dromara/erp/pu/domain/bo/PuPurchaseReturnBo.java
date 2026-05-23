package org.dromara.erp.pu.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.erp.pu.domain.PuPurchaseReturn;
import org.dromara.erp.pu.domain.PuPurchaseReturnLine;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 采购退货单业务对象 BO
 * <p>
 * 用于采购退货单的前后端数据交互，承载新增、修改、查询等操作的请求参数。
 * 与 {@link PuPurchaseReturn} 实体通过 MapStruct 自动映射转换。
 * 包含退货单头信息和明细行列表 {@link #puPurchaseReturnLineList}。
 *
 * @author ruoyi
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PuPurchaseReturn.class)
public class PuPurchaseReturnBo extends BaseEntity {

    @NotNull(message = "采购退货ID不能为空")
    private Long returnId;

    @NotBlank(message = "退货单号不能为空")
    private String returnNo;

    /** 来源采购订单ID */
    private Long orderId;

    /** 来源采购订单号 */
    private String orderNo;

    /** 供应商ID */
    private Long vendorId;

    /** 供应商编码 */
    private String vendorCode;

    /** 供应商名称 */
    private String vendorName;

    /** 退货日期 */
    private Date returnDate;

    /** 退货原因 */
    private String returnReason;

    /** 退货总金额 */
    private BigDecimal totalAmount;

    /** 退货单状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 退货明细行列表 */
    private List<PuPurchaseReturnLine> puPurchaseReturnLineList;
}
