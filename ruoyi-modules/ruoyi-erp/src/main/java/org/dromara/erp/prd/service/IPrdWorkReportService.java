package org.dromara.erp.prd.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.prd.domain.PrdWorkReport;
import org.dromara.erp.prd.domain.bo.PrdWorkReportBo;
import org.dromara.erp.prd.domain.vo.PrdWorkReportVo;

import java.util.List;

/**
 * 生产报工Service接口
 *
 * @author ruoyi
 */
public interface IPrdWorkReportService {

    TableDataInfo<PrdWorkReportVo> selectPageList(PrdWorkReportBo bo, PageQuery pageQuery);

    List<PrdWorkReportVo> selectList(PrdWorkReportBo bo);

    PrdWorkReportVo selectById(Long id);

    PrdWorkReport selectPrdWorkReportByReportId(Long reportId);

    List<PrdWorkReport> selectPrdWorkReportList(PrdWorkReport prdWorkReport);

    int insertPrdWorkReport(PrdWorkReport prdWorkReport);

    int insertByBo(PrdWorkReportBo bo);

    int updateByBo(PrdWorkReportBo bo);

    int updatePrdWorkReport(PrdWorkReport prdWorkReport);

    int deleteByIds(Long[] ids);

    int deletePrdWorkReportByReportIds(Long[] reportIds);

    int deletePrdWorkReportByReportId(Long reportId);
}
