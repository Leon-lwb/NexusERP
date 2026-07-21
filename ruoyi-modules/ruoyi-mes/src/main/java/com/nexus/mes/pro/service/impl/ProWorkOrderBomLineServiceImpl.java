package com.nexus.mes.pro.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import com.nexus.mes.pro.domain.ProWorkOrderBomLine;
import com.nexus.mes.pro.domain.vo.ProWorkOrderBomLineVo;
import com.nexus.mes.pro.mapper.ProWorkOrderBomLineMapper;
import com.nexus.mes.pro.service.IProWorkOrderBomLineService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
@DS("mes")
@Service
public class ProWorkOrderBomLineServiceImpl implements IProWorkOrderBomLineService {

    private final ProWorkOrderBomLineMapper baseMapper;

    @Override
    /**
     * 查询
     * @param mesWoId MES工单ID
     * @return 结果列表
     */
    public List<ProWorkOrderBomLineVo> selectByWoId(Long mesWoId) {
        if (mesWoId == null) {
            return Collections.emptyList();
        }
        return baseMapper.selectVoList(Wrappers.lambdaQuery(ProWorkOrderBomLine.class)
            .eq(ProWorkOrderBomLine::getMesWoId, mesWoId)
            .orderByAsc(ProWorkOrderBomLine::getSortOrder));
    }
}
