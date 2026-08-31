package com.nexus.workflow.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.workflow.domain.WfFlowConfig;
import com.nexus.workflow.domain.vo.WfFlowConfigVo;

import org.apache.ibatis.annotations.Param;


/**
 * 流程配置主Mapper接口
 *
 * @author wanghuan
 * @date 2023-11-19
 */
public interface WfFlowConfigMapper extends BaseMapperPlus<WfFlowConfig, WfFlowConfigVo> {
	void updateFlowConfig(@Param("flowConfigVo") WfFlowConfigVo flowConfigVo);
	WfFlowConfig selectByModelIdAndNodeKey(@Param("flowConfigVo") WfFlowConfigVo flowConfigVo);
}
