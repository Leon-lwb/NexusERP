package com.nexus.erp.so.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import com.alibaba.fastjson.annotation.JSONField;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.so.domain.SoReturn;
import com.nexus.erp.so.domain.SoReturnLine;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 销售退货视图对象 so_return
 *
 * @author ruoyi
 */
@Data
@AutoMapper(target = SoReturn.class)
public class SoReturnVo implements Serializable {

    /** 退货ID */
    @ExcelProperty("退货ID")
    private Long returnId;

    /** 退货单号 */
    @ExcelProperty("退货单号")
    private String returnNo;

    /** 销售订单号 */
    @ExcelProperty("销售订单号")
    private String soNo;

    /** 客户名称 */
    @ExcelProperty("客户名称")
    private String clientName;

    /** 退货日期 */
    @ExcelProperty("退货日期")
    private Date returnDate;

    /** 退货原因 */
    @ExcelProperty("退货原因")
    private String returnReason;

    /** 退货类型 */
    @ExcelProperty("退货类型")
    private String returnType;

    /** 总金额 */
    @ExcelProperty("总金额")
    private BigDecimal totalAmount;

    /** 退款金额 */
    @ExcelProperty("退款金额")
    private BigDecimal refundAmount;

    /** 状态 */
    @ExcelProperty("状态")
    private String status;

    /** 审批流程ID */
    private Long selectFlowId;

    /** 备注 */
    @ExcelProperty("备注")
    private String remark;

    /** 退货明细行列表（不参与序列化） */
    @JSONField(serialize = false)
    private List<SoReturnLine> soReturnLineList;
}
