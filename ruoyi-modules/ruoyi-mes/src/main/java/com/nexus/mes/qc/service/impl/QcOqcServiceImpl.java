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
import com.nexus.mes.qc.domain.QcOqc;
import com.nexus.mes.qc.domain.bo.QcOqcBo;
import com.nexus.mes.qc.domain.vo.QcOqcVo;
import com.nexus.mes.qc.mapper.QcOqcMapper;
import com.nexus.mes.qc.service.IQcOqcService;
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
 * MES出货检验 Service 业务层实现
 * <p>
 * 状态机: PENDING -> INSPECTING -> COMPLETED
 * 检验结果: PENDING -> QUALIFIED / UNQUALIFIED
 * OQC检验完成后(COMPLETED)若关联销售订单(soId)则通过MQ回传ERP, 更新销售订单行OQC状态。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("qcOqcService")
public class QcOqcServiceImpl implements IQcOqcService {

    /** OQC 出货检验单数据访问 Mapper。 */
    private final QcOqcMapper baseMapper;
    /** MES MQ 发送服务，用于检验完成后异步回传 ERP。 */
    private final MesMqSendService mesMqSendService;

    /**
     * 分页查询 OQC 出货检验列表。
     *
     * @param bo        检验单查询条件（编号/状态/结果/销售订单/物料）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含 OQC 检验单视图列表及分页元信息
     */
    @Override
    public TableDataInfo<QcOqcVo> selectPageList(QcOqcBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<QcOqc> lqw = buildQueryWrapper(bo);
        Page<QcOqcVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 根据主键 ID 获取 OQC 出货检验单详情。
     *
     * @param oqcId OQC 检验单 ID
     * @return OQC 检验单视图对象
     */
    @Override
    public QcOqcVo selectById(Long oqcId) {
        return baseMapper.selectVoById(oqcId);
    }

    /**
     * 新增 OQC 出货检验单，自动生成检验编号并初始化为待检验状态。
     *
     * @param bo OQC 检验单业务对象，包含销售订单、物料、数量等信息
     * @return 新增后的 OQC 检验单视图对象
     */
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

    /**
     * 执行 OQC 检验：将状态推进为 COMPLETED，记录检验员与检验时间。
     *
     * @param oqcId         OQC 检验单 ID
     * @param inspectResult 检验结果 QUALIFIED（合格）/ UNQUALIFIED（不合格）
     * @param defectInfo    不良品信息（JSON 字符串，可为空）
     * @param sampleQty     抽样数量（可为空，为空则不更新）
     * @param remark        备注（可为空）
     */
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

        // 事务提交后异步MQ回传ERP(更新销售订单行OQC状态)
        QcOqc updated = baseMapper.selectById(oqcId);
        final Long soId = updated.getSoId();
        final Long materialId = updated.getMaterialId();
        final String materialCode = updated.getMaterialCode();
        final String oqcCode = updated.getOqcNo();
        final BigDecimal quantity = updated.getQuantity() != null ? updated.getQuantity() : BigDecimal.ZERO;
        final String result = inspectResult;
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (soId == null) {
                        log.info("OQC未关联ERP销售订单, 跳过MQ回传: oqcNo={}", oqcCode);
                        return;
                    }
                    String tenantId = TenantHelper.getTenantId();
                    mesMqSendService.sendOqcCompleted(oqcId, oqcCode, soId, materialId,
                        materialCode, result, quantity, tenantId);
                }
            });
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

    /**
     * 根据查询条件构建 OQC 检验单的 LambdaQueryWrapper（编号模糊匹配，状态/结果/销售订单/物料精确匹配，按创建时间倒序）。
     *
     * @param bo 检验单查询条件
     * @return OQC 查询条件构造器
     */
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
