package org.dromara.erp.pu.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.erp.pu.domain.PuPurchaseReturn;
import org.dromara.erp.pu.domain.PuPurchaseReturnLine;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 采购退货单视图对象 VO
 * <p>
 * 用于采购退货单的前端展示和导出，通过 {@link ExcelProperty} 注解定义导出列映射。
 * 与 {@link PuPurchaseReturn} 实体通过 MapStruct 自动映射转换。
 * 包含退货单头展示信息和明细行列表 {@link #puPurchaseReturnLineList}。
 *
 * @author ruoyi
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PuPurchaseReturn.class)
public class PuPurchaseReturnVo extends BaseEntity {

    /** 退货单ID */
    private Long returnId;

    @ExcelProperty(value = "退货单号")
    private String returnNo;

    @ExcelProperty(value = "采购订单号")
    private String orderNo;

    @ExcelProperty(value = "供应商")
    private String vendorName;

    @ExcelProperty(value = "退货日期")
    private Date returnDate;

    @ExcelProperty(value = "退货原因")
    private String returnReason;

    @ExcelProperty(value = "金额")
    private BigDecimal totalAmount;

    @ExcelProperty(value = "状态")
    private String status;

    /** 备注 */
    private String remark;

    /** 已选审批流程模板ID */
    private Long selectFlowId;

    /** 退货明细行列表 */
    private List<PuPurchaseReturnLine> puPurchaseReturnLineList;
}
