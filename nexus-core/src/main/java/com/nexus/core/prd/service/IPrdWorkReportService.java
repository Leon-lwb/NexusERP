package com.nexus.core.prd.service;

import java.util.List;
import com.nexus.core.prd.domain.PrdWorkReport;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 生产报工Service接口
 *
 * @author nexus
 * @date 2026-03-27
 */
public interface IPrdWorkReportService extends IService<PrdWorkReport> {
    /**
     * 查询生产报工
     *
     * @param reportId 生产报工主键
     * @return 生产报工
     */
    public PrdWorkReport selectPrdWorkReportByReportId(Long reportId);

    /**
     * 查询生产报工列表
     *
     * @param prdWorkReport 生产报工
     * @return 生产报工集合
     */
    public List<PrdWorkReport> selectPrdWorkReportList(PrdWorkReport prdWorkReport);

    /**
     * 新增生产报工
     *
     * @param prdWorkReport 生产报工
     * @return 结果
     */
    public int insertPrdWorkReport(PrdWorkReport prdWorkReport);

    /**
     * 修改生产报工
     *
     * @param prdWorkReport 生产报工
     * @return 结果
     */
    public int updatePrdWorkReport(PrdWorkReport prdWorkReport);

    /**
     * 批量删除生产报工
     *
     * @param reportIds 需要删除的生产报工主键集合
     * @return 结果
     */
    public int deletePrdWorkReportByReportIds(Long[] reportIds);

    /**
     * 删除生产报工信息
     *
     * @param reportId 生产报工主键
     * @return 结果
     */
    public int deletePrdWorkReportByReportId(Long reportId);
}
