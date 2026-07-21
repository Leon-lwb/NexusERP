package com.nexus.workflow.domain.vo;

import lombok.Data;

/**
 * 自定义表单关联视图对象
 * <p>描述流程业务单与流程表单的关联关系，属于 Flowable 工作流模块。</p>
 *
 * @author wanghuan
 * @since 2022/5/3
 */
@Data
public class CustomFormVo {
	/** 流程业务单 ID。 */
	private String id;
	/** 关联流程部署 ID。 */
	private String deployId;
	/** 流程名称。 */
	private String flowName;
}
