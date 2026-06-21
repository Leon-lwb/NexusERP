package com.nexus.mes.pro.service;

import com.nexus.mes.pro.domain.vo.MesMbomLineVo;

import java.util.List;

public interface IMesMbomLineService {
    List<MesMbomLineVo> selectByMbomId(Long mbomId);
}
