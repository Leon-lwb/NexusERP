package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmTransfer;
import com.nexus.erp.wm.domain.WmTransferLine;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 库存调拨单视图对象 wm_transfer
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmTransfer.class)
public class WmTransferVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 调拨单ID
     */
    @ExcelProperty(value = "调拨单ID")
    private Long transferId;

    /**
     * 调拨单编号
     */
    @ExcelProperty(value = "调拨单编号")
    private String transferNo;

    /**
     * 来源仓库
     */
    @ExcelProperty(value = "来源仓库")
    private String fromWarehouseName;

    private Long fromWarehouseId;
    private String fromWarehouseCode;
    private Long fromAreaId;
    private String fromAreaCode;
    private String fromAreaName;

    /**
     * 目标仓库
     */
    @ExcelProperty(value = "目标仓库")
    private String toWarehouseName;

    private Long toWarehouseId;
    private String toWarehouseCode;
    private Long toAreaId;
    private String toAreaCode;
    private String toAreaName;

    /**
     * 调拨类型
     */
    @ExcelProperty(value = "调拨类型")
    private String transferType;

    /**
     * 总数量
     */
    @ExcelProperty(value = "总数量")
    private BigDecimal totalQuantity;

    /**
     * 状态
     */
    @ExcelProperty(value = "状态")
    private String status;

    /**
     * 调拨日期
     */
    @ExcelProperty(value = "调拨日期")
    private Date transferDate;

    /**
     * 执行日期
     */
    @ExcelProperty(value = "执行日期")
    private Date executeDate;

    /**
     * 关联流程ID
     */
    private Long selectFlowId;

    /**
     * 操作人名称
     */
    private String operatorName;

    /**
     * 备注
     */
    private String remark;

    /**
     * 调拨单行项目列表
     */
    private List<WmTransferLine> wmTransferLineList;
}
