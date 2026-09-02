package com.nexus.mes.qc.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.qc.domain.QcInspectTemplate;
import com.nexus.mes.qc.domain.vo.QcInspectTemplateVo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QcInspectTemplateMapper extends BaseMapperPlus<QcInspectTemplate, QcInspectTemplateVo> {
}
