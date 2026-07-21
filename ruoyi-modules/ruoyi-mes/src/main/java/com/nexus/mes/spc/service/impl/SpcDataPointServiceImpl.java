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

    /** 数据点数据访问 Mapper。 */
    private final SpcDataPointMapper baseMapper;

    /**
     * 分页查询 SPC 数据点列表。
     *
     * @param bo        数据点查询条件（控制图/样本组/OOC 标志）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含数据点视图列表及分页元信息
     */
    @Override
    public TableDataInfo<SpcDataPointVo> selectPageList(SpcDataPointBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<SpcDataPoint> lqw = buildQueryWrapper(bo);
        Page<SpcDataPointVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 根据主键 ID 获取 SPC 数据点详情。
     *
     * @param dataId 数据点 ID
     * @return 数据点视图对象
     */
    @Override
    public SpcDataPointVo selectById(Long dataId) {
        return baseMapper.selectVoById(dataId);
    }

    /**
     * 按控制图查询其全部数据点（按样本组号升序），供前端绘制控制图。
     *
     * @param chartId 控制图 ID
     * @return 该控制图下的数据点列表
     */
    /**
     * 按控制图查询其全部数据点（按样本组号升序），供前端绘制控制图。
     *
     * @param chartId 控制图 ID
     * @return 该控制图下的数据点列表
     */
    @Override
    public List<SpcDataPointVo> listByChartId(Long chartId) {
        return baseMapper.selectVoList(Wrappers.<SpcDataPoint>lambdaQuery()
            .eq(SpcDataPoint::getChartId, chartId)
            .orderByAsc(SpcDataPoint::getSampleGroup));
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 根据查询条件构建数据点的 LambdaQueryWrapper（控制图/样本组/OOC 标志精确匹配，按样本组号升序）。
     *
     * @param bo 数据点查询条件
     * @return 数据点查询条件构造器
     */
    /**
     * 根据查询条件构建数据点的 LambdaQueryWrapper（控制图/样本组/OOC 标志精确匹配，按样本组号升序）。
     *
     * @param bo 数据点查询条件
     * @return 数据点查询条件构造器
     */
    private LambdaQueryWrapper<SpcDataPoint> buildQueryWrapper(SpcDataPointBo bo) {
        return Wrappers.<SpcDataPoint>lambdaQuery()
            .eq(bo.getChartId() != null, SpcDataPoint::getChartId, bo.getChartId())
            .eq(bo.getSampleGroup() != null, SpcDataPoint::getSampleGroup, bo.getSampleGroup())
            .eq(StringUtils.isNotBlank(bo.getIsOoc()), SpcDataPoint::getIsOoc, bo.getIsOoc())
            .orderByAsc(SpcDataPoint::getSampleGroup);
    }
}
