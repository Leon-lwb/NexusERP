package com.nexus.mes.flash.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.flash.domain.TestItem;
import com.nexus.mes.flash.domain.vo.TestItemVo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 测试项标准 Mapper 接口
 *
 * @author Nexus
 */
@Mapper
public interface TestItemMapper extends BaseMapperPlus<TestItem, TestItemVo> {
}