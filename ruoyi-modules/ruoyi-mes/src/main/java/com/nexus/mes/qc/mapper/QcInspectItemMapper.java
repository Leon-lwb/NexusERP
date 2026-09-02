package com.nexus.mes.qc.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.qc.domain.QcInspectItem;
import com.nexus.mes.qc.domain.vo.QcInspectItemVo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QcInspectItemMapper extends BaseMapperPlus<QcInspectItem, QcInspectItemVo> {
}
