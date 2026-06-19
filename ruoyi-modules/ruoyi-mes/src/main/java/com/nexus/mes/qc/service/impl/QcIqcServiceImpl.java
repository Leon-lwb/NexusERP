package com.nexus.mes.qc.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.common.mq.MesMqSendService;
import com.nexus.mes.qc.domain.QcIqc;
import com.nexus.mes.qc.domain.bo.QcIqcBo;
import com.nexus.mes.qc.domain.vo.QcIqcVo;
import com.nexus.mes.qc.mapper.QcIqcMapper;
import com.nexus.mes.qc.service.IQcIqcService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * MES来料检验 Service 业务层实现
 * <p>
 * 状态机: PENDING -> INSPECTING -> COMPLETED
 * 检验结果: PENDING -> QUALIFIED / UNQUALIFIED
 * IQC检验完成后(COMPLETED)通过MQ回传ERP, 更新收货单质检状态。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("qcIqcService")
public class QcIqcServiceImpl implements IQcIqcService {

    private final QcIqcMapper baseMapper;
    private final MesMqSendService mesMqSendService;

    @Override
    public TableDataInfo<QcIqcVo> selectPageList(QcIqcBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<QcIqc> lqw = buildQueryWrapper(bo);
        Page<QcIqcVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public QcIqcVo selectById(Long iqcId) {
        return baseMapper.selectVoById(iqcId);
    }

    /**
     * 新增IQC检验单(生成编号)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public QcIqcVo insertByBo(QcIqcBo bo) {
        QcIqc iqc = new QcIqc();
        iqc.setIqcNo(generateNo("IQC"));
        iqc.setReceiveId(bo.getReceiveId());
        iqc.setMaterialId(bo.getMaterialId());
        iqc.setMaterialCode(bo.getMaterialCode());
        iqc.setMaterialName(bo.getMaterialName());
        iqc.setVendorId(bo.getVendorId());
        iqc.setVendorName(bo.getVendorName());
        iqc.setQuantity(bo.getQuantity());
        iqc.setSampleQty(bo.getSampleQty());
        iqc.setInspectResult("PENDING");
        iqc.setStatus(STATUS_PENDING);
        iqc.setRemark(bo.getRemark());
        baseMapper.insert(iqc);
        log.info("新增IQC检验单: iqcId={}, iqcNo={}", iqc.getIqcId(), iqc.getIqcNo());
        return baseMapper.selectVoById(iqc.getIqcId());
    }

    /**
     * 执行检验(PENDING/INSPECTING -> COMPLETED), 完成后MQ回传ERP
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inspect(Long iqcId, String inspectResult, String defectInfo,
                        BigDecimal sampleQty, String remark) {
        if (!RESULT_QUALIFIED.equals(inspectResult) && !RESULT_UNQUALIFIED.equals(inspectResult)) {
            throw new RuntimeException("检验结果必须为 QUALIFIED 或 UNQUALIFIED");
        }
        QcIqc iqc = baseMapper.selectById(iqcId);
        if (iqc == null) {
            throw new RuntimeException("IQC检验单不存在: " + iqcId);
        }
        if (STATUS_COMPLETED.equals(iqc.getStatus())) {
            throw new RuntimeException("IQC已检验完成, 不可重复检验");
        }

        // CAS校验状态: 仅 PENDING/INSPECTING 可检验
        int rows = baseMapper.update(null, Wrappers.<QcIqc>lambdaUpdate()
            .set(QcIqc::getInspectResult, inspectResult)
            .set(QcIqc::getDefectInfo, defectInfo)
            .set(sampleQty != null, QcIqc::getSampleQty, sampleQty)
            .set(StringUtils.isNotBlank(remark), QcIqc::getRemark, remark)
            .set(QcIqc::getInspectorId, LoginHelper.getUserId())
            .set(QcIqc::getInspectorName, LoginHelper.getUsername())
            .set(QcIqc::getInspectTime, new Date())
            .set(QcIqc::getStatus, STATUS_COMPLETED)
            .eq(QcIqc::getIqcId, iqcId)
            .in(QcIqc::getStatus, STATUS_PENDING, STATUS_INSPECTING));
        if (rows == 0) {
            throw new RuntimeException("IQC状态已变更, 检验失败");
        }

        QcIqc updated = baseMapper.selectById(iqcId);
        log.info("IQC检验完成: iqcId={}, iqcNo={}, result={}", iqcId, updated.getIqcNo(), inspectResult);

        // 事务提交后异步MQ回传ERP(更新收货单质检状态)
        final Long receiveId = updated.getReceiveId();
        final String iqcCode = updated.getIqcNo();
        final BigDecimal quantity = updated.getQuantity() != null ? updated.getQuantity() : BigDecimal.ZERO;
        final String result = inspectResult;
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (receiveId == null) {
                        log.info("IQC未关联ERP收货单, 跳过MQ回传: iqcNo={}", iqcCode);
                        return;
                    }
                    String tenantId = TenantHelper.getTenantId();
                    mesMqSendService.sendIqcCompleted(iqcId, iqcCode, receiveId, result, quantity, tenantId);
                }
            });
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 生成检验编号: 前缀 + yyyyMMdd + 3位序号
     * 序号基于当日已有数 +1; 并发冲突由 uk_xxx_no 唯一索引兜底
     */
    private String generateNo(String prefix) {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Long count = baseMapper.selectCount(Wrappers.<QcIqc>lambdaQuery()
            .likeRight(QcIqc::getIqcNo, prefix + "-" + today));
        int seq = (count == null ? 0 : count.intValue()) + 1;
        return prefix + "-" + today + "-" + String.format("%03d", seq);
    }

    private LambdaQueryWrapper<QcIqc> buildQueryWrapper(QcIqcBo bo) {
        return Wrappers.<QcIqc>lambdaQuery()
            .like(StringUtils.isNotBlank(bo.getIqcNo()), QcIqc::getIqcNo, bo.getIqcNo())
            .eq(StringUtils.isNotBlank(bo.getStatus()), QcIqc::getStatus, bo.getStatus())
            .eq(StringUtils.isNotBlank(bo.getInspectResult()), QcIqc::getInspectResult, bo.getInspectResult())
            .eq(bo.getReceiveId() != null, QcIqc::getReceiveId, bo.getReceiveId())
            .eq(bo.getMaterialId() != null, QcIqc::getMaterialId, bo.getMaterialId())
            .eq(bo.getVendorId() != null, QcIqc::getVendorId, bo.getVendorId())
            .orderByDesc(QcIqc::getCreateTime);
    }
}
