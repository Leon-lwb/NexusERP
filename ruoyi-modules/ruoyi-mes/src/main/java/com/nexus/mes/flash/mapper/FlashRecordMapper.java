package com.nexus.mes.flash.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.flash.domain.FlashRecord;
import com.nexus.mes.flash.domain.vo.FlashRecordVo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 固件刷写记录 Mapper 接口
 *
 * @author Nexus
 */
@Mapper
public interface FlashRecordMapper extends BaseMapperPlus<FlashRecord, FlashRecordVo> {
}