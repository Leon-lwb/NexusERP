package org.dromara.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import com.alibaba.fastjson.annotation.JSONField;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.erp.wm.domain.WmProductionIssue;
import org.dromara.erp.wm.domain.WmProductionIssueLine;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@AutoMapper(target = WmProductionIssue.class)
public class WmProductionIssueVo implements Serializable {

    @ExcelProperty("生产领料ID")
    private Long issueId;

    @ExcelProperty("领料单号")
    private String issueCode;

    @ExcelProperty("关联工单ID")
    private Long woId;

    @ExcelProperty("工单编号")
    private String woNo;

    @ExcelProperty("仓库ID")
    private Long warehouseId;

    @ExcelProperty("仓库编码")
    private String warehouseCode;

    @ExcelProperty("仓库名称")
    private String warehouseName;

    @ExcelProperty("领料日期")
    private Date issueDate;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("备注")
    private String remark;

    @JSONField(serialize = false)
    private List<WmProductionIssueLine> wmProductionIssueLineList;
}
