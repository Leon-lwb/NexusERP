package com.nexus.mes.pro.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.pro.domain.bo.MesMbomBo;
import com.nexus.mes.pro.domain.vo.MesMbomVo;

import java.util.List;

public interface IMesMbomService {
    TableDataInfo<MesMbomVo> selectPageList(MesMbomBo bo, PageQuery pageQuery);
    MesMbomVo selectById(Long mbomId);
    MesMbomVo selectByBomId(Long bomId);
    int insertByBo(MesMbomBo bo);
    int updateByBo(MesMbomBo bo);
    int deleteByIds(Long[] ids);
    void activate(Long mbomId);
    String verify(Long mbomId);
}
