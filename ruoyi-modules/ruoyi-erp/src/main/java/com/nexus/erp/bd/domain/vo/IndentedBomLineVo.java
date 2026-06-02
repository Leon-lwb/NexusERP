package com.nexus.erp.bd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 缩进式BOM行VO - 用于缩进式BOM表格展示
 *
 * @author nexus
 */
@Data
public class IndentedBomLineVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 层级 (0=顶层)
     */
    private Integer level;

    /**
     * 缩进字符串
     */
    private String indent;

    /**
     * BOM ID
     */
    private Long bomId;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 物料编码
     */
    private String materialCode;

    /**
     * 物料名称
     */
    private String materialName;

    /**
     * 规格型号
     */
    private String materialSpec;

    /**
     * 物料类型
     */
    private String materialType;

    /**
     * 节点类型: ASSEMBLY=组件(有下级BOM), PART=零件(叶子)
     */
    private String nodeType;

    /**
     * 单位父项用量
     */
    private BigDecimal quantityPerParent;

    /**
     * 损耗率(%)
     */
    private BigDecimal lossRate;

    /**
     * 累计用量（含损耗）
     */
    private BigDecimal cumulativeQuantity;

    /**
     * 单位名称
     */
    private String unitName;

    /**
     * 来源类型
     */
    private String sourceType;

    /**
     * 提前期(小时)
     */
    private Integer leadTime;

    /**
     * 工作中心名称
     */
    private String workstationName;

    /**
     * 是否可展开（有子BOM）
     */
    private boolean expandable;

    /**
     * 关联的明细ID
     */
    private Long detailId;

    /**
     * 父级BOM ID
     */
    private Long parentBomId;
}
