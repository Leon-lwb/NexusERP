package com.nexus.erp.bd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.bd.domain.BdProcess;

import java.math.BigDecimal;

/**
 * 工序定义业务对象 bd_process
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BdProcess.class, reverseConvertGenerate = false)
public class BdProcessBo extends BaseEntity {

    /**
     * 工序ID
     */
    private Long processId;

    /**
     * 工序编码
     */
    @NotBlank(message = "工序编码不能为空")
    private String processCode;

    /**
     * 工序名称
     */
    @NotBlank(message = "工序名称不能为空")
    private String processName;

    /**
     * 工序描述
     */
    private String processDesc;

    /**
     * 工序类别: PROCESSING-加工, ASSEMBLY-装配, INSPECTION-检验, PACKAGING-包装
     */
    private String processCategory;

    /**
     * 默认工作中心ID
     */
    private Long workstationId;

    /**
     * 默认工作中心编码
     */
    private String workstationCode;

    /**
     * 默认工作中心名称
     */
    private String workstationName;

    /**
     * 标准工时(小时/件)
     */
    private BigDecimal standardHours;

    /**
     * 准备工时(小时)
     */
    private BigDecimal setupHours;

    /**
     * 标准人工数
     */
    private Integer laborCount;

    /**
     * 标准机台数
     */
    private Integer machineCount;

    /**
     * 是否关键工序
     */
    private Integer isKeyProcess;

    /**
     * 是否委外工序
     */
    private Integer isOutsource;

    /**
     * 是否需要质检
     */
    private Integer qualityRequired;

    /**
     * 状态
     */
    private Long status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;
}
