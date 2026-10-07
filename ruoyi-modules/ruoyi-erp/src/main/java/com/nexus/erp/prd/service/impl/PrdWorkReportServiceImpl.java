package com.nexus.erp.prd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.prd.domain.PrdWorkReport;
import com.nexus.erp.prd.domain.bo.PrdWorkReportBo;
import com.nexus.erp.prd.domain.vo.PrdWorkReportVo;
import com.nexus.erp.prd.mapper.PrdWorkReportMapper;
import com.nexus.erp.prd.service.IPrdWorkReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 生产报工 Service 业务层实现
 * <p>
 * 实现生产报工相关的业务逻辑，包括报工记录的查询、新增、修改、删除及状态变更等。
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class PrdWorkReportServiceImpl implements IPrdWorkReportService {

    private final PrdWorkReportMapper baseMapper;
    private final PrdWorkOrderMapper workOrderMapper;

    /** 报工状态：已提交 */
    private static final String STATUS_SUBMITTED = "SUBMITTED";
    /** 报工状态：已审核 */
    private static final String STATUS_APPROVED = "APPROVED";
    /**
     * 超报工容差比例：0 表示不允许超过工单计划数量。
     * 若业务允许一定比例超报，改为如 {@code new BigDecimal("0.02")}。
     */
    private static final BigDecimal OVER_REPORT_TOLERANCE = BigDecimal.ZERO;

    /**
     * 分页查询生产报工列表
     *
     * @param bo        查询参数
     * @param pageQuery 分页参数
     * @return 生产报工分页列表
     */
    @Override
    public TableDataInfo<PrdWorkReportVo> selectPageList(PrdWorkReportBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PrdWorkReport> lqw = buildQueryWrapper(bo);
        Page<PrdWorkReportVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询生产报工列表（不分页）
     *
     * @param bo 查询参数
     * @return 生产报工列表
     */
    @Override
    public List<PrdWorkReportVo> selectList(PrdWorkReportBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    /**
     * 根据 ID 查询生产报工详情
     *
     * @param id 报工记录 ID
     * @return 生产报工详情
     */
    @Override
    public PrdWorkReportVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    /**
     * 根据报工 ID 查询生产报工实体
     *
     * @param reportId 报工记录 ID
     * @return 生产报工实体
     */
    @Override
    public PrdWorkReport selectPrdWorkReportByReportId(Long reportId) {
        return baseMapper.selectById(reportId);
    }

    /**
     * 查询生产报工列表（按实体条件）
     *
     * @param prdWorkReport 报工查询实体
     * @return 生产报工列表
     */
    @Override
    public List<PrdWorkReport> selectPrdWorkReportList(PrdWorkReport prdWorkReport) {
        LambdaQueryWrapper<PrdWorkReport> lqw = buildEntityQueryWrapper(prdWorkReport);
        return baseMapper.selectList(lqw);
    }

    /**
     * 新增生产报工记录
     *
     * @param prdWorkReport 生产报工实体
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertPrdWorkReport(PrdWorkReport prdWorkReport) {
        return baseMapper.insert(prdWorkReport);
    }

    /**
     * 新增生产报工记录（通过 BO）
     *
     * @param bo 生产报工业务对象
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PrdWorkReportBo bo) {
        PrdWorkReport entity = MapstructUtils.convert(bo, PrdWorkReport.class);
        // ★ ERP-PRD-02: 服务端强制状态机，报工新增只能落 SUBMITTED，不能由客户端直接落 APPROVED
        entity.setReportStatus(STATUS_SUBMITTED);
        return baseMapper.insert(entity);
    }

    /**
     * 修改生产报工记录（通过 BO）
     * <p>
     * 状态从非 APPROVED 变为 APPROVED 时，先锁父工单、再锁读报工，CAS 成功后回写工单累计产量，
     * 防止并发双审核导致 completed_qty 双倍累加（ERP-PRD-02）。
     *
     * @param bo 生产报工业务对象
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PrdWorkReportBo bo) {
        PrdWorkReport entity = MapstructUtils.convert(bo, PrdWorkReport.class);
        if (STATUS_APPROVED.equals(entity.getReportStatus())) {
            return approveAndSyncWorkOrder(entity);
        }
        return baseMapper.updateById(entity);
    }

    /**
     * 审核报工并回写工单产量：父工单锁→报工当前锁读→CAS→累计回写，保证只累加一次。
     *
     * @param entity 报工实体（至少含 reportId）
     * @return 影响的行数；已被审核过则返回 0（幂等）
     */
    private int approveAndSyncWorkOrder(PrdWorkReport entity) {
        if (entity.getReportId() == null) {
            throw new ServiceException("报工记录ID不能为空");
        }
        // 普通读只发现父编号；不能用其数量或状态授权审核。
        PrdWorkReport discovered = baseMapper.selectById(entity.getReportId());
        if (discovered == null || discovered.getWoId() == null) {
            throw new ServiceException("报工记录或来源工单不存在");
        }
        // 与生产入库统一先父后子；禁止先 CAS 锁子行再等待入库已持有的父锁。
        PrdWorkOrder wo = workOrderMapper.selectOne(Wrappers.<PrdWorkOrder>lambdaQuery()
            .eq(PrdWorkOrder::getWoId, discovered.getWoId()).last("FOR UPDATE"));
        if (wo == null) {
            throw new ServiceException("报工来源工单不存在");
        }
        PrdWorkReport old = baseMapper.selectOne(Wrappers.<PrdWorkReport>lambdaQuery()
            .eq(PrdWorkReport::getReportId, entity.getReportId()).last("FOR UPDATE"));
        if (old == null || !Objects.equals(entity.getReportId(), old.getReportId())
            || !Objects.equals(discovered.getWoId(), old.getWoId()) || !Objects.equals(wo.getWoId(), old.getWoId())) {
            throw new ServiceException("报工来源已变化，请重试");
        }
        // 父子事实均来自当前锁读，再沿用原超报工守卫和原 CAS 回写。
        assertNotOverReport(old, wo);
        int rows = baseMapper.update(null, Wrappers.<PrdWorkReport>lambdaUpdate()
            .set(PrdWorkReport::getReportStatus, STATUS_APPROVED)
            .eq(PrdWorkReport::getReportId, entity.getReportId())
            .ne(PrdWorkReport::getReportStatus, STATUS_APPROVED));
        if (rows == 0) {
            log.info("报工记录已被审核过或状态已变更，跳过工单回写: reportId={}", entity.getReportId());
            return 0;
        }
        syncWorkOrderQuantity(old);
        return rows;
    }

    /**
     * 超报工上限校验（ERP-PRD-02）。
     *
     * @param report 当前锁读的待审核报工记录
     * @param wo 已锁定的来源工单
     */
    private void assertNotOverReport(PrdWorkReport report, PrdWorkOrder wo) {
        if (report.getWoId() == null || report.getReportedQty() == null) {
            return;
        }
        if (wo.getPlannedQty() == null) {
            return;
        }
        BigDecimal completed = wo.getCompletedQty() != null ? wo.getCompletedQty() : BigDecimal.ZERO;
        BigDecimal maxAllowed = wo.getPlannedQty().multiply(BigDecimal.ONE.add(OVER_REPORT_TOLERANCE));
        if (completed.add(report.getReportedQty()).compareTo(maxAllowed) > 0) {
            throw new ServiceException("报工数量超过工单计划数量: plannedQty=" + wo.getPlannedQty().toPlainString()
                + ", completedQty=" + completed.toPlainString()
                + ", reportedQty=" + report.getReportedQty().toPlainString()
                + ", 超报容差=" + OVER_REPORT_TOLERANCE.toPlainString());
        }
    }

    /**
     * 报工审核通过后回写工单累计产量
     */
    private void syncWorkOrderQuantity(PrdWorkReport report) {
        if (report.getWoId() == null || report.getReportedQty() == null) {
            return;
        }
        // 原子更新: 累加完工数量 + 累加报废数量, 避免并发覆盖
        // 使用 toPlainString() 避免 BigDecimal 科学计数法导致 SQL 语法错误
        String scrapSetSql = report.getScrapQty() != null
            ? "scrap_qty = scrap_qty + " + report.getScrapQty().toPlainString() : null;
        LambdaUpdateWrapper<PrdWorkOrder> wrapper = Wrappers.<PrdWorkOrder>lambdaUpdate()
            .eq(PrdWorkOrder::getWoId, report.getWoId())
            .setSql("completed_qty = completed_qty + " + report.getReportedQty().toPlainString())
            .setSql(scrapSetSql != null, scrapSetSql)
            // ★ SQL 侧超报工上界(ERP-PRD-02)：并发下再挡一次；计划数量为空时不设上限
            .apply("(planned_qty IS NULL OR IFNULL(completed_qty, 0) + {0} <= planned_qty * (1 + {1}))",
                report.getReportedQty(), OVER_REPORT_TOLERANCE);
        int rows = workOrderMapper.update(null, wrapper);
        if (rows == 0) {
            log.warn("报工回写工单数量失败, 工单可能已被修改或超出计划数量: woId={}", report.getWoId());
            throw new ServiceException("工单数量更新失败, 请重试");
        }
    }

    /**
     * 修改生产报工记录
     * <p>ERP-PRD-02：不再允许绕过回写直接 updateById 改状态，审核路径统一走 CAS + 回写。</p>
     *
     * @param prdWorkReport 生产报工实体
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePrdWorkReport(PrdWorkReport prdWorkReport) {
        if (STATUS_APPROVED.equals(prdWorkReport.getReportStatus())) {
            return approveAndSyncWorkOrder(prdWorkReport);
        }
        return baseMapper.updateById(prdWorkReport);
    }

    /**
     * 批量删除生产报工记录
     *
     * @param ids 要删除的报工记录 ID 数组
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    /**
     * 根据报工 ID 数组批量删除生产报工记录
     *
     * @param reportIds 报工记录 ID 数组
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePrdWorkReportByReportIds(Long[] reportIds) {
        return baseMapper.deleteBatchIds(List.of(reportIds));
    }

    /**
     * 根据报工 ID 删除生产报工记录
     *
     * @param reportId 报工记录 ID
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePrdWorkReportByReportId(Long reportId) {
        return baseMapper.deleteById(reportId);
    }

    private LambdaQueryWrapper<PrdWorkReport> buildQueryWrapper(PrdWorkReportBo bo) {
        LambdaQueryWrapper<PrdWorkReport> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getReportNo()), PrdWorkReport::getReportNo, bo.getReportNo());
        lqw.eq(StringUtils.isNotBlank(bo.getReportType()), PrdWorkReport::getReportType, bo.getReportType());
        lqw.eq(StringUtils.isNotBlank(bo.getReportStatus()), PrdWorkReport::getReportStatus, bo.getReportStatus());
        lqw.eq(StringUtils.isNotBlank(bo.getWoNo()), PrdWorkReport::getWoNo, bo.getWoNo());
        lqw.like(StringUtils.isNotBlank(bo.getWoName()), PrdWorkReport::getWoName, bo.getWoName());
        lqw.eq(StringUtils.isNotBlank(bo.getMaterialCode()), PrdWorkReport::getMaterialCode, bo.getMaterialCode());
        lqw.like(StringUtils.isNotBlank(bo.getMaterialName()), PrdWorkReport::getMaterialName, bo.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(bo.getQualityStatus()), PrdWorkReport::getQualityStatus, bo.getQualityStatus());
        lqw.eq(bo.getWorkerId() != null, PrdWorkReport::getWorkerId, bo.getWorkerId());
        lqw.like(StringUtils.isNotBlank(bo.getWorkerName()), PrdWorkReport::getWorkerName, bo.getWorkerName());
        lqw.eq(StringUtils.isNotBlank(bo.getReportChannel()), PrdWorkReport::getReportChannel, bo.getReportChannel());
        lqw.eq(StringUtils.isNotBlank(bo.getSourceType()), PrdWorkReport::getSourceType, bo.getSourceType());
        lqw.eq(bo.getSourceId() != null, PrdWorkReport::getSourceId, bo.getSourceId());
        lqw.eq(StringUtils.isNotBlank(bo.getSourceNo()), PrdWorkReport::getSourceNo, bo.getSourceNo());
        lqw.eq(StringUtils.isNotBlank(bo.getBarcode()), PrdWorkReport::getBarcode, bo.getBarcode());
        return lqw;
    }

    private LambdaQueryWrapper<PrdWorkReport> buildEntityQueryWrapper(PrdWorkReport entity) {
        LambdaQueryWrapper<PrdWorkReport> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(entity.getReportNo()), PrdWorkReport::getReportNo, entity.getReportNo());
        lqw.eq(StringUtils.isNotBlank(entity.getReportType()), PrdWorkReport::getReportType, entity.getReportType());
        lqw.eq(StringUtils.isNotBlank(entity.getReportStatus()), PrdWorkReport::getReportStatus, entity.getReportStatus());
        lqw.eq(StringUtils.isNotBlank(entity.getWoNo()), PrdWorkReport::getWoNo, entity.getWoNo());
        lqw.like(StringUtils.isNotBlank(entity.getWoName()), PrdWorkReport::getWoName, entity.getWoName());
        lqw.eq(StringUtils.isNotBlank(entity.getMaterialCode()), PrdWorkReport::getMaterialCode, entity.getMaterialCode());
        lqw.like(StringUtils.isNotBlank(entity.getMaterialName()), PrdWorkReport::getMaterialName, entity.getMaterialName());
        lqw.eq(StringUtils.isNotBlank(entity.getQualityStatus()), PrdWorkReport::getQualityStatus, entity.getQualityStatus());
        lqw.eq(entity.getWorkerId() != null, PrdWorkReport::getWorkerId, entity.getWorkerId());
        lqw.like(StringUtils.isNotBlank(entity.getWorkerName()), PrdWorkReport::getWorkerName, entity.getWorkerName());
        lqw.eq(StringUtils.isNotBlank(entity.getReportChannel()), PrdWorkReport::getReportChannel, entity.getReportChannel());
        lqw.eq(StringUtils.isNotBlank(entity.getSourceType()), PrdWorkReport::getSourceType, entity.getSourceType());
        lqw.eq(entity.getSourceId() != null, PrdWorkReport::getSourceId, entity.getSourceId());
        lqw.eq(StringUtils.isNotBlank(entity.getSourceNo()), PrdWorkReport::getSourceNo, entity.getSourceNo());
        lqw.eq(StringUtils.isNotBlank(entity.getBarcode()), PrdWorkReport::getBarcode, entity.getBarcode());
        return lqw;
    }
}
