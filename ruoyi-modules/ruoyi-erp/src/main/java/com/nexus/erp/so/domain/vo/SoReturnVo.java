package com.nexus.erp.so.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.so.domain.SoReturn;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 销售退货视图对象 so_return
 *
 * @author nexus
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

    /**
     * 退货明细行列表。
     * <p>用 {@link SoReturnLineVo} 而非实体：原实现直接返回实体，会把 {@code tenantId}/
     * {@code delFlag}/{@code createBy} 等审计字段一并暴露给前端；且原来的
     * {@code @JSONField(serialize = false)} 是 fastjson2 注解，本项目 HTTP 走 Jackson，
     * 该注解完全不生效（即便生效也会让退货表单读不到明细行）。</p>
     */
    private List<SoReturnLineVo> soReturnLineList;
}
