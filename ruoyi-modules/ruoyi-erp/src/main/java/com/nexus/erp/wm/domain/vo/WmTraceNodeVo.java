package com.nexus.erp.wm.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 追溯节点视图对象
 *
 * @author nexus
 * @date 2026-06-02
 */
@Data
public class WmTraceNodeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 唯一节点ID (materialId + docType + docId) */
    private String nodeId;

    /** 父节点ID，用于树形结构 */
    private String parentNodeId;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 批次编码 */
    private String batchCode;

    /** 序列号编码 */
    private String snCode;

    /** 数量 */
    private BigDecimal quantity;

    /** 来源单据类型 */
    private String docType;

    /** 来源单据ID */
    private Long docId;

    /** 来源单据编号 */
    private String docNo;

    /** 仓库名称 */
    private String warehouseName;

    /** 事务时间 */
    private Date transactionTime;

    /** 子节点列表 */
    private List<WmTraceNodeVo> children;
}
