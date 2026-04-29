package org.dromara.workflow.domain.vo;

import java.util.List;

import lombok.Data;

/**
 * 流程规则对象
 *
 * @author nbacheng
 * @date 2023-11-24
 */
@Data
public class WfRuleVo {
	/**
     * 流程节点自定义表单信息
     */
	 private List<WfCustomRuleVo> customRuleVoList;
	 
	/**
     * 流程节点操作信息
     */
	 private List<WfOperateRuleVo> operateRuleVoList;
}
