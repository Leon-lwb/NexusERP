package com.nexus.workflow.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.workflow.domain.WfCustomRule;
import com.nexus.workflow.domain.vo.WfCustomRuleVo;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;


/**
 * 流程自定义业务规则Mapper接口
 *
 * @author wanghuan
 * @date 2023-11-23
 */
public interface WfCustomRuleMapper extends BaseMapperPlus<WfCustomRule, WfCustomRuleVo> {

	//根据主表configId获取自定义表单规则
	@Select("SELECT * FROM wf_custom_rule WHERE config_id = #{configId}")
	List<WfCustomRuleVo> selectRuleByConfigId(@Param("configId") Long configId);
}
