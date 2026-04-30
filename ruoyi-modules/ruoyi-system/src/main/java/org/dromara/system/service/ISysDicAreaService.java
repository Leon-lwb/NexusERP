package org.dromara.system.service;

import org.dromara.system.domain.SysDicArea;
import org.dromara.system.domain.vo.SysDicAreaVo;

import java.util.List;

public interface ISysDicAreaService {

    List<SysDicAreaVo> selectSysDicAreaList(SysDicArea sysDicArea);

    List<SysDicAreaVo> selectSysDicAreaTreeList(SysDicArea sysDicArea);

    List<SysDicAreaVo> selectChildrenTreeList(SysDicArea sysDicArea);

    SysDicAreaVo selectSysDicAreaByPkid(Long pkid);

    List<SysDicAreaVo> selectProvinces();

    List<SysDicAreaVo> selectNextLevel(Long parentAreaId);

    int insertSysDicArea(SysDicArea sysDicArea);

    int updateSysDicArea(SysDicArea sysDicArea);

    int deleteSysDicAreaByPkid(Long pkid);

    int deleteSysDicAreaByPkids(Long[] pkids);
}
