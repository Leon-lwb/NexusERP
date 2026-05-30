package com.nexus.erp.prd.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.prd.domain.PrdWorkReport;
import com.nexus.erp.prd.domain.bo.PrdWorkReportBo;
import com.nexus.erp.prd.domain.vo.PrdWorkReportVo;

import java.util.List;

/**
 * 生产报工 Service 接口
 * <p>
 * 定义生产报工相关的业务逻辑操作，包括报工记录的查询、新增、修改、删除等。
 * 报工记录用于管理工人生产工时的上报与统计。
 *
 * @author ruoyi
 */
public interface IPrdWorkReportService {

    /**
     * 分页查询生产报工列表
     *
     * @param bo        查询参数，包含工单编号、工人、日期范围等筛选条件
     * @param pageQuery 分页参数，包含页码、每页条数及排序信息
     * @return 生产报工分页列表
     */
    TableDataInfo<PrdWorkReportVo> selectPageList(PrdWorkReportBo bo, PageQuery pageQuery);

    /**
     * 查询生产报工列表（不分页）
     *
     * @param bo 查询参数，包含工单编号、工人、日期范围等筛选条件
     * @return 生产报工列表
     */
    List<PrdWorkReportVo> selectList(PrdWorkReportBo bo);

    /**
     * 根据 ID 查询生产报工详情
     *
     * @param id 报工记录 ID
     * @return 生产报工详细信息
     */
    PrdWorkReportVo selectById(Long id);

    /**
     * 根据报工 ID 查询报工实体
     *
     * @param reportId 报工记录 ID
     * @return 生产报工实体
     */
    PrdWorkReport selectPrdWorkReportByReportId(Long reportId);

    /**
     * 查询生产报工列表（按实体条件）
     *
     * @param prdWorkReport 报工查询实体
     * @return 生产报工列表
     */
    List<PrdWorkReport> selectPrdWorkReportList(PrdWorkReport prdWorkReport);

    /**
     * 新增生产报工记录
     *
     * @param prdWorkReport 生产报工实体
     * @return 影响的行数
     */
    int insertPrdWorkReport(PrdWorkReport prdWorkReport);

    /**
     * 新增生产报工记录（通过 BO）
     *
     * @param bo 生产报工业务对象
     * @return 影响的行数
     */
    int insertByBo(PrdWorkReportBo bo);

    /**
     * 修改生产报工记录（通过 BO）
     * <p>
     * 状态从 SUBMITTED 变为 APPROVED 时，自动回写工单累计产量。
     *
     * @param bo 生产报工业务对象
     * @return 影响的行数
     */
    int updateByBo(PrdWorkReportBo bo);

    /**
     * 修改生产报工记录
     *
     * @param prdWorkReport 生产报工实体
     * @return 影响的行数
     */
    int updatePrdWorkReport(PrdWorkReport prdWorkReport);

    /**
     * 批量删除生产报工记录
     *
     * @param ids 要删除的报工记录 ID 数组
     * @return 影响的行数
     */
    int deleteByIds(Long[] ids);

    /**
     * 根据报工 ID 数组批量删除生产报工记录
     *
     * @param reportIds 报工记录 ID 数组
     * @return 影响的行数
     */
    int deletePrdWorkReportByReportIds(Long[] reportIds);

    /**
     * 根据报工 ID 删除生产报工记录
     *
     * @param reportId 报工记录 ID
     * @return 影响的行数
     */
    int deletePrdWorkReportByReportId(Long reportId);
}
