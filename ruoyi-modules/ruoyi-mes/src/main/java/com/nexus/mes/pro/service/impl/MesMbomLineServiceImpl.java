package com.nexus.mes.pro.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import com.nexus.mes.pro.domain.MesMbomLine;
import com.nexus.mes.pro.domain.vo.MesMbomLineVo;
import com.nexus.mes.pro.mapper.MesMbomLineMapper;
import com.nexus.mes.pro.service.IMesMbomLineService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
@DS("mes")
@Service
public class MesMbomLineServiceImpl implements IMesMbomLineService {

    private final MesMbomLineMapper baseMapper;

    @Override
    /**
     * 查询
     * @param mbomId MBOMID
     * @return 结果列表
     */
    public List<MesMbomLineVo> selectByMbomId(Long mbomId) {
        if (mbomId == null) {
            return Collections.emptyList();
        }
        return baseMapper.selectVoList(Wrappers.lambdaQuery(MesMbomLine.class)
            .eq(MesMbomLine::getMbomId, mbomId)
            .orderByAsc(MesMbomLine::getSortOrder));
    }
}
