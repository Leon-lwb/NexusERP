package com.nexus.mes.ncr.service;

import com.nexus.mes.ncr.domain.bo.Ncr8dReportBo;
import com.nexus.mes.ncr.domain.vo.Ncr8dReportVo;

/**
 * MES NCR 8D报告 Service 业务层接口
 * <p>
 * 8D 报告是 NCR 的不合格纠正措施报告,步骤: D1~D8。
 * 状态: DRAFT -> IN_PROGRESS -> COMPLETED
 * </p>
 *
 * @author Nexus
 */
public interface INcr8dReportService {

    /** 状态: 草稿 */
    String STATUS_DRAFT = "DRAFT";
    /** 状态: 进行中 */
    String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 状态: 已完成 */
    String STATUS_COMPLETED = "COMPLETED";

    /**
     * 获取或创建8D报告(懒创建)
     * <p>按 ncrId 查询,存在则返回,不存在则创建 DRAFT 报告。</p>
     *
     * @param ncrId NCR单ID
     * @return 8D报告视图对象
     */
    Ncr8dReportVo getOrCreateByNcr(Long ncrId);

    /**
     * 更新8D报告内容
     *
     * @param bo 8D报告业务对象
     * @return 更新成功返回 true
     */
    Boolean updateByBo(Ncr8dReportBo bo);

    /**
     * 完成8D报告(DRAFT/IN_PROGRESS -> COMPLETED)
     *
     * @param reportId 报告ID
     * @return 完成成功返回 true
     */
    Boolean complete(Long reportId);
}