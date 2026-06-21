package com.nexus.mes.pro.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.pro.domain.MesMbom;
import com.nexus.mes.pro.domain.vo.MesMbomVo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MesMbomMapper extends BaseMapperPlus<MesMbom, MesMbomVo> {
}
