package com.nexus.erp.bd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * BOM成本卷积结果VO
 * 物料成本 = Σ 叶子物料(标准成本 × 累计用量含损耗)
 * 工序成本 = Σ 步骤(标准工时 × (人工费率 × 人数 + 机器费率 × 机台数))
 * 单位总成本 = 物料成本 + 工序成本 (按单位父项计)
 *
 * @author nexus
 */
@Data
public class BomCostRollupVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long bomId;
    private Long parentMaterialId;
    private String parentMaterialCode;
    private String parentMaterialName;
    private String bomVersion;
    private Long routingId;
    private String routingCode;

    /** 物料成本合计 */
    private BigDecimal totalMaterialCost;

    /** 工序成本合计(任一步骤缺费率则为0且routingCostAvailable=false) */
    private BigDecimal totalRoutingCost;

    /** 单位总成本 = 物料成本 + 工序成本 */
    private BigDecimal totalCostPerUnit;

    /** 工序成本是否可计算(所有步骤费率齐全) */
    private boolean routingCostAvailable;

    /** 物料成本明细(按叶子物料聚合) */
    private List<MaterialCostLine> materialLines;

    /** 工序成本明细 */
    private List<RoutingCostLine> routingLines;

    @Data
    public static class MaterialCostLine implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long materialId;
        private String materialCode;
        private String materialName;
        private String materialSpec;
        /** 累计用量(含损耗,已聚合) */
        private BigDecimal cumulativeQuantity;
        private BigDecimal unitCost;
        private BigDecimal lineCost;
    }

    @Data
    public static class RoutingCostLine implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long stepId;
        private Integer sequenceNum;
        private String processName;
        private String workstationName;
        private BigDecimal standardHours;
        private Integer laborCount;
        private BigDecimal laborRate;
        private Integer machineCount;
        private BigDecimal machineRate;
        private BigDecimal stepCost;
    }
}
