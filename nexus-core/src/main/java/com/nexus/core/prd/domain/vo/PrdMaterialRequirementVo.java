
package com.nexus.core.prd.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物料清单对象 prd_material_requirement vo
 *
 * @author nexus
 * @date 2025-11-21
 */
@Data
public class PrdMaterialRequirementVo extends BasePageDtoRequest {

    /**
     * 物料需求单ID
     */
    private Long requirementId;
    /**
     * 物料需求单编号
     */
    private String requirementNo;
    /**
     * 生产计划ID
     */
    private Long planId;
    /**
     * 生产计划编号
     */
    private String planNo;
    /**
     * BOM ID
     */
    private Long bomId;
    /**
     * 需求日期
     */
    private LocalDateTime requirementDate;
    /**
     * 物料种类总数
     */
    private Long totalMaterialTypes;
    /**
     * 需求总量
     */
    private BigDecimal totalRequiredQuantity;
    /**
     * 状态
     */
    private String status;
    /**
     * 计划员ID
     */
    private Long plannerId;
    /**
     * 计划员
     */
    private String plannerName;
    /**
     * 部门ID
     */
    private Long departmentId;
    /**
     * 部门
     */
    private String departmentName;

}