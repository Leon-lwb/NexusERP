package com.nexus.common.mq.domain;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息记录Mapper接口
 *
 * @author Nexus
 */
@Mapper
public interface MessageRecordMapper extends BaseMapperPlus<MessageRecord, MessageRecord> {
}
