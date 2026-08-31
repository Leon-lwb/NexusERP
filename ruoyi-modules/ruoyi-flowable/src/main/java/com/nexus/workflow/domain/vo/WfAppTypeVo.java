package com.nexus.workflow.domain.vo;

import lombok.Data;

/**
 * 流程APP分类视图对象
 * <p>描述流程分类下关联的 APP 类型，用于流程发起时选择应用分类，属于 Flowable 工作流模块。</p>
 *
 * @author wanghuan
 * @since 2023/10/13
 */
@Data
public class WfAppTypeVo {
	/** APP 类型 ID。 */
	private String id;
	/** APP 类型名称。 */
	private String name;
}
