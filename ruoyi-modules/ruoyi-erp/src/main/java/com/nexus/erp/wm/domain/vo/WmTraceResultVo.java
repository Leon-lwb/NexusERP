package com.nexus.erp.wm.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 追溯结果视图对象
 *
 * @author nexus
 * @date 2026-06-02
 */
@Data
public class WmTraceResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 追溯方向：FORWARD / BACKWARD */
    private String direction;

    /** 追溯关键字（物料编码 + 批次/序列号） */
    private String traceKey;

    /** 扁平节点列表 */
    private List<WmTraceNodeVo> nodes;

    /** 树形结构根节点 */
    private WmTraceNodeVo tree;
    /** Stable physical subject when the query has been constrained to one formal SN. */
    @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
    private Long identityId;
    private String traceScope;
    private Boolean truncated;
    private String truncationReason;
    private List<String> evidenceGaps;
}
