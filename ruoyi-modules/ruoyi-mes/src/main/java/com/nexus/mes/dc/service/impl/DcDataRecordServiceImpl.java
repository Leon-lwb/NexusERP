package com.nexus.mes.dc.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.dc.domain.DcCollectionPoint;
import com.nexus.mes.dc.domain.DcDataRecord;
import com.nexus.mes.dc.domain.bo.DcDataRecordBo;
import com.nexus.mes.dc.domain.vo.DcDataRecordVo;
import com.nexus.mes.dc.mapper.DcCollectionPointMapper;
import com.nexus.mes.dc.mapper.DcDataRecordMapper;
import com.nexus.mes.dc.service.IDcDataRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * MES采集数据记录 Service 业务层实现
 * <p>
 * 手动录入(source=MANUAL, collectBy=当前用户); 按采集点聚合供SPC analyze。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("dcDataRecordService")
public class DcDataRecordServiceImpl implements IDcDataRecordService {

    private final DcDataRecordMapper baseMapper;
    private final DcCollectionPointMapper pointMapper;

    @Override
    public TableDataInfo<DcDataRecordVo> selectPageList(DcDataRecordBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<DcDataRecord> lqw = buildQueryWrapper(bo);
        Page<DcDataRecordVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public DcDataRecordVo selectById(Long recordId) {
        return baseMapper.selectVoById(recordId);
    }

    /**
     * 手动录入采集数据
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DcDataRecordVo insertByBo(DcDataRecordBo bo) {
        DcCollectionPoint point = pointMapper.selectById(bo.getPointId());
        if (point == null) {
            throw new RuntimeException("采集点不存在: " + bo.getPointId());
        }
        DcDataRecord record = new DcDataRecord();
        record.setPointId(bo.getPointId());
        record.setPointCode(point.getPointCode());
        record.setMesWoId(bo.getMesWoId());
        record.setCardId(bo.getCardId());
        record.setStepRecordId(bo.getStepRecordId());
        record.setNumericValue(bo.getNumericValue());
        record.setTextValue(bo.getTextValue());
        record.setCollectTime(bo.getCollectTime() != null ? bo.getCollectTime() : new Date());
        record.setCollectBy(LoginHelper.getUserId());
        record.setSource(StringUtils.isNotBlank(bo.getSource()) ? bo.getSource() : SOURCE_MANUAL);
        record.setRemark(bo.getRemark());
        baseMapper.insert(record);
        log.info("录入采集数据: recordId={}, pointId={}, numericValue={}", record.getRecordId(), record.getPointId(), record.getNumericValue());
        return baseMapper.selectVoById(record.getRecordId());
    }

    /**
     * 按采集点查询全部记录(按collect_time升序, 供SPC analyze)
     */
    @Override
    public List<DcDataRecordVo> listByPointId(Long pointId) {
        return baseMapper.selectVoList(Wrappers.<DcDataRecord>lambdaQuery()
            .eq(DcDataRecord::getPointId, pointId)
            .isNotNull(DcDataRecord::getNumericValue)
            .orderByAsc(DcDataRecord::getCollectTime));
    }

    // ==================== 私有辅助方法 ====================

    private LambdaQueryWrapper<DcDataRecord> buildQueryWrapper(DcDataRecordBo bo) {
        return Wrappers.<DcDataRecord>lambdaQuery()
            .eq(bo.getPointId() != null, DcDataRecord::getPointId, bo.getPointId())
            .eq(bo.getMesWoId() != null, DcDataRecord::getMesWoId, bo.getMesWoId())
            .eq(bo.getCardId() != null, DcDataRecord::getCardId, bo.getCardId())
            .eq(StringUtils.isNotBlank(bo.getSource()), DcDataRecord::getSource, bo.getSource())
            .orderByDesc(DcDataRecord::getCollectTime);
    }
}