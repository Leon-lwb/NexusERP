package com.nexus.mes.spc.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.spc.domain.SpcDataPoint;
import com.nexus.mes.spc.domain.bo.SpcDataPointBo;
import com.nexus.mes.spc.domain.vo.SpcDataPointVo;
import com.nexus.mes.spc.mapper.SpcDataPointMapper;
import com.nexus.mes.spc.service.ISpcDataPointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MES SPC数据点 Service 业务层实现
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("spcDataPointService")
public class SpcDataPointServiceImpl implements ISpcDataPointService {

    private final SpcDataPointMapper baseMapper;

    @Override
    public TableDataInfo<SpcDataPointVo> selectPageList(SpcDataPointBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SpcDataPoint> lqw = buildQueryWrapper(bo);
        Page<SpcDataPointVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public SpcDataPointVo selectById(Long dataId) {
        return baseMapper.selectVoById(dataId);
    }

    @Override
    public List<SpcDataPointVo> listByChartId(Long chartId) {
        return baseMapper.selectVoList(Wrappers.<SpcDataPoint>lambdaQuery()
            .eq(SpcDataPoint::getChartId, chartId)
            .orderByAsc(SpcDataPoint::getSampleGroup));
    }

    // ==================== 私有辅助方法 ====================

    private LambdaQueryWrapper<SpcDataPoint> buildQueryWrapper(SpcDataPointBo bo) {
        return Wrappers.<SpcDataPoint>lambdaQuery()
            .eq(bo.getChartId() != null, SpcDataPoint::getChartId, bo.getChartId())
            .eq(bo.getSampleGroup() != null, SpcDataPoint::getSampleGroup, bo.getSampleGroup())
            .eq(StringUtils.isNotBlank(bo.getIsOoc()), SpcDataPoint::getIsOoc, bo.getIsOoc())
            .orderByAsc(SpcDataPoint::getSampleGroup);
    }
}
