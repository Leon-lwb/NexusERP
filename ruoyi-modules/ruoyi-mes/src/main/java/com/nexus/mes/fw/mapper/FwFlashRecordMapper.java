package com.nexus.mes.fw.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.fw.domain.FwFlashRecord;
import com.nexus.mes.fw.domain.vo.FwFlashRecordVo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 固件刷写记录 Mapper 接口
 *
 * @author Nexus
 */
@Mapper
public interface FwFlashRecordMapper extends BaseMapperPlus<FwFlashRecord, FwFlashRecordVo> {
}