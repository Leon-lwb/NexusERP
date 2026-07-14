package com.nexus.mes.eq.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.eq.domain.EqPatrolTask;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EqPatrolTask.class)
public class EqPatrolTaskVo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long taskId;
    private Long routeId;
    @ExcelProperty("路线名称")
    private String routeName;
    @ExcelProperty("计划日期")
    private Date planDate;
    private Date actualDate;
    @ExcelProperty("指派人")
    private String assignedToName;
    @ExcelProperty("状态")
    private String status;
    private String remark;
}
