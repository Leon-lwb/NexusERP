package org.dromara.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import com.alibaba.fastjson.annotation.JSONField;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.erp.wm.domain.WmMiscRecpt;
import org.dromara.erp.wm.domain.WmMiscRecptLine;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@AutoMapper(target = WmMiscRecpt.class)
public class WmMiscRecptVo implements Serializable {

    @ExcelProperty("杂项入库ID")
    private Long recptId;

    @ExcelProperty("入库单号")
    private String recptCode;

    @ExcelProperty("入库类型")
    private String recptType;

    @ExcelProperty("仓库ID")
    private Long warehouseId;

    @ExcelProperty("仓库编码")
    private String warehouseCode;

    @ExcelProperty("仓库名称")
    private String warehouseName;

    @ExcelProperty("库区ID")
    private Long areaId;

    @ExcelProperty("库区编码")
    private String areaCode;

    @ExcelProperty("库区名称")
    private String areaName;

    @ExcelProperty("入库日期")
    private Date recptDate;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("备注")
    private String remark;

    @JSONField(serialize = false)
    private List<WmMiscRecptLine> wmMiscRecptLineList;
}
