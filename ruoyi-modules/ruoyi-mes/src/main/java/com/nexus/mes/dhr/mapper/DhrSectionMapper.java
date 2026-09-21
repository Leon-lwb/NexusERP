package com.nexus.mes.dhr.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.dhr.domain.DhrSection;
import com.nexus.mes.dhr.domain.vo.DhrSectionVo;

/**
 * DHR 分册 Mapper（append-only；仅 DRAFT 期允许 rebuild 覆盖）。
 *
 * <p>实体: {@link DhrSection} / VO: {@link DhrSectionVo}</p>
 *
 * @author Nexus
 */
public interface DhrSectionMapper extends BaseMapperPlus<DhrSection, DhrSectionVo> {
}
