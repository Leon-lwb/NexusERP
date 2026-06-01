package com.nexus.erp.prd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * BOM计算节点VO
 *
 * @author nexus
 */
@Data
public class BomCalculationNodeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 物料ID */
    private Long materialId;
    /** 数量 */
    private BigDecimal quantity;
    /** BOM ID */
    private Long bomId;
    /** BOM层级 */
    private Integer level;
    /** 物料路径 */
    private String materialPath;
    /** 父级明细ID */
    private Long parentDetail;

    /**
     * 判断是否有父级明细
     *
     * @return 是否有父级明细
     */
    public boolean hasParentDetail() {
        return parentDetail != null;
    }

    /**
     * 判断是否为根节点
     *
     * @return 是否为根节点（层级为0）
     */
    public boolean isRootNode() {
        return level != null && level == 0;
    }
}
