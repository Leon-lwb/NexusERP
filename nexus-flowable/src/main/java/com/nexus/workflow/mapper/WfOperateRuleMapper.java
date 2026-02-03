package com.nexus.workflow.mapper;

import com.nexus.common.core.mapper.BaseMapperPlus;
import com.nexus.workflow.domain.WfOperateRule;
import com.nexus.workflow.domain.vo.WfOperateRuleVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;


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
