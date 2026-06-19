package com.nexus.mes.qc.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.qc.domain.QcOqc;
import com.nexus.mes.qc.domain.bo.QcOqcBo;
import com.nexus.mes.qc.domain.vo.QcOqcVo;
import com.nexus.mes.qc.mapper.QcOqcMapper;
import com.nexus.mes.qc.service.IQcOqcService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * MES出货检验 Service 业务层实现
 * <p>
 * 状态机: PENDING -> INSPECTING -> COMPLETED
 * 检验结果: PENDING -> QUALIFIED / UNQUALIFIED
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("qcOqcService")
public class QcOqcServiceImpl implements IQcOqcService {

    private final QcOqcMapper baseMapper;

    @Override
    public TableDataInfo<QcOqcVo> selectPageList(QcOqcBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<QcOqc> lqw = buildQueryWrapper(bo);
        Page<QcOqcVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public QcOqcVo selectById(Long oqcId) {
        return baseMapper.selectVoById(oqcId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QcOqcVo insertByBo(QcOqcBo bo) {
        QcOqc oqc = new QcOqc();
        oqc.setOqcNo(generateNo());
        oqc.setSoId(bo.getSoId());
        oqc.setMaterialId(bo.getMaterialId());
        oqc.setMaterialCode(bo.getMaterialCode());
        oqc.setMaterialName(bo.getMaterialName());
        oqc.setQuantity(bo.getQuantity());
        oqc.setSampleQty(bo.getSampleQty());
        oqc.setInspectResult("PENDING");
        oqc.setStatus(STATUS_PENDING);
        oqc.setRemark(bo.getRemark());
        baseMapper.insert(oqc);
        log.info("新增OQC检验单: oqcId={}, oqcNo={}", oqc.getOqcId(), oqc.getOqcNo());
        return baseMapper.selectVoById(oqc.getOqcId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inspect(Long oqcId, String inspectResult, String defectInfo,
                        BigDecimal sampleQty, String remark) {
        if (!RESULT_QUALIFIED.equals(inspectResult) && !RESULT_UNQUALIFIED.equals(inspectResult)) {
            throw new RuntimeException("检验结果必须为 QUALIFIED 或 UNQUALIFIED");
        }
        QcOqc oqc = baseMapper.selectById(oqcId);
        if (oqc == null) {
            throw new RuntimeException("OQC检验单不存在: " + oqcId);
        }
        if (STATUS_COMPLETED.equals(oqc.getStatus())) {
            throw new RuntimeException("OQC已检验完成, 不可重复检验");
        }

        int rows = baseMapper.update(null, Wrappers.<QcOqc>lambdaUpdate()
            .set(QcOqc::getInspectResult, inspectResult)
            .set(QcOqc::getDefectInfo, defectInfo)
            .set(sampleQty != null, QcOqc::getSampleQty, sampleQty)
            .set(StringUtils.isNotBlank(remark), QcOqc::getRemark, remark)
            .set(QcOqc::getInspectorId, LoginHelper.getUserId())
            .set(QcOqc::getInspectorName, LoginHelper.getUsername())
            .set(QcOqc::getInspectTime, new Date())
            .set(QcOqc::getStatus, STATUS_COMPLETED)
            .eq(QcOqc::getOqcId, oqcId)
            .in(QcOqc::getStatus, STATUS_PENDING, STATUS_INSPECTING));
        if (rows == 0) {
            throw new RuntimeException("OQC状态已变更, 检验失败");
        }
        log.info("OQC检验完成: oqcId={}, result={}", oqcId, inspectResult);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 生成OQC编号: OQC + yyyyMMdd + 3位序号
     */
    private String generateNo() {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Long count = baseMapper.selectCount(Wrappers.<QcOqc>lambdaQuery()
            .likeRight(QcOqc::getOqcNo, "OQC-" + today));
        int seq = (count == null ? 0 : count.intValue()) + 1;
        return "OQC-" + today + "-" + String.format("%03d", seq);
    }

    private LambdaQueryWrapper<QcOqc> buildQueryWrapper(QcOqcBo bo) {
        return Wrappers.<QcOqc>lambdaQuery()
            .like(StringUtils.isNotBlank(bo.getOqcNo()), QcOqc::getOqcNo, bo.getOqcNo())
            .eq(StringUtils.isNotBlank(bo.getStatus()), QcOqc::getStatus, bo.getStatus())
            .eq(StringUtils.isNotBlank(bo.getInspectResult()), QcOqc::getInspectResult, bo.getInspectResult())
            .eq(bo.getSoId() != null, QcOqc::getSoId, bo.getSoId())
            .eq(bo.getMaterialId() != null, QcOqc::getMaterialId, bo.getMaterialId())
            .orderByDesc(QcOqc::getCreateTime);
    }
}
