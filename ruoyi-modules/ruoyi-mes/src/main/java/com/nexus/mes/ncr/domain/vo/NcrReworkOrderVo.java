package com.nexus.mes.ncr.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.ncr.domain.NcrReworkOrder;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * MES NCR返工单视图对象 ncr_rework_order
 * <p>属于 MES NCR 不合格品管理模块，作为 NCR 返工单关联的查询/导出返回结构。</p>
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = NcrReworkOrder.class)
public class NcrReworkOrderVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 返工记录ID */
    @ExcelProperty("返工记录ID")
    private Long reworkId;

    /** 返工单编号 */
    @ExcelProperty("返工单编号")
    private String reworkCode;

    /** NCR单ID */
    private Long ncrId;

    /** 原始工单ID */
    private Long originalWoId;

    /** 原始工单编号 */
    @ExcelProperty("原始工单")
    private String originalWoNo;

    /** 返工工单ID */
    private Long reworkWoId;

    /** 返工工单编号 */
    @ExcelProperty("返工工单")
    private String reworkWoNo;

    /** 返工数量 */
    @ExcelProperty("返工数量")
    private BigDecimal reworkQty;

    /** 返工原因 */
    @ExcelProperty("返工原因")
    private String reworkReason;

    /** 状态 */
    @ExcelProperty("状态")
    private String status;
}