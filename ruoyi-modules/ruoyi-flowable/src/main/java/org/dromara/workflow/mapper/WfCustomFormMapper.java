package org.dromara.workflow.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.workflow.domain.WfCustomForm;
import org.dromara.workflow.domain.bo.WfCustomFormBo;
import org.dromara.workflow.domain.vo.CustomFormVo;
import org.dromara.workflow.domain.vo.WfCustomFormVo;

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
	WfCustomForm selectSysCustomFormById(Long formId);
	List<WfCustomForm> selectSysCustomFormByServiceName(String serviceName);
	WfCustomFormBo selectSysCustomFormByDeployId(String deployId);
	@Select("select business_service from wf_custom_form where deploy_id = #{deployId}")
	String selectServiceNameByDeployId(@Param("deployId") String deployId);
}
