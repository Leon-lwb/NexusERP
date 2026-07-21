package com.nexus.erp.prd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        return baseMapper.insert(entity);
    }

    /**
     * 修改生产报工记录（通过 BO）
     * <p>
     * 状态从 SUBMITTED 变为 APPROVED 时，自动回写工单累计产量。
     *
     * @param bo 生产报工业务对象
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PrdWorkReportBo bo) {
        PrdWorkReport entity = MapstructUtils.convert(bo, PrdWorkReport.class);

        // ★ 新增：状态从SUBMITTED变为APPROVED时，回写工单产量
        if ("APPROVED".equals(entity.getReportStatus())) {
            PrdWorkReport old = baseMapper.selectById(entity.getReportId());
            if (old != null && !"APPROVED".equals(old.getReportStatus())) {
                syncWorkOrderQuantity(old);
            }
        }

        return baseMapper.updateById(entity);
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
            .setSql(scrapSetSql != null, scrapSetSql);
        int rows = workOrderMapper.update(null, wrapper);
        if (rows == 0) {
            log.warn("报工回写工单数量失败, 工单可能已被修改: woId={}", report.getWoId());
            throw new RuntimeException("工单数量更新失败, 请重试");
        }
    }

    /**
     * 修改生产报工记录
     *
     * @param prdWorkReport 生产报工实体
     * @return 影响的行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePrdWorkReport(PrdWorkReport prdWorkReport) {
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
