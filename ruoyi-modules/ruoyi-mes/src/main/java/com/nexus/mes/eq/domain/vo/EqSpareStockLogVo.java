package com.nexus.mes.eq.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.eq.domain.EqSpareStockLog;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * MES备件库存流水视图对象 eq_spare_stock_log
 *
 * <p>属于MES 设备管理模块，备件库存流水视图对象（查询出参）。</p>
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EqSpareStockLog.class)
public class EqSpareStockLogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 流水ID */
    private Long logId;

    /** 备件ID */
    private Long spareId;

    /** 备件编码 */
    @ExcelProperty("备件编码")
    private String spareCode;

    /** 备件名称 */
    @ExcelProperty("备件名称")
    private String spareName;

    /** 流水类型 */
    @ExcelProperty("类型")
    private String transactionType;

    /** 变动数量 */
    @ExcelProperty("数量")
    private BigDecimal quantity;

    /** 变动前库存 */
    private BigDecimal beforeQty;

    /** 变动后库存 */
    private BigDecimal afterQty;

    /** 关联单据类型 */
    private String refType;

    /** 关联单据ID */
    private Long refId;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 */
    @ExcelProperty("操作人")
    private String operatorName;

    /** 操作时间 */
    @ExcelProperty("操作时间")
    private Date operateTime;

    /** 备注 */
    private String remark;
}
