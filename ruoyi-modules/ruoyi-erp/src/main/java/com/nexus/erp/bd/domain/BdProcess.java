package com.nexus.erp.bd.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

/**
 * 工序定义对象 bd_process
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("bd_process")
public class BdProcess extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 工序ID
     */
    @TableId(value = "process_id")
    private Long processId;

    /**
     * 工序编码
     */
    @ExcelProperty("工序编码")
    private String processCode;

    /**
     * 工序名称
     */
    @ExcelProperty("工序名称")
    private String processName;

    /**
     * 工序描述
     */
    @ExcelProperty("工序描述")
    private String processDesc;

    /**
     * 工序类别: PROCESSING-加工, ASSEMBLY-装配, INSPECTION-检验, PACKAGING-包装
     */
    @ExcelProperty("工序类别")
    private String processCategory;

    /**
     * 默认工作中心ID
     */
    @ExcelProperty("默认工作中心ID")
    private Long workstationId;

    /**
     * 默认工作中心编码
     */
    @ExcelProperty("默认工作中心编码")
    private String workstationCode;

    /**
     * 默认工作中心名称
     */
    @ExcelProperty("默认工作中心名称")
    private String workstationName;

    /**
     * 标准工时(小时/件)
     */
    @ExcelProperty("标准工时(小时/件)")
    private BigDecimal standardHours;

    /**
     * 准备工时(小时)
     */
    @ExcelProperty("准备工时(小时)")
    private BigDecimal setupHours;

    /**
     * 标准人工数
     */
    @ExcelProperty("标准人工数")
    private Integer laborCount;

    /**
     * 标准机台数
     */
    @ExcelProperty("标准机台数")
    private Integer machineCount;

    /**
     * 是否关键工序
     */
    @ExcelProperty("是否关键工序")
    private Integer isKeyProcess;

    /**
     * 是否委外工序
     */
    @ExcelProperty("是否委外工序")
    private Integer isOutsource;

    /**
     * 是否需要质检
     */
    @ExcelProperty("是否需要质检")
    private Integer qualityRequired;

    /**
     * 状态
     */
    @ExcelProperty("状态")
    private Long status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 预留字段1
     */
    private String attr1;

    /**
     * 预留字段2
     */
    private String attr2;

    /**
     * 预留字段3
     */
    private Long attr3;

    /**
     * 预留字段4
     */
    private Long attr4;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}
