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

    /** IPQC 过程检验单数据访问 Mapper。 */
    private final QcIpqcMapper baseMapper;

    /**
     * 分页查询 IPQC 过程检验列表。
     *
     * @param bo        检验单查询条件（编号/状态/结果/工单/流转卡/物料）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含 IPQC 检验单视图列表及分页元信息
     */
    @Override
    public TableDataInfo<QcIpqcVo> selectPageList(QcIpqcBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<QcIpqc> lqw = buildQueryWrapper(bo);
        Page<QcIpqcVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 根据主键 ID 获取 IPQC 过程检验单详情。
     *
     * @param ipqcId IPQC 检验单 ID
     * @return IPQC 检验单视图对象
     */
    @Override
    public QcIpqcVo selectById(Long ipqcId) {
        return baseMapper.selectVoById(ipqcId);
    }

    /**
     * 新增 IPQC 过程检验单，自动生成检验编号并初始化为待检验状态。
     *
     * @param bo IPQC 检验单业务对象，包含工单、工序、物料、数量等信息
     * @return 新增后的 IPQC 检验单视图对象
     */
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

    /**
     * 执行 IPQC 检验：将状态推进为 COMPLETED，自动计算不良率（不良数/抽样数乘100，两位小数）并记录检验员与时间。
     *
     * @param ipqcId        IPQC 检验单 ID
     * @param inspectResult 检验结果 QUALIFIED（合格）/ UNQUALIFIED（不合格）
     * @param defectInfo    不良品信息（JSON 字符串，可为空）
     * @param sampleQty     抽样数量（可为空，为空则不更新）
     * @param defectQty     不良数量（用于计算不良率，可为空）
     * @param remark        备注（可为空）
     */
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

    /**
     * 根据查询条件构建 IPQC 检验单的 LambdaQueryWrapper（编号模糊匹配，状态/结果/工单/流转卡/物料精确匹配，按创建时间倒序）。
     *
     * @param bo 检验单查询条件
     * @return IPQC 查询条件构造器
     */
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
