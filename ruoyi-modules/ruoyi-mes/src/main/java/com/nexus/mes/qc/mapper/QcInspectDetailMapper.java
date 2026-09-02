package com.nexus.mes.qc.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.qc.domain.QcInspectDetail;
import com.nexus.mes.qc.domain.vo.QcInspectDetailVo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QcInspectDetailMapper extends BaseMapperPlus<QcInspectDetail, QcInspectDetailVo> {
}
