package com.nexus.erp.bd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.bd.domain.BdProcess;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工序定义视图对象 bd_process
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BdProcess.class)
public class BdProcessVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 工序ID
     */
    @ExcelProperty(value = "工序ID")
    private Long processId;

    /**
     * 工序编码
     */
    @ExcelProperty(value = "工序编码")
    private String processCode;

    /**
     * 工序名称
     */
    @ExcelProperty(value = "工序名称")
    private String processName;

    /**
     * 工序描述
     */
    @ExcelProperty(value = "工序描述")
    private String processDesc;

    /**
     * 工序类别
     */
    @ExcelProperty(value = "工序类别")
    private String processCategory;

    /**
     * 默认工作中心ID
     */
    private Long workstationId;

    /**
     * 默认工作中心编码
     */
    @ExcelProperty(value = "工作中心编码")
    private String workstationCode;

    /**
     * 默认工作中心名称
     */
    @ExcelProperty(value = "工作中心名称")
    private String workstationName;

    /**
     * 标准工时(小时/件)
     */
    @ExcelProperty(value = "标准工时")
    private BigDecimal standardHours;

    /**
     * 准备工时(小时)
     */
    @ExcelProperty(value = "准备工时")
    private BigDecimal setupHours;

    /**
     * 标准人工数
     */
    @ExcelProperty(value = "标准人工数")
    private Integer laborCount;

    /**
     * 标准机台数
     */
    @ExcelProperty(value = "标准机台数")
    private Integer machineCount;

    /**
     * 是否关键工序
     */
    @ExcelProperty(value = "是否关键工序")
    private Integer isKeyProcess;

    /**
     * 是否委外工序
     */
    @ExcelProperty(value = "是否委外工序")
    private Integer isOutsource;

    /**
     * 是否需要质检
     */
    @ExcelProperty(value = "是否需要质检")
    private Integer qualityRequired;

    /**
     * 状态
     */
    @ExcelProperty(value = "状态")
    private Long status;

    /**
     * 备注
     */
    @ExcelProperty(value = "备注")
    private String remark;

    /**
     * 创建时间
     */
    @ExcelProperty(value = "创建时间")
    private LocalDateTime createTime;
}
