package com.nexus.mes.dc.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.dc.domain.DcCollectionPoint;
import com.nexus.mes.dc.domain.bo.DcCollectionPointBo;
import com.nexus.mes.dc.domain.vo.DcCollectionPointVo;
import com.nexus.mes.dc.mapper.DcCollectionPointMapper;
import com.nexus.mes.dc.service.IDcCollectionPointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

/**
 * MES数据采集点 Service 业务层实现
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("dcCollectionPointService")
public class DcCollectionPointServiceImpl implements IDcCollectionPointService {

    private final DcCollectionPointMapper baseMapper;

    @Override
    public TableDataInfo<DcCollectionPointVo> selectPageList(DcCollectionPointBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<DcCollectionPoint> lqw = buildQueryWrapper(bo);
        Page<DcCollectionPointVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public DcCollectionPointVo selectById(Long pointId) {
        return baseMapper.selectVoById(pointId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DcCollectionPointVo insertByBo(DcCollectionPointBo bo) {
        Long existCount = baseMapper.selectCount(Wrappers.<DcCollectionPoint>lambdaQuery()
            .eq(DcCollectionPoint::getPointCode, bo.getPointCode()));
        if (existCount != null && existCount > 0) {
            throw new RuntimeException("采集点编码已存在: " + bo.getPointCode());
        }
        DcCollectionPoint point = new DcCollectionPoint();
        point.setPointCode(bo.getPointCode());
        point.setPointName(bo.getPointName());
        point.setPointType(bo.getPointType());
        point.setEquipmentId(bo.getEquipmentId());
        point.setProcessId(bo.getProcessId());
        point.setDataType(bo.getDataType());
        point.setUnit(bo.getUnit());
        point.setFrequency(bo.getFrequency());
        point.setStatus(StringUtils.isNotBlank(bo.getStatus()) ? bo.getStatus() : STATUS_ACTIVE);
        point.setRemark(bo.getRemark());
        baseMapper.insert(point);
        log.info("新增采集点: pointId={}, code={}", point.getPointId(), point.getPointCode());
        return baseMapper.selectVoById(point.getPointId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DcCollectionPointVo updateByBo(DcCollectionPointBo bo) {
        if (bo.getPointId() == null) {
            throw new RuntimeException("采集点ID不能为空");
        }
        DcCollectionPoint point = baseMapper.selectById(bo.getPointId());
        if (point == null) {
            throw new RuntimeException("采集点不存在: " + bo.getPointId());
        }
        if (StringUtils.isNotBlank(bo.getPointCode())
            && !bo.getPointCode().equals(point.getPointCode())) {
            Long existCount = baseMapper.selectCount(Wrappers.<DcCollectionPoint>lambdaQuery()
                .eq(DcCollectionPoint::getPointCode, bo.getPointCode())
                .ne(DcCollectionPoint::getPointId, bo.getPointId()));
            if (existCount != null && existCount > 0) {
                throw new RuntimeException("采集点编码已存在: " + bo.getPointCode());
            }
            point.setPointCode(bo.getPointCode());
        }
        point.setPointName(bo.getPointName());
        point.setPointType(bo.getPointType());
        point.setEquipmentId(bo.getEquipmentId());
        point.setProcessId(bo.getProcessId());
        point.setDataType(bo.getDataType());
        point.setUnit(bo.getUnit());
        point.setFrequency(bo.getFrequency());
        if (StringUtils.isNotBlank(bo.getStatus())) {
            point.setStatus(bo.getStatus());
        }
        point.setRemark(bo.getRemark());
        baseMapper.updateById(point);
        log.info("编辑采集点: pointId={}", point.getPointId());
        return baseMapper.selectVoById(point.getPointId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        return baseMapper.deleteByIds(Arrays.asList(ids)) > 0;
    }

    // ==================== 私有辅助方法 ====================

    private LambdaQueryWrapper<DcCollectionPoint> buildQueryWrapper(DcCollectionPointBo bo) {
        return Wrappers.<DcCollectionPoint>lambdaQuery()
            .like(StringUtils.isNotBlank(bo.getPointCode()), DcCollectionPoint::getPointCode, bo.getPointCode())
            .like(StringUtils.isNotBlank(bo.getPointName()), DcCollectionPoint::getPointName, bo.getPointName())
            .eq(StringUtils.isNotBlank(bo.getPointType()), DcCollectionPoint::getPointType, bo.getPointType())
            .eq(StringUtils.isNotBlank(bo.getDataType()), DcCollectionPoint::getDataType, bo.getDataType())
            .eq(StringUtils.isNotBlank(bo.getStatus()), DcCollectionPoint::getStatus, bo.getStatus())
            .eq(bo.getEquipmentId() != null, DcCollectionPoint::getEquipmentId, bo.getEquipmentId())
            .eq(bo.getProcessId() != null, DcCollectionPoint::getProcessId, bo.getProcessId())
            .orderByDesc(DcCollectionPoint::getCreateTime);
    }
}