package com.nexus.core.prd.domain;

    import java.math.BigDecimal;
    import java.util.Date;
    import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 物料需求单明细对象 prd_material_requirement_detail
 *
 * @author nexus
 * @date 2025-11-21
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.prd.mapper.PrdMaterialRequirementDetailMapper.PrdMaterialRequirementDetailResult")
public class PrdMaterialRequirementDetail extends BaseEntity {
private static final long serialVersionUID = 1L;

    /** 需求明细ID */
        @TableId(value = "detail_id", type = IdType.AUTO)
    private Long detailId;

    /** 物料需求单ID */
            @Excel(name = "物料需求单ID")
    private Long requirementId;

    /** 物料ID */
            @Excel(name = "物料ID")
    private Long materialId;

    /** 物料编码 */
            @Excel(name = "物料编码")
    private String materialCode;

    /** 物料名称 */
            @Excel(name = "物料名称")
    private String materialName;

    /** 物料规格 */
            @Excel(name = "物料规格")
    private String materialSpec;

    /** 单位 */
            @Excel(name = "单位")
    private String unit;

    /** BOM标准用量 */
            @Excel(name = "BOM标准用量")
    private BigDecimal bomQuantity;

    /** 损耗率(%) */
            @Excel(name = "损耗率(%)")
    private BigDecimal lossRate;

    /** 需求数量 */
            @Excel(name = "需求数量")
    private BigDecimal requiredQuantity;

    /** 已发料数量 */
            @Excel(name = "已发料数量")
    private BigDecimal issuedQuantity;

    /** 当前库存数量 */
            @Excel(name = "当前库存数量")
    private BigDecimal stockQuantity;

    /** 缺料数量 */
            @Excel(name = "缺料数量")
    private BigDecimal shortageQuantity;

    /** 工序步骤 */
            @Excel(name = "工序步骤")
    private Long operationStep;

    /** 工作中心ID */
            @Excel(name = "工作中心ID")
    private Long workstationId;

    /** 工作中心名称 */
            @Excel(name = "工作中心名称")
    private String workstationName;

    /** 提前期(小时) */
            @Excel(name = "提前期(小时)")
    private Long leadTime;

    /** 需求日期 */
            @JsonFormat(pattern = "yyyy-MM-dd")
            @Excel(name = "需求日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date requiredDate;

    /** 状态 */
            @Excel(name = "状态")
    private Long status;

}