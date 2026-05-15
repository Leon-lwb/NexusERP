package org.dromara.workflow.domain.vo;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

/**
 * 当前节点流程补充信息
 *
 * @author wanghuan
 * @createTime 2024/01/04
 */

@Data
public class CurNodeInfoVo {
	/**
     * 任务编号
     */
    private String taskId;
    /**
     * 当前任务节点执行人
     */
    private String assignee;
    /**
     * 流程定义名称
     */
    private String procDefName;
    /**
     * 流程定义内置使用版本
     */
    private String procDefVersion;
    /**
     * 流程实例ID
     */
    private String procInsId;
    /**
     * 当前任务节点接收时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date receiveTime;
}
