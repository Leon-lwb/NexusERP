package com.nexus.core.prd.domain.vo;

import com.nexus.core.bd.domain.vo.BomDetailVO;
import lombok.Data;
import java.math.BigDecimal;

/**
 * BOM计算节点VO
 */
@Data
public class BomCalculationNodeVO {
    private Long materialId;
    private BigDecimal quantity;
    private Long bomId;
    private Integer level;
    private String materialPath;
    private BomDetailVO parentDetail;
    
    // 可以添加一些便捷方法
    public boolean hasParentDetail() {
        return parentDetail != null;
    }
    
    public boolean isRootNode() {
        return level != null && level == 0;
    }
}