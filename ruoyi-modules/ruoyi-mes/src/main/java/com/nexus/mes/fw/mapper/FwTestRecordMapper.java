package com.nexus.mes.fw.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.fw.domain.FwTestRecord;
import com.nexus.mes.fw.domain.vo.FwTestRecordVo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 测试记录 Mapper 接口
 *
 * @author Nexus
 */
@Mapper
public interface FwTestRecordMapper extends BaseMapperPlus<FwTestRecord, FwTestRecordVo> {
}