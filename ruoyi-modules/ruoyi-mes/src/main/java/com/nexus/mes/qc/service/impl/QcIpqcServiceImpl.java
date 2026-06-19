package com.nexus.mes.qc.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.qc.domain.QcIpqc;
import com.nexus.mes.qc.domain.bo.QcIpqcBo;
import com.nexus.mes.qc.domain.vo.QcIpqcVo;
import com.nexus.mes.qc.mapper.QcIpqcMapper;
import com.nexus.mes.qc.service.IQcIpqcService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * MES过程检验 Service 业务层实现
 * <p>
 * 状态机: PENDING -> INSPECTING -> COMPLETED
 * 检验时自动计算不良率 = defectQty / sampleQty * 100
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("qcIpqcService")
public class QcIpqcServiceImpl implements IQcIpqcService {

    private final QcIpqcMapper baseMapper;

    @Override
    public TableDataInfo<QcIpqcVo> selectPageList(QcIpqcBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<QcIpqc> lqw = buildQueryWrapper(bo);
        Page<QcIpqcVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public QcIpqcVo selectById(Long ipqcId) {
        return baseMapper.selectVoById(ipqcId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QcIpqcVo insertByBo(QcIpqcBo bo) {
        QcIpqc ipqc = new QcIpqc();
        ipqc.setIpqcNo(generateNo());
        ipqc.setMesWoId(bo.getMesWoId());
        ipqc.setWoNo(bo.getWoNo());
        ipqc.setCardId(bo.getCardId());
        ipqc.setStepRecordId(bo.getStepRecordId());
        ipqc.setProcessId(bo.getProcessId());
        ipqc.setProcessCode(bo.getProcessCode());
        ipqc.setProcessName(bo.getProcessName());
        ipqc.setMaterialId(bo.getMaterialId());
        ipqc.setMaterialCode(bo.getMaterialCode());
        ipqc.setMaterialName(bo.getMaterialName());
        ipqc.setSampleQty(bo.getSampleQty());
        ipqc.setDefectQty(BigDecimal.ZERO);
        ipqc.setInspectResult("PENDING");
        ipqc.setStatus(STATUS_PENDING);
        ipqc.setRemark(bo.getRemark());
        baseMapper.insert(ipqc);
        log.info("新增IPQC检验单: ipqcId={}, ipqcNo={}", ipqc.getIpqcId(), ipqc.getIpqcNo());
        return baseMapper.selectVoById(ipqc.getIpqcId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inspect(Long ipqcId, String inspectResult, String defectInfo,
                        BigDecimal sampleQty, BigDecimal defectQty, String remark) {
        if (!RESULT_QUALIFIED.equals(inspectResult) && !RESULT_UNQUALIFIED.equals(inspectResult)) {
            throw new RuntimeException("检验结果必须为 QUALIFIED 或 UNQUALIFIED");
        }
        QcIpqc ipqc = baseMapper.selectById(ipqcId);
        if (ipqc == null) {
            throw new RuntimeException("IPQC检验单不存在: " + ipqcId);
        }
        if (STATUS_COMPLETED.equals(ipqc.getStatus())) {
            throw new RuntimeException("IPQC已检验完成, 不可重复检验");
        }

        // 计算不良率 = defectQty / sampleQty * 100 (两位小数)
        BigDecimal defectRate = calcDefectRate(sampleQty, defectQty);

        int rows = baseMapper.update(null, Wrappers.<QcIpqc>lambdaUpdate()
            .set(QcIpqc::getInspectResult, inspectResult)
            .set(QcIpqc::getDefectInfo, defectInfo)
            .set(sampleQty != null, QcIpqc::getSampleQty, sampleQty)
            .set(defectQty != null, QcIpqc::getDefectQty, defectQty)
            .set(defectRate != null, QcIpqc::getDefectRate, defectRate)
            .set(StringUtils.isNotBlank(remark), QcIpqc::getRemark, remark)
            .set(QcIpqc::getInspectorId, LoginHelper.getUserId())
            .set(QcIpqc::getInspectorName, LoginHelper.getUsername())
            .set(QcIpqc::getInspectTime, new Date())
            .set(QcIpqc::getStatus, STATUS_COMPLETED)
            .eq(QcIpqc::getIpqcId, ipqcId)
            .in(QcIpqc::getStatus, STATUS_PENDING, STATUS_INSPECTING));
        if (rows == 0) {
            throw new RuntimeException("IPQC状态已变更, 检验失败");
        }
        log.info("IPQC检验完成: ipqcId={}, result={}, defectRate={}", ipqcId, inspectResult, defectRate);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 计算不良率 = defectQty / sampleQty * 100, 两位小数; 分母为0或空返回null
     */
    private BigDecimal calcDefectRate(BigDecimal sampleQty, BigDecimal defectQty) {
        if (sampleQty == null || sampleQty.compareTo(BigDecimal.ZERO) == 0
            || defectQty == null) {
            return null;
        }
        return defectQty.multiply(BigDecimal.valueOf(100))
            .divide(sampleQty, 2, RoundingMode.HALF_UP);
    }

    /**
     * 生成IPQC编号: IPQC + yyyyMMdd + 3位序号
     */
    private String generateNo() {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Long count = baseMapper.selectCount(Wrappers.<QcIpqc>lambdaQuery()
            .likeRight(QcIpqc::getIpqcNo, "IPQC-" + today));
        int seq = (count == null ? 0 : count.intValue()) + 1;
        return "IPQC-" + today + "-" + String.format("%03d", seq);
    }

    private LambdaQueryWrapper<QcIpqc> buildQueryWrapper(QcIpqcBo bo) {
        return Wrappers.<QcIpqc>lambdaQuery()
            .like(StringUtils.isNotBlank(bo.getIpqcNo()), QcIpqc::getIpqcNo, bo.getIpqcNo())
            .eq(StringUtils.isNotBlank(bo.getStatus()), QcIpqc::getStatus, bo.getStatus())
            .eq(StringUtils.isNotBlank(bo.getInspectResult()), QcIpqc::getInspectResult, bo.getInspectResult())
            .eq(bo.getMesWoId() != null, QcIpqc::getMesWoId, bo.getMesWoId())
            .eq(StringUtils.isNotBlank(bo.getWoNo()), QcIpqc::getWoNo, bo.getWoNo())
            .eq(bo.getCardId() != null, QcIpqc::getCardId, bo.getCardId())
            .eq(bo.getMaterialId() != null, QcIpqc::getMaterialId, bo.getMaterialId())
            .orderByDesc(QcIpqc::getCreateTime);
    }
}
