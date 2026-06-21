package com.nexus.mes.pro.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.pro.domain.ProWorkOrderBomLine;
import com.nexus.mes.pro.domain.vo.ProWorkOrderBomLineVo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProWorkOrderBomLineMapper extends BaseMapperPlus<ProWorkOrderBomLine, ProWorkOrderBomLineVo> {
}
