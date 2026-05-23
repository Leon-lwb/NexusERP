package org.dromara.erp.pu.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.idev.excel.annotation.ExcelProperty;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.erp.pu.domain.PuPurchaseOrder;
import org.dromara.erp.pu.domain.PuPurchaseOrderLine;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 采购订单视图对象 VO
 * <p>
 * 用于采购订单的前端展示和导出，通过 {@link ExcelProperty} 注解定义导出列映射。
 * 与 {@link PuPurchaseOrder} 实体通过 MapStruct 自动映射转换。
 * 包含订单头展示信息和明细行列表 {@link #lineList}。
 *
 * @author ruoyi
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PuPurchaseOrder.class)
public class PuPurchaseOrderVo extends BaseEntity {

    /** 采购订单ID */
    private Long orderId;

    @ExcelProperty(value = "采购订单号")
    private String orderNo;

    @ExcelProperty(value = "供应商")
    private String vendorName;

    @ExcelProperty(value = "来源请购单号")
    private String requisitionNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "订单日期")
    private Date orderDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "预计到货日期")
    private Date expectedArrivalDate;

    @ExcelProperty(value = "状态")
    private String status;

    @ExcelProperty(value = "金额")
    private BigDecimal totalAmount;

    /** 备注 */
    private String remark;

    /** 采购订单明细行列表 */
    private List<PuPurchaseOrderLine> lineList;
}
