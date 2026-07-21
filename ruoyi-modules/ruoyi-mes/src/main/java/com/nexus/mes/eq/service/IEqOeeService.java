package com.nexus.mes.eq.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.eq.domain.bo.EqOeeQueryBo;
import com.nexus.mes.eq.domain.vo.EqOeeDistributionVo;
import com.nexus.mes.eq.domain.vo.EqOeeOverviewVo;
import com.nexus.mes.eq.domain.vo.EqOeeRecordVo;
import com.nexus.mes.eq.domain.vo.EqOeeTopVo;
import com.nexus.mes.eq.domain.vo.EqOeeTrendVo;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * MES设备 OEE & 稼动率看板 Service 业务层接口
 * <p>
 * 首版策略(稼动率优先):
 * <ul>
 *   <li>停机时长来自 eq_repair.downtime_hours 当日求和;</li>
 *   <li>表现率/质量率占位 1.0, OEE = availability;</li>
 *   <li>聚合粒度: 日(stat_date)。</li>
 * </ul>
 *
 * @author Nexus
 */
public interface IEqOeeService {

    /** 首版默认计划生产时长(分钟): 8h 单班 */
    BigDecimal DEFAULT_PLANNED_MINUTES = new BigDecimal("480.00");

    /**
     * 看板概览 KPI: 平均稼动率、平均OEE、总停机时长、总故障次数、覆盖设备数
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 返回结果
     */
    EqOeeOverviewVo overview(EqOeeQueryBo bo);

    /**
     * 看板趋势: 按统计日期聚合的稼动率/OEE/停机时长折线
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 结果列表
     */
    List<EqOeeTrendVo> trend(EqOeeQueryBo bo);

    /**
     * 看板时长分布: 运行/停机/保养占比
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 返回结果
     */
    EqOeeDistributionVo distribution(EqOeeQueryBo bo);

    /**
     * 看板停机设备 TOP
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 结果列表
     */
    List<EqOeeTopVo> topDowntime(EqOeeQueryBo bo);

    /**
     * 分页查询 OEE 明细记录
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @param pageQuery 分页参数（页码、每页大小、排序）
     * @return 分页结果
     */
    TableDataInfo<EqOeeRecordVo> selectPageList(EqOeeQueryBo bo, PageQuery pageQuery);

    /**
     * 查询 OEE 明细记录(不分页, 用于导出)
     *
     * @param bo 业务对象（含筛选条件/字段值）
     * @return 结果列表
     */
    List<EqOeeRecordVo> selectList(EqOeeQueryBo bo);

    /**
     * 聚合指定日期的 OEE 数据(遍历所有有效租户)
     * <p>
     * 供 SnailJob 定时任务与手动重算调用。幂等: 按 (租户, 设备, stat_date) 唯一键 upsert。
     *
     * @param date 统计日期(按 eq_repair.repair_end 当日归集)
     * @return 处理记录数
     */
    int aggregate(Date date);
}
