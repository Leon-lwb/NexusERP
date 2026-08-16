package com.nexus.mes.fw.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.fw.domain.FwFirmware;
import com.nexus.mes.fw.domain.vo.FwFirmwareVo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 固件档案 Mapper 接口
 *
 * @author Nexus
 */
@Mapper
public interface FwFirmwareMapper extends BaseMapperPlus<FwFirmware, FwFirmwareVo> {
}