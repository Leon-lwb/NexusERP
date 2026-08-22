package com.nexus.mes.release.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.release.domain.ReleasePkgBomItem;
import com.nexus.mes.release.domain.vo.ReleasePkgBomItemVo;
import org.apache.ibatis.annotations.Mapper;

/**
 * MES发布包BOM选配 Mapper 接口
 *
 * @author Nexus
 */
@Mapper
public interface ReleasePkgBomItemMapper extends BaseMapperPlus<ReleasePkgBomItem, ReleasePkgBomItemVo> {
}
