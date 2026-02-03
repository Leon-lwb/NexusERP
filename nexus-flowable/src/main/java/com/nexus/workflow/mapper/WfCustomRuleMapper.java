package com.nexus.workflow.mapper;

import com.nexus.common.core.mapper.BaseMapperPlus;
import com.nexus.workflow.domain.WfCustomRule;
import com.nexus.workflow.domain.vo.WfCustomRuleVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;


/**
 * 流程自定义业务规则Mapper接口
 *
 * @author nbacheng
 * @date 2023-11-23
 */
public interface WfCustomRuleMapper extends BaseMapperPlus<WfCustomRule, WfCustomRuleVo> {

	//根据主表configId获取自定义表单规则
	@Select("SELECT * FROM wf_custom_rule WHERE config_id = #{configId}")
	List<WfCustomRuleVo> selectRuleByConfigId(@Param("configId") Long configId);
}
