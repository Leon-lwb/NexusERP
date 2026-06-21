package com.nexus.mes.pro.service;

import com.nexus.mes.pro.domain.vo.ProWorkOrderBomLineVo;

import java.util.List;

public interface IProWorkOrderBomLineService {
    List<ProWorkOrderBomLineVo> selectByWoId(Long mesWoId);
}
