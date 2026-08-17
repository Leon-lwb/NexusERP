package com.nexus.mes.flash.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.flash.domain.TestRecord;
import com.nexus.mes.flash.domain.vo.TestRecordVo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 测试记录 Mapper 接口
 *
 * @author Nexus
 */
@Mapper
public interface TestRecordMapper extends BaseMapperPlus<TestRecord, TestRecordVo> {
}