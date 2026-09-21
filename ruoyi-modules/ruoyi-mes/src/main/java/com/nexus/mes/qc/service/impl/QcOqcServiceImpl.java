package com.nexus.mes.qc.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.common.mq.MesMqSendService;
import com.nexus.mes.ncr.service.INcrOrderService;
import com.nexus.mes.qc.domain.QcInspectDetail;
import com.nexus.mes.qc.domain.QcOqc;
import com.nexus.mes.qc.domain.bo.QcInspectEntryBo;
import com.nexus.mes.qc.domain.bo.QcOqcBo;
import com.nexus.mes.qc.domain.vo.QcInspectDetailVo;
import com.nexus.mes.qc.domain.vo.QcOqcVo;
import com.nexus.mes.qc.mapper.QcOqcMapper;
import com.nexus.mes.qc.service.IQcInspectDetailService;
import com.nexus.mes.qc.service.IQcOqcService;
import com.nexus.mes.qc.service.QcJudgeEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    /** NCR 不合格品服务(P0-4)：检验判定不合格时自动创建 NCR 单。 */
    private final INcrOrderService ncrOrderService;
    /** 检验单明细服务: 建单时按模板(物料维度)生成明细, 判定后回写结果。 */
    private final IQcInspectDetailService inspectDetailService;

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
        try {
            baseMapper.insert(oqc);
        } catch (DuplicateKeyException e) {
            // MES-QC-04: uk_oqc_so_material 冲突 = 并发下已被其他线程建单, 视为幂等成功(不抛, 避免消费者反复重投)
            log.info("OQC已存在(唯一键冲突, 幂等跳过): soId={}, materialId={}",
                bo.getSoId(), bo.getMaterialId());
            return baseMapper.selectVoOne(Wrappers.<QcOqc>lambdaQuery()
                .eq(QcOqc::getSoId, bo.getSoId())
                .eq(QcOqc::getMaterialId, bo.getMaterialId())
                .last("LIMIT 1"));
        }
        log.info("新增OQC检验单: oqcId={}, oqcNo={}", oqc.getOqcId(), oqc.getOqcNo());
        // 按检验模板(物料维度)快照生成明细; 无匹配模板时 generateDetails 记 warn 并保持待人工判定
        int details = inspectDetailService.generateDetails("OQC", oqc.getOqcId(), oqc.getMaterialId(),
            oqc.getQuantity(), null, null);
        if (details == 0) {
            log.warn("[OQC] 检验单未生成检验明细(无匹配模板), 待人工判定: oqcId={}, materialId={}",
                oqc.getOqcId(), oqc.getMaterialId());
        }
        return baseMapper.selectVoById(oqc.getOqcId());
    }

    /**
     * 幂等查询：指定销售订单 + 物料是否已存在 OQC（销售出库审批自动建单去重）。
     */
    @Override
    public boolean existsBySoAndMaterial(Long soId, Long materialId) {
        Long count = baseMapper.selectCount(Wrappers.<QcOqc>lambdaQuery()
            .eq(QcOqc::getSoId, soId)
            .eq(QcOqc::getMaterialId, materialId));
        return count != null && count > 0;
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

        // P0-4: 判定不合格则自动创建NCR单(同事务,幂等)
        if (RESULT_UNQUALIFIED.equals(inspectResult)) {
            ncrOrderService.autoCreateFromOqc(oqcId, oqc.getSoId(), oqc.getMaterialId(), defectInfo);
        }

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

    /**
     * 按检验明细执行检验(自动判定)。
     * <p>录入实测值 -> QcJudgeEngine 逐项判 PASS/FAIL 落库 -> 汇总单级结果 ->
     * PARTIAL(仅重要/次要项失败)按不合格处理 -> 复用 {@link #inspect} 完成 NCR/MQ 回写链路。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inspectWithDetails(Long oqcId, List<QcInspectEntryBo> entries, String defectInfo, String remark) {
        if (entries == null || entries.isEmpty()) {
            throw new RuntimeException("检验明细为空, 请录入实测值后提交判定");
        }
        List<QcInspectDetail> details = inspectDetailService.selectByCheck("OQC", oqcId);
        if (details.isEmpty()) {
            throw new RuntimeException("检验单没有检验明细(无匹配模板), 请使用人工判定");
        }
        // MES-QC-05: 判定前校验覆盖率 —— entries 必须覆盖该单全部明细, 不得依赖 judgeOverall 跳过未判定项
        assertAllDetailsCovered(details, entries);
        List<QcInspectDetail> judged = new ArrayList<>();
        for (QcInspectEntryBo entry : entries) {
            QcInspectDetail detail = details.stream()
                .filter(d -> d.getDetailId().equals(entry.getDetailId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("检验明细不存在或不属于本检验单: " + entry.getDetailId()));
            detail.setMeasuredValue(entry.getMeasuredValue());
            detail.setSampleQty(entry.getSampleQty());
            if (StringUtils.isNotBlank(entry.getRemark())) {
                detail.setRemark(entry.getRemark());
            }
            QcJudgeEngine.judgeDetail(detail);
            judged.add(detail);
        }
        inspectDetailService.updateDetails(judged);

        String overall = QcJudgeEngine.judgeOverall(details);
        if (overall == null) {
            throw new RuntimeException("没有可判定的检验项目, 请录入实测值");
        }
        String finalResult = QcJudgeEngine.RESULT_QUALIFIED.equals(overall)
            ? QcJudgeEngine.RESULT_QUALIFIED : QcJudgeEngine.RESULT_UNQUALIFIED;
        log.info("OQC明细自动判定: oqcId={}, overall={}, final={}", oqcId, overall, finalResult);
        inspect(oqcId, finalResult, defectInfo, null, remark);
    }

    /**
     * 查询 OQC 检验单明细(按检验模板快照生成)
     */
    @Override
    public List<QcInspectDetailVo> selectDetails(Long oqcId) {
        return MapstructUtils.convert(inspectDetailService.selectByCheck("OQC", oqcId), QcInspectDetailVo.class);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 生成OQC编号: OQC + yyyyMMdd + 雪花ID后缀。
     * <p>MES-QC-08: 不再依赖"当日 count+1"(并发下会算出相同序号), 改用雪花ID保证唯一。</p>
     */
    private String generateNo() {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return "OQC-" + today + "-" + IdUtil.getSnowflakeNextIdStr();
    }

    /**
     * MES-QC-05: 校验提交的实测值覆盖该检验单全部明细(detailId 差集), 存在未录入项即拒绝提交。
     */
    private static void assertAllDetailsCovered(List<QcInspectDetail> details, List<QcInspectEntryBo> entries) {
        Set<Long> submitted = entries.stream()
            .map(QcInspectEntryBo::getDetailId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        List<String> missing = details.stream()
            .filter(d -> !submitted.contains(d.getDetailId()))
            .map(d -> d.getItemName() == null ? String.valueOf(d.getDetailId()) : d.getItemName())
            .collect(Collectors.toList());
        if (!missing.isEmpty()) {
            throw new ServiceException("仍有 " + missing.size() + " 项未录入实测值：" + String.join("、", missing));
        }
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
