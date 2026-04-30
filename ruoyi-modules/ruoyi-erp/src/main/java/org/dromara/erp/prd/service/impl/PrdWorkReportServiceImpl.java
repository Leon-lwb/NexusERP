package org.dromara.erp.prd.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.prd.domain.PrdWorkReport;
import org.dromara.erp.prd.domain.bo.PrdWorkReportBo;
import org.dromara.erp.prd.domain.vo.PrdWorkReportVo;
import org.dromara.erp.prd.mapper.PrdWorkReportMapper;
import org.dromara.erp.prd.service.IPrdWorkReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 生产报工Service业务层处理
 *
 * @author ruoyi
 */
@RequiredArgsConstructor
@Service
public class PrdWorkReportServiceImpl implements IPrdWorkReportService {

    private final PrdWorkReportMapper baseMapper;

    @Override
    public TableDataInfo<PrdWorkReportVo> selectPageList(PrdWorkReportBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PrdWorkReport> lqw = buildQueryWrapper(bo);
        Page<PrdWorkReportVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<PrdWorkReportVo> selectList(PrdWorkReportBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
    }

    @Override
    public PrdWorkReportVo selectById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public PrdWorkReport selectPrdWorkReportByReportId(Long reportId) {
        return baseMapper.selectById(reportId);
    }

    @Override
    public List<PrdWorkReport> selectPrdWorkReportList(PrdWorkReport prdWorkReport) {
        LambdaQueryWrapper<PrdWorkReport> lqw = buildEntityQueryWrapper(prdWorkReport);
        return baseMapper.selectList(lqw);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertPrdWorkReport(PrdWorkReport prdWorkReport) {
        return baseMapper.insert(prdWorkReport);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertByBo(PrdWorkReportBo bo) {
        PrdWorkReport entity = MapstructUtils.convert(bo, PrdWorkReport.class);
        return baseMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateByBo(PrdWorkReportBo bo) {
        PrdWorkReport entity = MapstructUtils.convert(bo, PrdWorkReport.class);
        return baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updatePrdWorkReport(PrdWorkReport prdWorkReport) {
        return baseMapper.updateById(prdWorkReport);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Long[] ids) {
        return baseMapper.deleteBatchIds(List.of(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deletePrdWorkReportByReportIds(Long[] reportIds) {
        return baseMapper.deleteBatchIds(List.of(reportIds));
    }

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
