package com.nexus.mes.release.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.release.domain.ReleasePkgConfig;
import com.nexus.mes.release.domain.vo.ReleasePkgConfigVo;
import org.apache.ibatis.annotations.Mapper;

/**
 * MES发布包配置项 Mapper 接口
 *
 * @author Nexus
 */
@Mapper
public interface ReleasePkgConfigMapper extends BaseMapperPlus<ReleasePkgConfig, ReleasePkgConfigVo> {
}
