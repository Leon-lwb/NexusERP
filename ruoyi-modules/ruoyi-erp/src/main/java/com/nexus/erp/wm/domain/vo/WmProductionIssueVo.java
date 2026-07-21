package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import com.alibaba.fastjson.annotation.JSONField;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.domain.WmProductionIssueLine;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 生产领料单视图对象 wm_production_issue
 *
 * @author nexus
 */
@Data
@AutoMapper(target = WmProductionIssue.class)
public class WmProductionIssueVo implements Serializable {

    /**
     * 生产领料单ID
     */
    @ExcelProperty("生产领料ID")
    private Long issueId;

    /**
     * 领料单号
     */
    @ExcelProperty("领料单号")
    private String issueCode;

    /**
     * 关联工单ID
     */
    @ExcelProperty("关联工单ID")
    private Long woId;

    /**
     * 工单编号
     */
    @ExcelProperty("工单编号")
    private String woNo;

    /**
     * 仓库ID
     */
    @ExcelProperty("仓库ID")
    private Long warehouseId;

    /**
     * 仓库编码
     */
    @ExcelProperty("仓库编码")
    private String warehouseCode;

    /**
     * 仓库名称
     */
    @ExcelProperty("仓库名称")
    private String warehouseName;

    /**
     * 领料日期
     */
    @ExcelProperty("领料日期")
    private Date issueDate;

    /**
     * 状态
     */
    @ExcelProperty("状态")
    private String status;

    /**
     * 领料类型
     */
    @ExcelProperty("领料类型")
    private String issueType;

    /**
     * 领料人ID
     */
    @ExcelProperty("领料人ID")
    private Long issuerId;

    /**
     * 领料人
     */
    @ExcelProperty("领料人")
    private String issuerName;

    /**
     * 总数量
     */
    @ExcelProperty("总数量")
    private BigDecimal totalQty;

    /**
     * 总金额
     */
    @ExcelProperty("总金额")
    private BigDecimal totalAmount;

    /**
     * 备注
     */
    @ExcelProperty("备注")
    private String remark;

    /**
     * 生产领料单行项目列表
     */
    @JSONField(serialize = false)
    private List<WmProductionIssueLine> wmProductionIssueLineList;
}
