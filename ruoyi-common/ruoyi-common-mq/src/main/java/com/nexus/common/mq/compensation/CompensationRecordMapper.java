package com.nexus.common.mq.compensation;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import org.apache.ibatis.annotations.Mapper;

/** 补偿记录Mapper接口
 *
 * @author Nexus
 */
@Mapper
public interface CompensationRecordMapper extends BaseMapperPlus<CompensationRecord, CompensationRecord> {
}
