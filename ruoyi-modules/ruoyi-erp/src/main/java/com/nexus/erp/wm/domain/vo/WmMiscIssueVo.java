package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import com.alibaba.fastjson.annotation.JSONField;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmMiscIssue;
import com.nexus.erp.wm.domain.WmMiscIssueLine;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@AutoMapper(target = WmMiscIssue.class)
public class WmMiscIssueVo implements Serializable {

    @ExcelProperty("杂项出库ID")
    private Long issueId;

    @ExcelProperty("出库单号")
    private String issueCode;

    @ExcelProperty("出库类型")
    private String issueType;

    @ExcelProperty("仓库ID")
    private Long warehouseId;

    @ExcelProperty("仓库编码")
    private String warehouseCode;

    @ExcelProperty("仓库名称")
    private String warehouseName;

    @ExcelProperty("出库日期")
    private Date issueDate;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("备注")
    private String remark;

    @JSONField(serialize = false)
    private List<WmMiscIssueLine> wmMiscIssueLineList;
}
