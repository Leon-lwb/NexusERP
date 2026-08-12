package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import com.alibaba.fastjson2.annotation.JSONField;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmMiscIssue;
import com.nexus.erp.wm.domain.WmMiscIssueLine;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 杂项出库单视图对象 wm_misc_issue
 *
 * @author nexus
 */
@Data
@AutoMapper(target = WmMiscIssue.class)
public class WmMiscIssueVo implements Serializable {

    /**
     * 出库单ID
     */
    @ExcelProperty("杂项出库ID")
    private Long issueId;

    /**
     * 出库单号
     */
    @ExcelProperty("出库单号")
    private String issueCode;

    /**
     * 出库类型
     */
    @ExcelProperty("出库类型")
    private String issueType;

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
     * 出库日期
     */
    @ExcelProperty("出库日期")
    private Date issueDate;

    /**
     * 状态
     */
    @ExcelProperty("状态")
    private String status;

    /**
     * 备注
     */
    @ExcelProperty("备注")
    private String remark;

    /**
     * 杂项出库单行项目列表
     */
    @JSONField(serialize = false)
    private List<WmMiscIssueLine> wmMiscIssueLineList;
}
