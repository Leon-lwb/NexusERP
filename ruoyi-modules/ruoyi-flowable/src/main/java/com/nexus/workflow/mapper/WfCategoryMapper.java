package com.nexus.workflow.mapper;

import org.apache.ibatis.annotations.Param;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.workflow.domain.WfCategory;
import com.nexus.workflow.domain.vo.WfAppTypeVo;
import com.nexus.workflow.domain.vo.WfCategoryVo;

/**
 * 流程分类Mapper接口
 *
 * @author wanghuan
 * @date 2022-01-15
 */
public interface WfCategoryMapper extends BaseMapperPlus<WfCategory, WfCategoryVo> {
   String selectAppTypeByCode(@Param("code") String code, @Param("tenantId") String tenantId);
   WfAppTypeVo selectAppTypeVoByCode(@Param("code") String code, @Param("tenantId") String tenantId);
}
