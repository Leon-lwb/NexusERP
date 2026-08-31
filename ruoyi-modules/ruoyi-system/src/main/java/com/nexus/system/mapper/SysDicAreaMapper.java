package com.nexus.system.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.system.domain.SysDicArea;
import com.nexus.system.domain.vo.SysDicAreaVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SysDicAreaMapper extends BaseMapperPlus<SysDicArea, SysDicAreaVo> {

    List<SysDicArea> selectProvinces();

    List<SysDicArea> selectNextLevel(@Param("parentAreaId") Long parentAreaId);
}
