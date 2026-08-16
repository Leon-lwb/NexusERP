package com.nexus.mes.fw.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.mes.fw.domain.FwTestItem;
import com.nexus.mes.fw.domain.vo.FwTestItemVo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 测试项标准 Mapper 接口
 *
 * @author Nexus
 */
@Mapper
public interface FwTestItemMapper extends BaseMapperPlus<FwTestItem, FwTestItemVo> {
}