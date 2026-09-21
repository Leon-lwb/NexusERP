package com.nexus.mes.qc.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.common.mq.MesMqSendService;
import com.nexus.mes.lt.service.ILtLotService;
import com.nexus.mes.ncr.service.INcrOrderService;
import com.nexus.mes.qc.domain.QcIqc;
import com.nexus.mes.qc.domain.QcInspectDetail;
import com.nexus.mes.qc.domain.bo.QcIqcBo;
import com.nexus.mes.qc.domain.bo.QcInspectEntryBo;
import com.nexus.mes.qc.domain.vo.QcIqcVo;
import com.nexus.mes.qc.domain.vo.QcInspectDetailVo;
import com.nexus.mes.qc.mapper.QcIqcMapper;
import com.nexus.mes.qc.service.IQcIqcService;
import com.nexus.mes.qc.service.IQcInspectDetailService;
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

    /** IQC 来料检验单数据访问 Mapper。 */
    private final QcIqcMapper baseMapper;
    /** MES MQ 发送服务，用于检验完成后异步回传 ERP。 */
    private final MesMqSendService mesMqSendService;
    /** MES 批次服务，用于检验完成后回写关联批次 qc_status（lot.iqc_id 已关联时生效）。 */
    private final ILtLotService ltLotService;
    /** NCR 不合格品服务(P0-4)：检验判定不合格时自动创建 NCR 单。 */
    private final INcrOrderService ncrOrderService;
    /** 检验单明细服务: 建单时按模板快照生成明细, 判定后回写结果。 */
    private final IQcInspectDetailService inspectDetailService;

    /**
     * 分页查询 IQC 来料检验列表。
     *
     * @param bo        检验单查询条件（编号/状态/结果/收货单/物料/供应商）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果，包含 IQC 检验单视图列表及分页元信息
     */
    @Override
    public TableDataInfo<QcIqcVo> selectPageList(QcIqcBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<QcIqc> lqw = buildQueryWrapper(bo);
        Page<QcIqcVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    /**
     * 根据主键 ID 获取 IQC 来料检验单详情。
     *
     * @param iqcId IQC 检验单 ID
     * @return IQC 检验单视图对象
     */
    @Override
    public QcIqcVo selectById(Long iqcId) {
        return baseMapper.selectVoById(iqcId);
    }

    /**
     * 新增 IQC 来料检验单，自动生成检验编号并初始化为待检验状态。
     *
     * @param bo IQC 检验单业务对象，包含物料、供应商、数量等信息
     * @return 新增后的 IQC 检验单视图对象
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
        try {
            baseMapper.insert(iqc);
        } catch (DuplicateKeyException e) {
            // MES-QC-04: uk_iqc_receive_material 冲突 = 并发下已被其他线程建单, 视为幂等成功(不抛, 避免消费者反复重投)
            log.info("IQC已存在(唯一键冲突, 幂等跳过): receiveId={}, materialId={}",
                bo.getReceiveId(), bo.getMaterialId());
            return baseMapper.selectVoOne(Wrappers.<QcIqc>lambdaQuery()
                .eq(QcIqc::getReceiveId, bo.getReceiveId())
                .eq(QcIqc::getMaterialId, bo.getMaterialId())
                .last("LIMIT 1"));
        }
        log.info("新增IQC检验单: iqcId={}, iqcNo={}", iqc.getIqcId(), iqc.getIqcNo());
        // 按检验模板快照生成明细(带批量算 AQL 建议抽样量; 无匹配模板返回0, 保持人工判定, 不阻断)
        inspectDetailService.generateDetails("IQC", iqc.getIqcId(), iqc.getMaterialId(), iqc.getQuantity());
        return baseMapper.selectVoById(iqc.getIqcId());
    }

    /**
     * 幂等查询：指定收货单 + 物料是否已存在 IQC 检验单。
     * <p>
     * 类级 {@code @DS("mes")} 保证查询落到 ry-mes 库（qc_iqc 表所在库）。
     *
     * @param receiveId  ERP 收货单 ID
     * @param materialId 物料 ID
     * @return 已存在返回 true
     */
    @Override
    public boolean existsByReceiveAndMaterial(Long receiveId, Long materialId) {
        Long count = baseMapper.selectCount(Wrappers.<QcIqc>lambdaQuery()
            .eq(QcIqc::getReceiveId, receiveId)
            .eq(QcIqc::getMaterialId, materialId));
        return count != null && count > 0;
    }

    /**
     * 执行 IQC 检验：将状态由 PENDING/INSPECTING 推进为 COMPLETED，记录检验员与检验时间；
     * 事务提交后通过 MQ 回传 ERP 更新收货单质检状态。
     *
     * @param iqcId         IQC 检验单 ID
     * @param inspectResult 检验结果 QUALIFIED（合格）/ UNQUALIFIED（不合格）
     * @param defectInfo    不良品信息（JSON 字符串，可为空）
     * @param sampleQty     抽样数量（可为空，为空则不更新）
     * @param remark        备注（可为空）
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

        // P0-4: 判定不合格则自动创建NCR单(同事务,幂等;建单失败回滚检验--质量闭环不容许NCR丢失)
        if (RESULT_UNQUALIFIED.equals(inspectResult)) {
            ncrOrderService.autoCreateFromIqc(iqcId, updated.getReceiveId(), updated.getMaterialId(), defectInfo);
        }

        // MES-QC-10①: 批次QC状态回写不再静默 —— 与IQC判定同库同事务(ry-mes), 失败即回滚整个检验,
        // 避免"检验单已落库但批次未同步/未隔离"的静默不一致
        try {
            ltLotService.applyIqcResult(iqcId, inspectResult);
        } catch (Exception e) {
            log.error("IQC回写批次QC状态失败, 检验事务回滚: iqcId={}, iqcNo={}", iqcId, updated.getIqcNo(), e);
            throw new ServiceException("IQC回写批次QC状态失败, 检验已回滚: " + e.getMessage());
        }

        // 事务提交后异步MQ回传ERP(更新收货单质检状态)
        final Long receiveId = updated.getReceiveId();
        final String iqcCode = updated.getIqcNo();
        final BigDecimal quantity = updated.getQuantity() != null ? updated.getQuantity() : BigDecimal.ZERO;
        final String result = inspectResult;
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 批次QC状态已在检验事务内回写; 此处只做 MQ 回传 ERP(更新收货单质检状态)
                    if (receiveId == null) {
                        log.info("IQC未关联ERP收货单, 跳过MQ回传: iqcNo={}", iqcCode);
                        return;
                    }
                    String tenantId = TenantHelper.getTenantId();
                    mesMqSendService.sendIqcCompleted(iqcId, iqcCode, receiveId, updated.getMaterialId(),
                        result, quantity, tenantId);
                }
            });
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 按检验明细执行检验(自动判定)。
     * <p>录入实测值 -> QcJudgeEngine 逐项判 PASS/FAIL 落库 -> 汇总单级结果 ->
     * PARTIAL 视为 UNQUALIFIED 走 NCR(让步接收由人工在 NCR 处置流程决策) ->
     * 复用 {@link #inspect(Long, String, String, BigDecimal, String)} 完成 NCR/MQ 回写链路。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void inspectWithDetails(Long iqcId, List<QcInspectEntryBo> entries, String defectInfo, String remark) {
        if (entries == null || entries.isEmpty()) {
            throw new RuntimeException("检验明细为空, 请录入实测值后提交判定");
        }
        List<QcInspectDetail> details = inspectDetailService.selectByCheck("IQC", iqcId);
        if (details.isEmpty()) {
            throw new RuntimeException("检验单没有检验明细(无匹配模板), 请使用人工判定");
        }
        // MES-QC-05: 判定前校验覆盖率 —— entries 必须覆盖该单全部明细, 不得依赖 judgeOverall 跳过未判定项
        assertAllDetailsCovered(details, entries);
        // 实测值录入到对应明细行并逐项判定
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
        // PARTIAL(仅重要/次要项失败)按不合格处理, 由 NCR 处置流程决策让步接收或退货
        String finalResult = QcJudgeEngine.RESULT_QUALIFIED.equals(overall)
            ? QcJudgeEngine.RESULT_QUALIFIED : QcJudgeEngine.RESULT_UNQUALIFIED;
        log.info("IQC明细自动判定: iqcId={}, overall={}, final={}", iqcId, overall, finalResult);
        inspect(iqcId, finalResult, defectInfo, null, remark);
    }

    /**
     * 查询 IQC 检验单明细(按检验模板快照生成)
     */
    @Override
    public List<QcInspectDetailVo> selectDetails(Long iqcId) {
        return MapstructUtils.convert(inspectDetailService.selectByCheck("IQC", iqcId), QcInspectDetailVo.class);
    }

    /**
     * 生成检验编号: 前缀 + yyyyMMdd + 雪花ID后缀。
     * <p>MES-QC-08: 不再依赖"当日 count+1"(并发下会算出相同序号), 改用雪花ID保证唯一;
     * 保留可读前缀与日期段, 列宽 varchar(64) 足够。</p>
     */
    private String generateNo(String prefix) {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return prefix + "-" + today + "-" + IdUtil.getSnowflakeNextIdStr();
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
     * 根据查询条件构建 IQC 检验单的 LambdaQueryWrapper（编号模糊匹配，状态/结果/收货单/物料/供应商精确匹配，按创建时间倒序）。
     *
     * @param bo 检验单查询条件
     * @return IQC 查询条件构造器
     */
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
