package com.nexus.workflow.mapper;

import com.nexus.common.core.mapper.BaseMapperPlus;
import com.nexus.workflow.domain.WfCopy;
import com.nexus.workflow.domain.vo.WfCopyVo;
import org.apache.ibatis.annotations.Param;

/**
 * 流程抄送Mapper接口
 *
 * @author nbacheng
 * @date 2022-05-19
 */
public interface WfCopyMapper extends BaseMapperPlus<WfCopy, WfCopyVo> {
  public int updateState(@Param("id") String id);
}
