package com.nexus.workflow.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.workflow.domain.WfDeployForm;
import com.nexus.workflow.domain.vo.WfDeployFormVo;

/**
 * 流程实例关联表单Mapper接口
 *
 * @author wanghuan
 * @createTime 2022/3/7 22:07
 */
public interface WfDeployFormMapper extends BaseMapperPlus<WfDeployForm, WfDeployFormVo> {

	WfDeployForm selectSysDeployFormByFormId(String id);

}
