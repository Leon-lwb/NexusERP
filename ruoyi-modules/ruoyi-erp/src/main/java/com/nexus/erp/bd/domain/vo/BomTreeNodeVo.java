package com.nexus.erp.bd.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * BOM树节点VO - 用于前端树形展示
 *
 * @author nexus
 */
@Data
public class BomTreeNodeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * BOM ID
     */
    private Long bomId;

    /**
     * 当前节点物料ID
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
     * 层级 (0=顶层)
     */
    private Integer level;

    /**
     * 节点类型: ASSEMBLY=组件(有下级BOM), PART=零件(叶子)
     */
    private String nodeType;

    /**
     * 父级BOM ID
     */
    private Long parentBomId;

    /**
     * 关联的明细ID
     */
    private Long detailId;

    /**
     * 单位父项用量
     */
    private BigDecimal quantityPerParent;

    /**
     * 损耗率(%)
     */
    private BigDecimal lossRate;

    /**
     * 含损耗的总用量
     */
    private BigDecimal totalQuantity;

    /**
     * 来源类型
     */
    private String sourceType;

    /**
     * 提前期(小时)
     */
    private Integer leadTime;

    /**
     * 是否有子BOM
     */
    private boolean hasChildren;

    /**
     * 是否叶子节点
     */
    private boolean isLeaf;

    /**
     * 子节点列表
     */
    private List<BomTreeNodeVo> children = new ArrayList<>();
}
