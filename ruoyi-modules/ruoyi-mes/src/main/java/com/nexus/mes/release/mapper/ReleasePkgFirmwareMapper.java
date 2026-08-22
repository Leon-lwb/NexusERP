package com.nexus.mes.release.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.release.domain.ReleasePkgFirmware;
import com.nexus.mes.release.domain.vo.ReleasePkgFirmwareVo;
import org.apache.ibatis.annotations.Mapper;

/**
 * MES发布包固件映射 Mapper 接口
 *
 * @author Nexus
 */
@Mapper
public interface ReleasePkgFirmwareMapper extends BaseMapperPlus<ReleasePkgFirmware, ReleasePkgFirmwareVo> {
}
