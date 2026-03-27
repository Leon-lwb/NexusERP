package com.nexus.core.prd.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.prd.mapper.PrdWorkReportMapper;
import com.nexus.core.prd.domain.PrdWorkReport;
import com.nexus.core.prd.service.IPrdWorkReportService;

/**
 * 生产报工Service业务层处理
 *
 * @author nexus
 * @date 2026-03-27
 */
@Service
public class PrdWorkReportServiceImpl extends ServiceImpl<PrdWorkReportMapper, PrdWorkReport> implements IPrdWorkReportService {

    @Autowired
    private PrdWorkReportMapper prdWorkReportMapper;

    /**
     * 查询生产报工
     *
     * @param reportId 生产报工主键
     * @return 生产报工
     */
    @Override
    public PrdWorkReport selectPrdWorkReportByReportId(Long reportId) {
        return prdWorkReportMapper.selectPrdWorkReportByReportId(reportId);
    }

    /**
     * 查询生产报工列表
     *
     * @param prdWorkReport 生产报工
     * @return 生产报工
     */
    @Override
    public List<PrdWorkReport> selectPrdWorkReportList(PrdWorkReport prdWorkReport) {
        return prdWorkReportMapper.selectPrdWorkReportList(prdWorkReport);
    }

    /**
     * 新增生产报工
     *
     * @param prdWorkReport 生产报工
     * @return 结果
     */
    @Override
    public int insertPrdWorkReport(PrdWorkReport prdWorkReport) {
                prdWorkReport.setCreateTime(DateUtils.getNowDate());
            return prdWorkReportMapper.insertPrdWorkReport(prdWorkReport);
    }

    /**
     * 修改生产报工
     *
     * @param prdWorkReport 生产报工
     * @return 结果
     */
    @Override
    public int updatePrdWorkReport(PrdWorkReport prdWorkReport) {
                prdWorkReport.setUpdateTime(DateUtils.getNowDate());
        return prdWorkReportMapper.updatePrdWorkReport(prdWorkReport);
    }

    /**
     * 批量删除生产报工
     *
     * @param reportIds 需要删除的生产报工主键
     * @return 结果
     */
    @Override
    public int deletePrdWorkReportByReportIds(Long[] reportIds) {
        return prdWorkReportMapper.deletePrdWorkReportByReportIds(reportIds);
    }

    /**
     * 删除生产报工信息
     *
     * @param reportId 生产报工主键
     * @return 结果
     */
    @Override
    public int deletePrdWorkReportByReportId(Long reportId) {
        return prdWorkReportMapper.deletePrdWorkReportByReportId(reportId);
    }
}
