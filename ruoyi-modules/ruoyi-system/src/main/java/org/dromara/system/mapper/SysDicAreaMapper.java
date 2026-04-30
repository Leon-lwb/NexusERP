package org.dromara.system.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.system.domain.SysDicArea;
import org.dromara.system.domain.vo.SysDicAreaVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SysDicAreaMapper extends BaseMapperPlus<SysDicArea, SysDicAreaVo> {

    List<SysDicArea> selectProvinces();

    List<SysDicArea> selectNextLevel(@Param("parentAreaId") Long parentAreaId);
}
