package org.dromara.workflow.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.workflow.domain.WfOperateRule;
import org.dromara.workflow.domain.vo.WfOperateRuleVo;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;


/**
 * 流程操作规则Mapper接口
 *
 * @author nbacheng
 * @date 2023-11-23
 */
public interface WfOperateRuleMapper extends BaseMapperPlus<WfOperateRule, WfOperateRuleVo> {

	//根据主表configId获取操作规则
	@Select("SELECT * FROM wf_operate_rule WHERE config_id = #{configId}")
	List<WfOperateRuleVo> selectRuleByConfigId(@Param("configId") Long configId);
}
