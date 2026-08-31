package com.nexus.workflow.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 流程意见数据传输对象
 * <p>封装流程审批、退回、驳回意见的类型与内容，属于 Flowable 工作流模块。</p>
 *
 * @author wanghuan
 * @createTime 2022/3/10 00:12
 */
@Data
@Builder
public class WfCommentDto implements Serializable {

    /**
     * 意见类别：0 正常意见，1 退回意见，2 驳回意见
     */
    private String type;

    /**
     * 意见内容
     */
    private String comment;
}
