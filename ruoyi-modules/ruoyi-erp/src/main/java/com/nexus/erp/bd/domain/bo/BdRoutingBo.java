package com.nexus.erp.bd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.bd.domain.BdRouting;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 工艺路线业务对象 bd_routing
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BdRouting.class, reverseConvertGenerate = false)
public class BdRoutingBo extends BaseEntity {

    private Long routingId;

    @NotBlank(message = "工艺路线编码不能为空")
    private String routingCode;

    @NotBlank(message = "工艺路线名称不能为空")
    private String routingName;

    private String routingDesc;

    private Long materialId;

    private String materialCode;

    private String materialName;

    private String routingVersion;

    private Long isCurrent;

    private Integer totalSteps;

    private BigDecimal totalHours;

    private Date effectiveDate;

    private Date expiryDate;

    private Long status;

    private String remark;

    /**
     * 工序步骤列表(随工艺路线一起保存:先删后插)
     */
    private List<BdRoutingStepBo> steps;

    private Long[] ids;
}
