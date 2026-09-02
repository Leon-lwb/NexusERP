package com.nexus.mes.qc.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.mes.qc.domain.QcInspectDetail;
import com.nexus.mes.qc.domain.QcInspectItem;
import com.nexus.mes.qc.domain.QcInspectTemplate;
import com.nexus.mes.qc.mapper.QcInspectDetailMapper;
import com.nexus.mes.qc.mapper.QcInspectItemMapper;
import com.nexus.mes.qc.mapper.QcInspectTemplateMapper;
import com.nexus.mes.qc.service.AqlSampler;
import com.nexus.mes.qc.service.IQcInspectDetailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * QC 检验单明细 Service 实现
 * <p>生成明细即快照: 从模板项目复制标准值, 之后模板变更不影响已生成明细。</p>
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service
public class QcInspectDetailServiceImpl implements IQcInspectDetailService {

    private final QcInspectDetailMapper baseMapper;
    private final QcInspectTemplateMapper templateMapper;
    private final QcInspectItemMapper itemMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generateDetails(String checkType, Long checkId, Long materialId) {
        return generateDetails(checkType, checkId, materialId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generateDetails(String checkType, Long checkId, Long materialId, BigDecimal lotQty) {
        // 幂等: 已有明细不重复生成
        Long exist = baseMapper.selectCount(Wrappers.lambdaQuery(QcInspectDetail.class)
            .eq(QcInspectDetail::getCheckType, checkType)
            .eq(QcInspectDetail::getCheckId, checkId));
        if (exist != null && exist > 0) {
            return 0;
        }
        QcInspectTemplate template = matchTemplate(checkType, materialId);
        if (template == null) {
            log.info("[QcDetail] 检验单无匹配模板, 保持人工判定: checkType={}, checkId={}, materialId={}",
                checkType, checkId, materialId);
            return 0;
        }
        // 批量给定时按 GB/T 2828.1(检验水平II)计算建议抽样量
        BigDecimal sampleQty = lotQty != null ? suggestSampleQty(lotQty) : null;
        List<QcInspectItem> items = itemMapper.selectList(Wrappers.lambdaQuery(QcInspectItem.class)
            .eq(QcInspectItem::getTemplateId, template.getTemplateId())
            .eq(QcInspectItem::getEnabled, "1")
            .orderByAsc(QcInspectItem::getSeqNum));
        for (QcInspectItem item : items) {
            QcInspectDetail detail = new QcInspectDetail();
            detail.setCheckType(checkType);
            detail.setCheckId(checkId);
            detail.setTemplateId(template.getTemplateId());
            detail.setItemId(item.getItemId());
            detail.setItemCode(item.getItemCode());
            detail.setItemName(item.getItemName());
            detail.setDataType(item.getDataType());
            detail.setStandardType(item.getStandardType());
            detail.setStandardDesc(buildStandardDesc(item));
            detail.setUnit(item.getUnit());
            detail.setCriticality(item.getCriticality());
            detail.setSampleQty(sampleQty);
            detail.setNominalValue(item.getNominalValue());
            detail.setLslValue(item.getLslValue());
            detail.setUslValue(item.getUslValue());
            detail.setTolUpper(item.getTolUpper());
            detail.setTolLower(item.getTolLower());
            detail.setSeqNum(item.getSeqNum());
            detail.setJudgeResult("PENDING");
            baseMapper.insert(detail);
        }
        log.info("[QcDetail] 检验单按模板生成明细: checkType={}, checkId={}, template={}, items={}",
            checkType, checkId, template.getTemplateCode(), items.size());
        return items.size();
    }

    @Override
    public List<QcInspectDetail> selectByCheck(String checkType, Long checkId) {
        return baseMapper.selectList(Wrappers.lambdaQuery(QcInspectDetail.class)
            .eq(QcInspectDetail::getCheckType, checkType)
            .eq(QcInspectDetail::getCheckId, checkId)
            .orderByAsc(QcInspectDetail::getSeqNum));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDetails(List<QcInspectDetail> details) {
        if (details == null) {
            return;
        }
        for (QcInspectDetail detail : details) {
            if (detail.getDetailId() == null) {
                continue;
            }
            QcInspectDetail update = new QcInspectDetail();
            update.setDetailId(detail.getDetailId());
            update.setMeasuredValue(detail.getMeasuredValue());
            update.setSampleQty(detail.getSampleQty());
            update.setJudgeResult(detail.getJudgeResult());
            update.setRemark(detail.getRemark());
            baseMapper.updateById(update);
        }
    }

    /**
     * 建议抽样量: GB/T 2828.1 检验水平II 字码行样本量(不做 AQL 箭头修正);
     * 批量≤0 取 0, 批量<2 按批量本身(不适用抽样表的极小批)。
     */
    private static BigDecimal suggestSampleQty(BigDecimal lotQty) {
        if (lotQty.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        long lot = lotQty.longValue();
        if (lot < 2) {
            return BigDecimal.valueOf(lot);
        }
        return BigDecimal.valueOf(AqlSampler.letterSampleSize(lot, "II"));
    }

    /**
     * 匹配 ACTIVE 模板: 物料专属优先, 无则通用模板(material_id 为空)
     */
    private QcInspectTemplate matchTemplate(String checkType, Long materialId) {
        if (materialId != null) {
            QcInspectTemplate specific = templateMapper.selectOne(Wrappers.lambdaQuery(QcInspectTemplate.class)
                .eq(QcInspectTemplate::getTemplateType, checkType)
                .eq(QcInspectTemplate::getStatus, "ACTIVE")
                .eq(QcInspectTemplate::getMaterialId, materialId)
                .orderByDesc(QcInspectTemplate::getVersionNo)
                .last("LIMIT 1"));
            if (specific != null) {
                return specific;
            }
        }
        return templateMapper.selectOne(Wrappers.lambdaQuery(QcInspectTemplate.class)
            .eq(QcInspectTemplate::getTemplateType, checkType)
            .eq(QcInspectTemplate::getStatus, "ACTIVE")
            .isNull(QcInspectTemplate::getMaterialId)
            .orderByDesc(QcInspectTemplate::getVersionNo)
            .last("LIMIT 1"));
    }

    private String buildStandardDesc(QcInspectItem item) {
        String unit = item.getUnit() == null ? "" : " " + item.getUnit();
        String standardType = item.getStandardType();
        if (standardType == null) {
            return "OK/NG";
        }
        switch (standardType) {
            case "LSL_USL" -> {
                return strip(item.getLslValue()) + " ~ " + strip(item.getUslValue()) + unit;
            }
            case "LSL_ONLY" -> {
                return "≥ " + strip(item.getLslValue()) + unit;
            }
            case "USL_ONLY" -> {
                return "≤ " + strip(item.getUslValue()) + unit;
            }
            case "NOMINAL" -> {
                BigDecimal tol = item.getTolUpper() != null ? item.getTolUpper() : BigDecimal.ZERO;
                return strip(item.getNominalValue()) + " ± " + strip(tol) + unit;
            }
            default -> {
                return standardType;
            }
        }
    }

    private String strip(BigDecimal value) {
        return value == null ? "?" : value.stripTrailingZeros().toPlainString();
    }
}
