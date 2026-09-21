package com.nexus.mes.eq.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * MES备件退库单视图对象 eq_spare_return（MES-EQ-04 收口）
 * <p>详情接口会填充 {@link #lines}；列表接口为控制查询开销<b>不</b>填充明细。</p>
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
public class EqSpareReturnVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 退库单ID */
    private Long returnId;

    /** 退库单号 */
    @ExcelProperty("退库单号")
    private String returnNo;

    /** 退库目标 ERP 仓库ID */
    private Long warehouseId;

    /** 退库日期 */
    @ExcelProperty("退库日期")
    private Date returnDate;

    /** 退库类型 REPAIR_RETURN / EXCESS_RETURN / OTHER */
    @ExcelProperty("退库类型")
    private String returnType;

    /** 状态 DRAFT / CONFIRMED / CANCELLED */
    @ExcelProperty("状态")
    private String status;

    /** 明细数量合计 */
    @ExcelProperty("数量合计")
    private BigDecimal totalQty;

    /** 备注 */
    private String remark;

    /** 确认人ID */
    private Long confirmBy;

    /** 确认时间 */
    private Date confirmTime;

    /** 版本号 */
    private Integer version;

    /** 明细行(仅详情接口填充) */
    private List<EqSpareReturnLineVo> lines;
}
