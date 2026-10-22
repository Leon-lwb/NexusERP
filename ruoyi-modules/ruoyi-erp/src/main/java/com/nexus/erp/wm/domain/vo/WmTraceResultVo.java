package com.nexus.erp.wm.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;

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

    /**
     * 追溯方向：FORWARD / BACKWARD
     */
    private String direction;

    /**
     * 追溯关键字（物料编码 + 批次/序列号）
     */
    private String traceKey;

    /**
     * 扁平节点列表
     */
    private List<WmTraceNodeVo> nodes;

    /**
     * 树形结构根节点
     */
    private WmTraceNodeVo tree;

    /** 查询已收敛到一个正式单件时使用的稳定实物主体。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long identityId;

    private String traceScope;
    private Boolean truncated;
    private String truncationReason;
    private List<String> evidenceGaps;
}
