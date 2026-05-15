package org.dromara.workflow.mapper;

import org.apache.ibatis.annotations.Param;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.workflow.domain.WfCopy;
import org.dromara.workflow.domain.vo.WfCopyVo;

/**
 * 流程抄送Mapper接口
 *
 * @author wanghuan
 * @date 2022-05-19
 */
public interface WfCopyMapper extends BaseMapperPlus<WfCopy, WfCopyVo> {
  public int updateState(@Param("id") String id);
}
