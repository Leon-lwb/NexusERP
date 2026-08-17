package com.nexus.mes.flash.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.flash.domain.Firmware;
import com.nexus.mes.flash.domain.vo.FirmwareVo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 固件档案 Mapper 接口
 *
 * @author Nexus
 */
@Mapper
public interface FirmwareMapper extends BaseMapperPlus<Firmware, FirmwareVo> {
}