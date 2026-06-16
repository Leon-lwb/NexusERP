package com.nexus.erp.bd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * BOM比较结果VO
 *
 * @author nexus
 */
@Data
public class BomComparisonVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * BOM1信息
     */
    private BomSummary bom1;

    /**
     * BOM2信息
     */
    private BomSummary bom2;

    /**
     * 仅在BOM1中存在的子项（BOM1有、BOM2无）
     */
    private List<ComparisonLine> onlyInBom1;

    /**
     * 仅在BOM2中存在的子项（BOM2有、BOM1无）
     */
    private List<ComparisonLine> onlyInBom2;

    /**
     * 两个BOM都存在但有差异的子项
     */
    private List<ComparisonLine> changed;

    /**
     * 两个BOM完全相同的子项
     */
    private List<ComparisonLine> unchanged;

    /**
     * BOM摘要信息
     */
    @Data
    public static class BomSummary implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long bomId;
        private String bomName;
        private String bomVersion;
        private String bomType;
        private String materialCode;
        private String materialName;
        private int totalItems;
    }

    /**
     * 比较行
     */
    @Data
    public static class ComparisonLine implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long childMaterialId;
        private String childMaterialCode;
        private String childMaterialName;
        private String childMaterialSpec;
        private BigDecimal quantity1;      // BOM1的用量
        private BigDecimal quantity2;      // BOM2的用量
        private BigDecimal lossRate1;      // BOM1的损耗率
        private BigDecimal lossRate2;      // BOM2的损耗率
        private Integer operationStep1;    // BOM1的工序步骤
        private Integer operationStep2;    // BOM2的工序步骤
        private String changeType;         // ADDED/REMOVED/MODIFIED/UNCHANGED
        private String changeDetail;       // 变更详情描述
    }
}
