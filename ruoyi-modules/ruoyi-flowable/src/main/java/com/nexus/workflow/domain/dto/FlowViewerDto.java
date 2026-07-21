package com.nexus.workflow.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 流程流转过程视图对象
 * <p>描述流程各节点是否已完成，用于前端渲染流程图高亮，属于 Flowable 工作流模块。</p>
 */
@Data
public class FlowViewerDto implements Serializable {

    /** 流程节点 key。 */
    private String key;
    /** 该节点是否已完成。 */
    private boolean completed;
}
