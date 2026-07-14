package com.nexus.mes.eq.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.eq.domain.EqPatrolRecord;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EqPatrolRecord.class)
public class EqPatrolRecordVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long recordId;
    private Long taskId;
    private Long routeId;
    private Long equipmentId;
    @ExcelProperty("设备编码")
    private String equipmentCode;
    @ExcelProperty("设备名称")
    private String equipmentName;
    @ExcelProperty("检查项目")
    private String checkItem;
    @ExcelProperty("实际值")
    private String actualValue;
    @ExcelProperty("结果")
    private String result;
    private String photoOssId;
    @ExcelProperty("执行人")
    private String executorName;
    @ExcelProperty("执行时间")
    private Date executeTime;
    private String remark;
}
