package com.nexus.mes.ncr.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.ncr.domain.Ncr8dReport;
import com.nexus.mes.ncr.domain.bo.Ncr8dReportBo;
import com.nexus.mes.ncr.domain.vo.Ncr8dReportVo;
import com.nexus.mes.ncr.mapper.Ncr8dReportMapper;
import com.nexus.mes.ncr.service.INcr8dReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * MES NCR 8D报告 Service 业务层实现
 * <p>
 * 8D 报告采用懒创建模式(DRAFT),支持编辑更新和完成流转。
 * 状态: DRAFT -> IN_PROGRESS -> COMPLETED
 * </p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("ncr8dReportService")
public class Ncr8dReportServiceImpl implements INcr8dReportService {

    private final Ncr8dReportMapper baseMapper;

    /**
     * 获取或创建8D报告(懒创建)
     * <p>按 ncrId 查询,存在则返回,不存在则创建 DRAFT 报告。</p>
     *
     * @param ncrId NCR单ID
     * @return 8D报告视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Ncr8dReportVo getOrCreateByNcr(Long ncrId) {
        Ncr8dReportVo existing = baseMapper.selectVoOne(
            Wrappers.<Ncr8dReport>lambdaQuery()
                .eq(Ncr8dReport::getNcrId, ncrId));
        if (existing != null) {
            return existing;
        }
        Ncr8dReport report = new Ncr8dReport();
        report.setNcrId(ncrId);
        report.setStatus(STATUS_DRAFT);
        baseMapper.insert(report);
        log.info("懒创建8D报告: ncrId={}, reportId={}", ncrId, report.getReportId());
        return baseMapper.selectVoById(report.getReportId());
    }

    /**
     * 更新8D报告内容
     *
     * @param bo 8D报告业务对象
     * @return 更新成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(Ncr8dReportBo bo) {
        Ncr8dReport report = baseMapper.selectById(bo.getReportId());
        if (report == null) {
            throw new RuntimeException("8D报告不存在: " + bo.getReportId());
        }
        // 自动推进状态: DRAFT -> IN_PROGRESS(首次填写内容)
        if (STATUS_DRAFT.equals(report.getStatus())) {
            report.setStatus(STATUS_IN_PROGRESS);
        }
        report.setD1Team(bo.getD1Team());
        report.setD2Problem(bo.getD2Problem());
        report.setD3Interim(bo.getD3Interim());
        report.setD4RootCause(bo.getD4RootCause());
        report.setD5Permanent(bo.getD5Permanent());
        report.setD6Implement(bo.getD6Implement());
        report.setD7Prevent(bo.getD7Prevent());
        report.setD8Recognize(bo.getD8Recognize());
        int rows = baseMapper.updateById(report);
        log.info("更新8D报告: reportId={}", bo.getReportId());
        return rows > 0;
    }

    /**
     * 完成8D报告(DRAFT/IN_PROGRESS -> COMPLETED)
     * <p>CAS: .in(status, DRAFT, IN_PROGRESS) + rows==0 抛"状态已变更"。</p>
     *
     * @param reportId 报告ID
     * @return 完成成功返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean complete(Long reportId) {
        Ncr8dReport report = baseMapper.selectById(reportId);
        if (report == null) {
            throw new RuntimeException("8D报告不存在: " + reportId);
        }
        if (STATUS_COMPLETED.equals(report.getStatus())) {
            throw new RuntimeException("8D报告已完成,不可重复完成");
        }
        int rows = baseMapper.update(null, Wrappers.<Ncr8dReport>lambdaUpdate()
            .set(Ncr8dReport::getStatus, STATUS_COMPLETED)
            .set(Ncr8dReport::getCompletedBy, LoginHelper.getUserId())
            .set(Ncr8dReport::getCompletedTime, new Date())
            .eq(Ncr8dReport::getReportId, reportId)
            .in(Ncr8dReport::getStatus, STATUS_DRAFT, STATUS_IN_PROGRESS));
        if (rows == 0) {
            throw new RuntimeException("8D报告状态已变更,完成失败");
        }
        log.info("完成8D报告: reportId={}, completedBy={}", reportId, LoginHelper.getUserId());
        return true;
    }
}