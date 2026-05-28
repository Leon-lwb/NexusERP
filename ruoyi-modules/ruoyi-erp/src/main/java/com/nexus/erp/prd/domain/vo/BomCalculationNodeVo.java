package com.nexus.erp.prd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * BOM计算节点VO
 *
 * @author ruoyi
 */
@Data
public class BomCalculationNodeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long materialId;
    private BigDecimal quantity;
    private Long bomId;
    private Integer level;
    private String materialPath;
    private Long parentDetail;

    public boolean hasParentDetail() {
        return parentDetail != null;
    }

    public boolean isRootNode() {
        return level != null && level == 0;
    }
}
