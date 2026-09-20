package com.nexus.mes.dc.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * MES采集数据记录 Service 业务层实现
 * <p>
 * 手动录入(source=MANUAL, collectBy=当前用户)并按采集点 dataType 分流校验; 按采集点聚合供SPC analyze。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("dcDataRecordService")
public class DcDataRecordServiceImpl implements IDcDataRecordService {

    /** 数据类型: 数值型(必须有 numericValue 且非负) */
    private static final String DATA_TYPE_NUMERIC = "NUMERIC";
    /** 数据类型: 文本型(必须有 textValue) */
    private static final String DATA_TYPE_TEXT = "TEXT";
    /** 数据类型: 枚举型(必须有 textValue) */
    private static final String DATA_TYPE_ENUM = "ENUM";
    /** numeric_value 列精度 decimal(14,4) 的小数位 */
    private static final int NUMERIC_SCALE = 4;

    private final DcDataRecordMapper baseMapper;
    private final DcCollectionPointMapper pointMapper;

    /**
     * 分页查询数据记录列表
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    @Override
    public TableDataInfo<DcDataRecordVo> selectPageList(DcDataRecordBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<DcDataRecord> lqw = buildQueryWrapper(bo);
        Page<DcDataRecordVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 获取数据记录详情
     *
     * @param recordId 数据记录ID
     * @return 返回结果
     */
    @Override
    public DcDataRecordVo selectById(Long recordId) {
        return baseMapper.selectVoById(recordId);
    }

    /**
     * 手动录入采集数据
     * <p>按采集点 dataType 分流校验：NUMERIC 必须有非负 numericValue（否则会被 SPC 的
     * {@code isNotNull(numeric_value)} 静默丢弃）；TEXT/ENUM 必须有非空 textValue。
     * dataType 未配置的历史采集点保持宽松（不阻断既有录入）。</p>
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 返回结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DcDataRecordVo insertByBo(DcDataRecordBo bo) {
        DcCollectionPoint point = pointMapper.selectById(bo.getPointId());
        if (point == null) {
            throw new ServiceException("采集点不存在: " + bo.getPointId());
        }
        BigDecimal numericValue = bo.getNumericValue();
        String textValue = bo.getTextValue();
        String dataType = point.getDataType();
        if (DATA_TYPE_NUMERIC.equals(dataType)) {
            if (numericValue == null) {
                throw new ServiceException("采集点[" + point.getPointCode() + "]数据类型为 NUMERIC, 必须填写数值(numericValue)");
            }
            if (numericValue.compareTo(BigDecimal.ZERO) < 0) {
                throw new ServiceException("采集点[" + point.getPointCode() + "]数值不能为负数: " + numericValue);
            }
            if (numericValue.scale() > NUMERIC_SCALE) {
                // numeric_value 为 decimal(14,4): 超出小数位按 HALF_UP 收敛到 4 位(不整条打回, 仅告警提示)
                BigDecimal scaled = numericValue.setScale(NUMERIC_SCALE, RoundingMode.HALF_UP);
                log.warn("采集数值小数位超过 {} 位, 已四舍五入: pointCode={}, raw={}, scaled={}",
                    NUMERIC_SCALE, point.getPointCode(), numericValue, scaled);
                numericValue = scaled;
            }
        } else if (DATA_TYPE_TEXT.equals(dataType) || DATA_TYPE_ENUM.equals(dataType)) {
            if (StringUtils.isBlank(textValue)) {
                throw new ServiceException("采集点[" + point.getPointCode() + "]数据类型为 " + dataType + ", 必须填写文本值(textValue)");
            }
            // ENUM 允许值校验依赖 dc_collection_point.enum_values 列(当前表无此列), 见交付说明中的 DDL
        }
        DcDataRecord record = new DcDataRecord();
        record.setPointId(bo.getPointId());
        record.setPointCode(point.getPointCode());
        record.setMesWoId(bo.getMesWoId());
        record.setCardId(bo.getCardId());
        record.setStepRecordId(bo.getStepRecordId());
        record.setNumericValue(numericValue);
        record.setTextValue(textValue);
        record.setCollectTime(bo.getCollectTime() != null ? bo.getCollectTime() : new Date());
        record.setCollectBy(LoginHelper.getUserId());
        record.setSource(StringUtils.isNotBlank(bo.getSource()) ? bo.getSource() : SOURCE_MANUAL);
        record.setRemark(bo.getRemark());
        baseMapper.insert(record);
        log.info("录入采集数据: recordId={}, pointId={}, dataType={}, numericValue={}",
            record.getRecordId(), record.getPointId(), dataType, record.getNumericValue());
        return baseMapper.selectVoById(record.getRecordId());
    }

    /**
     * 按采集点查询全部记录(按collect_time升序, 供SPC analyze用)
     *
     * @param pointId 采集点ID
     * @return 结果列表
     * @deprecated 全量拉取无上限, 请改用 {@link #listByPointId(Long, Date, Date, Integer)}
     */
    @Override
    @Deprecated
    public List<DcDataRecordVo> listByPointId(Long pointId) {
        return listByPointId(pointId, null, null, null);
    }

    /**
     * 按采集点 + 时间窗 + 条数上限查询记录(始终按 collect_time 升序返回)
     *
     * @param pointId   采集点ID
     * @param startTime 起始时间(含), null 表示不限
     * @param endTime   结束时间(含), null 表示不限
     * @param limit     最大条数, null 或 <=0 表示不限
     * @return 结果列表(collect_time 升序)
     */
    @Override
    public List<DcDataRecordVo> listByPointId(Long pointId, Date startTime, Date endTime, Integer limit) {
        boolean limited = limit != null && limit > 0;
        LambdaQueryWrapper<DcDataRecord> lqw = Wrappers.<DcDataRecord>lambdaQuery()
            .eq(DcDataRecord::getPointId, pointId)
            .isNotNull(DcDataRecord::getNumericValue)
            .ge(startTime != null, DcDataRecord::getCollectTime, startTime)
            .le(endTime != null, DcDataRecord::getCollectTime, endTime);
        if (limited) {
            // 取"最近 limit 条"而非"最早 limit 条": DESC + LIMIT, 再内存反转为升序(limit 为已校验正整数)
            lqw.orderByDesc(DcDataRecord::getCollectTime).last("LIMIT " + limit);
        } else {
            lqw.orderByAsc(DcDataRecord::getCollectTime);
        }
        List<DcDataRecordVo> list = baseMapper.selectVoList(lqw);
        if (!limited || list == null || list.size() < 2) {
            return list;
        }
        List<DcDataRecordVo> ascending = new ArrayList<>(list);
        Collections.reverse(ascending);
        return ascending;
    }

    // ==================== 私有辅助方法 ====================

    private LambdaQueryWrapper<DcDataRecord> buildQueryWrapper(DcDataRecordBo bo) {
        return Wrappers.<DcDataRecord>lambdaQuery()
            .eq(bo.getPointId() != null, DcDataRecord::getPointId, bo.getPointId())
            .eq(StringUtils.isNotBlank(bo.getPropertyCode()), DcDataRecord::getPropertyCode, bo.getPropertyCode())
            .eq(bo.getMesWoId() != null, DcDataRecord::getMesWoId, bo.getMesWoId())
            .eq(bo.getCardId() != null, DcDataRecord::getCardId, bo.getCardId())
            .eq(StringUtils.isNotBlank(bo.getSource()), DcDataRecord::getSource, bo.getSource())
            .orderByDesc(DcDataRecord::getCollectTime);
    }
}