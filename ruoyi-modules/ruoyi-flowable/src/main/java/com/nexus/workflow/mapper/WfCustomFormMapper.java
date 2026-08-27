package com.nexus.workflow.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.workflow.domain.WfCustomForm;
import com.nexus.workflow.domain.bo.WfCustomFormBo;
import com.nexus.workflow.domain.vo.CustomFormVo;
import com.nexus.workflow.domain.vo.WfCustomFormVo;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;


/**
 * 流程业务单Mapper接口
 *
 * @author wanghuan
 * @date 2023-10-09
 */
public interface WfCustomFormMapper extends BaseMapperPlus<WfCustomForm, WfCustomFormVo> {
	void updateCustom(@Param("customFormVo") CustomFormVo customFormVo);
	List<WfCustomForm> selectSysCustomFormByServiceName(String serviceName);
	WfCustomFormBo selectSysCustomFormByDeployId(String deployId);
	@Select("select business_service from wf_custom_form where deploy_id = #{deployId}")
	String selectServiceNameByDeployId(@Param("deployId") String deployId);
}
