package com.nexus.mes.pro.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.pro.domain.MesMbomLine;
import com.nexus.mes.pro.domain.vo.MesMbomLineVo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MesMbomLineMapper extends BaseMapperPlus<MesMbomLine, MesMbomLineVo> {
}
